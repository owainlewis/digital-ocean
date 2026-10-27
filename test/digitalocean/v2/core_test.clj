(ns digitalocean.v2.core-test
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [digitalocean.v2.core :as do]
            [org.httpkit.server :as server])
  (:import [java.net ServerSocket URLDecoder]))

(defn- free-port []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn- with-test-server [handler f]
  (let [port (free-port)
        stop (server/run-server handler {:port port})
        client (do/client "test-token"
                          {:endpoint (str "http://127.0.0.1:" port "/v2")})]
    (try
      (f client)
      (finally
        (stop)))))

(defn- json-response
  ([body] (json-response 200 body))
  ([status body]
   (cond-> {:status status
            :headers {"content-type" "application/json"
                      "ratelimit-limit" "5000"
                      "ratelimit-remaining" "4999"
                      "ratelimit-reset" "1720000000"}}
     body (assoc :body (json/generate-string body)))))

(defn- query-map [query-string]
  (if (str/blank? query-string)
    {}
    (into {}
          (map (fn [part]
                 (let [[k v] (str/split part #"=" 2)]
                   [(URLDecoder/decode k "UTF-8")
                    (URLDecoder/decode (or v "") "UTF-8")])))
          (str/split query-string #"&"))))

(deftest resource-url-builds-safe-paths
  (binding [do/*endpoint* "https://api.digitalocean.com/v2/"]
    (is (= "https://api.digitalocean.com/v2/domains/example.com/records"
           (do/resource-url :domains "example.com" "/records")))
    (is (= "https://api.digitalocean.com/v2/tags/PROD"
           (do/resource-url :tags "PROD")))
    (is (= "https://api.digitalocean.com/v2/account/keys/name%20with%20spaces"
           (do/resource-url "account/keys" "name with spaces")))))

(deftest get-requests-use-query-params-and-auth-headers
  (let [seen (promise)]
    (with-test-server
      (fn [request]
        (deliver seen request)
        (json-response {:droplets []
                        :links {}
                        :meta {:total 0}}))
      (fn [client]
        (is (= {:droplets []
                :links {}
                :meta {:total 0}}
               (do/droplets client {:page 2
                                    :per-page 100
                                    :tag-name "Prod"})))
        (let [request @seen]
          (is (= :get (:request-method request)))
          (is (= "/v2/droplets" (:uri request)))
          (is (= "Bearer test-token"
                 (get-in request [:headers "authorization"])))
          (is (= {"page" "2"
                  "per_page" "100"
                  "tag_name" "Prod"}
                 (query-map (:query-string request)))))))))

(deftest post-requests-send-normalized-json-bodies
  (let [seen (promise)]
    (with-test-server
      (fn [request]
        (deliver seen (assoc request :body-text (slurp (:body request))))
        (json-response 202 {:droplet {:id 123}}))
      (fn [client]
        (is (= {:droplet {:id 123}}
               (do/create-droplet client {:name "web-01"
                                          :region "lon1"
                                          :size "s-1vcpu-1gb"
                                          :image "ubuntu-24-04-x64"
                                          :ssh-keys [456]})))
        (let [request @seen]
          (is (= :post (:request-method request)))
          (is (= "/v2/droplets" (:uri request)))
          (is (= {:name "web-01"
                  :region "lon1"
                  :size "s-1vcpu-1gb"
                  :image "ubuntu-24-04-x64"
                  :ssh_keys [456]}
                 (json/parse-string (:body-text request) true))))))))

(deftest delete-requests-handle-empty-success-bodies
  (with-test-server
    (fn [request]
      (is (= :delete (:request-method request)))
      (is (= "/v2/droplets/123" (:uri request)))
      {:status 204
       :headers {"ratelimit-limit" "5000"}})
    (fn [client]
      (is (nil? (do/delete-droplet client 123))))))

(deftest request-exposes-status-headers-and-rate-limit-data
  (with-test-server
    (fn [_]
      (json-response {:account {:droplet_limit 25}}))
    (fn [client]
      (let [response (do/request client :get :account)]
        (is (:ok? response))
        (is (= 200 (:status response)))
        (is (= {:limit 5000
                :remaining 4999
                :reset 1720000000}
               (:rate-limit response)))
        (is (= {:account {:droplet_limit 25}}
               (:body response)))))))

(deftest request-options-support-http-proxies
  (let [seen (promise)]
    (with-test-server
      (fn [request]
        (deliver seen request)
        (json-response {:account {}}))
      (fn [client]
        (let [proxied-client (assoc client
                                    :endpoint "http://digitalocean.test/v2"
                                    :request-options
                                    {:proxy-host "127.0.0.1"
                                     :proxy-port (-> client :endpoint
                                                     (str/split #":")
                                                     last
                                                     (str/split #"/")
                                                     first
                                                     parse-long)})]
          (is (= {:account {}} (do/account proxied-client)))
          (let [request @seen]
            (is (= :get (:request-method request)))
            (is (= "digitalocean.test" (get-in request [:headers "host"])))
            (is (= "http://digitalocean.test/v2/account" (:uri request)))))))))

(deftest request-bang-throws-on-api-errors
  (with-test-server
    (fn [_]
      (json-response 401 {:id "unauthorized"
                          :message "bad token"
                          :request_id "req-123"}))
    (fn [client]
      (try
        (do/droplets client)
        (is false "expected ex-info")
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (is (= "bad token" (.getMessage e)))
            (is (false? (:ok? data)))
            (is (= 401 (:status data)))
            (is (= {:id "unauthorized"
                    :message "bad token"
                    :request_id "req-123"}
                   (:body data)))))))))

(deftest list-all-follows-digitalocean-pagination
  (let [pages (atom [])]
    (with-test-server
      (fn [request]
        (let [query (query-map (:query-string request))
              page (parse-long (get query "page"))]
          (swap! pages conj page)
          (json-response {:droplets [{:id page}]
                          :links (if (= 1 page)
                                   {:pages {:next "http://example.test/v2/droplets?page=2"}}
                                   {})
                          :meta {:total 2}})))
      (fn [client]
        (is (= [{:id 1} {:id 2}]
               (do/all-droplets client)))
        (is (= [1 2] @pages))))))

(deftest databases-helper-uses-databases-endpoint
  (let [seen (promise)]
    (with-test-server
      (fn [request]
        (deliver seen request)
        (json-response {:databases [{:id "db-1"}]}))
      (fn [client]
        (is (= {:databases [{:id "db-1"}]}
               (do/databases client)))
        (is (= "/v2/databases" (:uri @seen)))))))

(deftest non-json-bodies-are-returned-as-text
  (with-test-server
    (fn [request]
      (is (= "/v2/kubernetes/clusters/k8s-1/kubeconfig" (:uri request)))
      {:status 200
       :headers {"content-type" "application/yaml"}
       :body "apiVersion: v1\nkind: Config\n"})
    (fn [client]
      (is (= "apiVersion: v1\nkind: Config\n"
             (do/kubeconfig client "k8s-1"))))))

(deftest html-error-pages-do-not-break-error-handling
  (with-test-server
    (fn [_]
      {:status 502
       :headers {"content-type" "text/html"}
       :body "<html>Bad Gateway</html>"})
    (fn [client]
      (try
        (do/account client)
        (is false "expected ex-info")
        (catch clojure.lang.ExceptionInfo e
          (is (= "DigitalOcean API request failed with HTTP 502" (.getMessage e)))
          (is (= "<html>Bad Gateway</html>" (:body (ex-data e)))))))))

(deftest resource-helpers-use-api-paths
  (let [seen (atom [])]
    (with-test-server
      (fn [request]
        (swap! seen conj [(:request-method request) (:uri request)])
        (json-response {}))
      (fn [client]
        (do/load-balancers client)
        (do/get-firewall client "fw-1")
        (do/update-kubernetes-cluster client "k8s-1" {:name "prod"})
        (do/reserved-ips client)
        (do/delete-app client "app-1")
        (do/cdn-endpoints client)
        (do/uptime-checks client)
        (do/spaces-keys client)
        (do/registry client)
        (is (= [[:get "/v2/load_balancers"]
                [:get "/v2/firewalls/fw-1"]
                [:put "/v2/kubernetes/clusters/k8s-1"]
                [:get "/v2/reserved_ips"]
                [:delete "/v2/apps/app-1"]
                [:get "/v2/cdn/endpoints"]
                [:get "/v2/uptime/checks"]
                [:get "/v2/spaces/keys"]
                [:get "/v2/registry"]]
               @seen))))))

(deftest action-helpers-send-typed-bodies
  (let [seen (atom [])]
    (with-test-server
      (fn [request]
        (swap! seen conj {:uri (:uri request)
                          :body (json/parse-string (slurp (:body request)) true)})
        (json-response 201 {:action {:id 1 :status "in-progress"}}))
      (fn [client]
        (do/attach-volume client "vol-1" 123 "lon1")
        (do/assign-reserved-ip client "203.0.113.10" 123)
        (is (= [{:uri "/v2/volumes/vol-1/actions"
                 :body {:type "attach" :droplet_id 123 :region "lon1"}}
                {:uri "/v2/reserved_ips/203.0.113.10/actions"
                 :body {:type "assign" :droplet_id 123}}]
               @seen))))))

(deftest wait-for-action-polls-until-completed
  (let [polls (atom 0)]
    (with-test-server
      (fn [request]
        (is (= "/v2/actions/42" (:uri request)))
        (json-response {:action {:id 42
                                 :status (if (< (swap! polls inc) 3)
                                           "in-progress"
                                           "completed")}}))
      (fn [client]
        (is (= {:id 42 :status "completed"}
               (do/wait-for-action client {:action {:id 42 :status "in-progress"}}
                                   :interval-ms 1)))
        (is (= 3 @polls))))))

(deftest wait-for-action-throws-on-error-and-timeout
  (with-test-server
    (fn [_]
      (json-response {:action {:id 7 :status "errored"}}))
    (fn [client]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"action 7 errored"
                            (do/wait-for-action client 7 :interval-ms 1)))))
  (with-test-server
    (fn [_]
      (json-response {:action {:id 8 :status "in-progress"}}))
    (fn [client]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Timed out"
                            (do/wait-for-action client 8 :interval-ms 1 :timeout-ms 20))))))
