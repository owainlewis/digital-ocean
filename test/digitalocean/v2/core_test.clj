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
        client (do/client "dop_v1_test"
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
          (is (= "Bearer dop_v1_test"
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
