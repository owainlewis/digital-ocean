(ns digitalocean.v2.core
  (:refer-clojure :exclude [keys])
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.walk :as walk])
  (:import [java.net URLEncoder]
           [java.nio.charset StandardCharsets]))

(def endpoint "https://api.digitalocean.com/v2")
(def ^:dynamic *endpoint* endpoint)
(def default-timeout-ms 30000)

(defn client
  "Build a reusable DigitalOcean API client.

  A plain token string can still be passed to every public function. Use a
  client map when you want to set a custom endpoint, timeout, or headers."
  ([token] (client token {}))
  ([token opts]
   (when (str/blank? (str token))
     (throw (ex-info "DigitalOcean token is required" {:type :missing-token})))
   (merge {:token token
           :endpoint *endpoint*
           :timeout default-timeout-ms
           :headers {}
           :request-options {}}
          opts)))

(def make-client client)

(defn- coerce-client [value]
  (cond
    (string? value) (client value)
    (and (map? value) (:token value)) (merge (client (:token value)) value)
    :else (throw (ex-info "Expected a token string or client map"
                          {:type :invalid-client
                           :value value}))))

(defn- trim-slashes [s]
  (str/replace (str s) #"^/+|/+$" ""))

(defn- trim-trailing-slashes [s]
  (str/replace (str s) #"/+$" ""))

(defn- path-part->segments [part]
  (if (nil? part)
    []
    (->> (str/split (trim-slashes (if (keyword? part) (name part) part)) #"/+")
         (remove str/blank?))))

(defn- encode-path-segment [segment]
  (-> (URLEncoder/encode (str segment) (.name StandardCharsets/UTF_8))
      (str/replace "+" "%20")
      (str/replace "%7E" "~")))

(defn- path-segments [parts]
  (mapcat path-part->segments parts))

(defn resource-url
  "Build a DigitalOcean V2 API URL from path parts.

  Path segment case is preserved because some DigitalOcean resources, such as
  tags, use canonical capitalization."
  [& parts]
  (let [segments (path-segments parts)]
    (str (trim-trailing-slashes *endpoint*)
         (when (seq segments)
           (str "/" (str/join "/" (map encode-path-segment segments)))))))

(defn- absolute-url? [value]
  (boolean (re-find #"(?i)^https?://" (str value))))

(defn- request-url [client path]
  (cond
    (absolute-url? path) path
    (sequential? path) (binding [*endpoint* (:endpoint client)]
                         (apply resource-url path))
    :else (binding [*endpoint* (:endpoint client)]
            (resource-url path))))

(defn- api-key-name [k]
  (cond
    (keyword? k) (str/replace (name k) "-" "_")
    (string? k) k
    :else (str k)))

(defn- normalize-api-data [value]
  (cond
    (map? value) (into {}
                       (map (fn [[k v]]
                              [(api-key-name k) (normalize-api-data v)]))
                       value)
    (sequential? value) (mapv normalize-api-data value)
    :else value))

(defn- json-content-type? [content-type]
  (or (str/blank? (str content-type))
      (str/includes? (str/lower-case (str content-type)) "json")))

(defn- parse-body
  "Parse JSON bodies into keywordized maps. Non-JSON bodies, such as the YAML
  returned by the Kubernetes kubeconfig endpoint or an HTML error page from a
  proxy, are returned as text."
  [content-type body]
  (when-not (str/blank? (str body))
    (if (json-content-type? content-type)
      (try
        (json/parse-string body true)
        (catch com.fasterxml.jackson.core.JsonProcessingException _
          body))
      body)))

(defn- lower-case-keys [m]
  (walk/postwalk
   (fn [x]
     (if (map? x)
       (into {}
             (map (fn [[k v]]
                    [(if (string? k) (str/lower-case k) k) v]))
             x)
       x))
   m))

(defn- header-value [headers header-name]
  (let [lower-name (str/lower-case header-name)]
    (or (get headers header-name)
        (get headers lower-name)
        (get headers (keyword header-name))
        (get headers (keyword lower-name)))))

(defn- parse-long-safe [value]
  (when value
    (try
      (Long/parseLong (str value))
      (catch NumberFormatException _ nil))))

(defn- rate-limit [headers]
  (let [limit (parse-long-safe (header-value headers "ratelimit-limit"))
        remaining (parse-long-safe (header-value headers "ratelimit-remaining"))
        reset (parse-long-safe (header-value headers "ratelimit-reset"))]
    (cond-> {}
      limit (assoc :limit limit)
      remaining (assoc :remaining remaining)
      reset (assoc :reset reset))))

(defn request
  "Make a DigitalOcean API request.

  Returns an envelope:

  {:ok? true
   :status 200
   :headers {...}
   :rate-limit {:limit 5000 :remaining 4999 :reset 1720000000}
   :body {...}}

  Use request! or the convenience functions when you want HTTP failures to
  raise an exception."
  ([client method path] (request client method path {}))
  ([client method path {:keys [query body headers timeout] :or {headers {}}}]
   (let [client (coerce-client client)
         req-headers (merge {"Accept" "application/json"
                             "Content-Type" "application/json"
                             "Authorization" (str "Bearer " (:token client))}
                            (:headers client)
                            headers)
         timeout-ms (or timeout (:timeout client))
         opts (cond-> (merge (:request-options client)
                             {:method method
                              :url (request-url client path)
                              :headers req-headers
                              :throw-exceptions false
                              :as :text
                              :connection-timeout timeout-ms
                              :socket-timeout timeout-ms})
                (seq query) (assoc :query-params (normalize-api-data query))
                (some? body) (assoc :body (json/generate-string
                                           (normalize-api-data body))))
         {:keys [status headers body error]} (http/request opts)
         parsed-body (parse-body (header-value headers "content-type") body)
         response {:ok? (and status (<= 200 status 299))
                   :status status
                   :headers (lower-case-keys headers)
                   :rate-limit (rate-limit headers)
                   :body parsed-body}]
     (if error
       (assoc response :ok? false :error error)
       (cond-> response
         (not (:ok? response)) (assoc :error parsed-body))))))

(defn- response-error-message [{:keys [status error body]}]
  (or (:message error)
      (:message body)
      (when status (str "DigitalOcean API request failed with HTTP " status))
      "DigitalOcean API request failed"))

(defn request!
  "Like request, but returns the parsed body and throws ex-info on failure."
  [& args]
  (let [response (apply request args)]
    (if (:ok? response)
      (:body response)
      (throw (ex-info (response-error-message response) response)))))

(defn- request-options [method params]
  (let [params (not-empty params)]
    (cond
      (nil? params) {}
      (#{:get :delete} method) {:query params}
      :else {:body params})))

(defn run-request
  "Compatibility helper for older callers.

  Prefer request or request! for new code."
  [method url token & params]
  (let [response (request token method url (request-options method (into {} params)))]
    (if (:ok? response)
      (:body response)
      {:status (:status response)
       :error (:error response)
       :body (:body response)})))

(defn generic
  "Create a small resource function for REST-shaped endpoints."
  [method resource]
  (fn
    ([client]
     (request! client method [resource]))
    ([client id-or-params]
     (if (map? id-or-params)
       (request! client method [resource] (request-options method id-or-params))
       (request! client method [resource id-or-params])))
    ([client id params]
     (if (nil? id)
       (request! client method [resource] (request-options method params))
       (request! client method [resource id] (request-options method params))))))

(def account (generic :get :account))

(def actions (generic :get :actions))
(def get-action actions)

(def domains (generic :get :domains))
(def get-domain domains)
(def create-domain (generic :post :domains))
(def delete-domain (generic :delete :domains))

(defn records
  "Return records for a domain."
  ([client domain]
   (request! client :get [:domains domain :records]))
  ([client domain params]
   (request! client :get [:domains domain :records] {:query params})))

(def domain-records records)

(defn get-record [client domain record-id]
  (request! client :get [:domains domain :records record-id]))

(defn create-record [client domain params]
  (request! client :post [:domains domain :records] {:body params}))

(defn update-record [client domain record-id params]
  (request! client :put [:domains domain :records record-id] {:body params}))

(defn delete-record [client domain record-id]
  (request! client :delete [:domains domain :records record-id]))

(def droplets (generic :get :droplets))
(def get-droplet droplets)
(def create-droplet (generic :post :droplets))
(def delete-droplet (generic :delete :droplets))

(defn droplet-action
  "Run an action against a Droplet.

  Examples:
  (droplet-action client 123 :reboot)
  (droplet-action client 123 :resize {:size \"s-1vcpu-2gb\" :disk true})"
  ([client droplet-id action]
   (droplet-action client droplet-id action {}))
  ([client droplet-id action params]
   (request! client :post [:droplets droplet-id :actions]
             {:body (assoc params :type (api-key-name action))})))

(defn reboot-droplet [client droplet-id]
  (droplet-action client droplet-id :reboot))

(defn power-cycle-droplet [client droplet-id]
  (droplet-action client droplet-id :power-cycle))

(defn shutdown-droplet [client droplet-id]
  (droplet-action client droplet-id :shutdown))

(defn power-off-droplet [client droplet-id]
  (droplet-action client droplet-id :power-off))

(defn power-on-droplet [client droplet-id]
  (droplet-action client droplet-id :power-on))

(defn password-reset-droplet [client droplet-id]
  (droplet-action client droplet-id :password-reset))

(defn snapshot-droplet [client droplet-id name]
  (droplet-action client droplet-id :snapshot {:name name}))

(defn resize-droplet [client droplet-id size & [{:keys [disk] :or {disk false}}]]
  (droplet-action client droplet-id :resize {:size size :disk disk}))

(defn restore-droplet [client droplet-id image]
  (droplet-action client droplet-id :restore {:image image}))

(defn rebuild-droplet [client droplet-id image]
  (droplet-action client droplet-id :rebuild {:image image}))

(defn rename-droplet [client droplet-id name]
  (droplet-action client droplet-id :rename {:name name}))

(def images (generic :get :images))
(def get-image images)
(def snapshots (generic :get :snapshots))
(def get-snapshot snapshots)

(def ssh-keys (generic :get "account/keys"))
(def get-key ssh-keys)
(def create-key (generic :post "account/keys"))
(def update-key (generic :put "account/keys"))
(def delete-key (generic :delete "account/keys"))
(def keys ssh-keys)

(def regions (generic :get :regions))
(def sizes (generic :get :sizes))

(def tags (generic :get :tags))
(def get-tag tags)
(def create-tag (generic :post :tags))
(def delete-tag (generic :delete :tags))

(def projects (generic :get :projects))
(def get-project projects)
(def create-project (generic :post :projects))
(def update-project (generic :patch :projects))
(def delete-project (generic :delete :projects))

(def databases (generic :get :databases))
(def get-database databases)
(def create-database (generic :post :databases))
(def update-database (generic :patch :databases))
(def delete-database (generic :delete :databases))

(def vpcs (generic :get :vpcs))
(def get-vpc vpcs)
(def create-vpc (generic :post :vpcs))
(def update-vpc (generic :patch :vpcs))
(def delete-vpc (generic :delete :vpcs))

(defn list-all
  "Fetch every page for a collection endpoint.

  `collection-key` is the key of the list in the response body. Response keys
  keep DigitalOcean's snake_case spelling.

  (list-all client :droplets :droplets)
  (list-all client [:domains \"example.com\" :records] :domain_records)"
  ([client resource collection-key]
   (list-all client resource collection-key {}))
  ([client resource collection-key params]
   (loop [page (or (:page params) 1)
          acc []]
     (let [response (request! client :get resource
                              {:query (assoc params :page page :per-page
                                             (or (:per-page params) 200))})
           items (get response collection-key)
           next-page (get-in response [:links :pages :next])]
       (if next-page
         (recur (inc page) (into acc items))
         (into acc items))))))

(defn all-droplets
  ([client] (all-droplets client {}))
  ([client params] (list-all client :droplets :droplets params)))

(defn all-images
  ([client] (all-images client {}))
  ([client params] (list-all client :images :images params)))

(defn all-regions
  ([client] (all-regions client {}))
  ([client params] (list-all client :regions :regions params)))

(defn all-sizes
  ([client] (all-sizes client {}))
  ([client params] (list-all client :sizes :sizes params)))

(defn all-volumes
  ([client] (all-volumes client {}))
  ([client params] (list-all client :volumes :volumes params)))

(defn all-ssh-keys
  ([client] (all-ssh-keys client {}))
  ([client params] (list-all client "account/keys" :ssh_keys params)))

;; Block storage

(def volumes (generic :get :volumes))
(def get-volume volumes)
(def create-volume (generic :post :volumes))
(def delete-volume (generic :delete :volumes))

(defn volume-action
  "Run an action against a volume, e.g. attach, detach or resize.

  (volume-action client \"vol-id\" :attach {:droplet-id 123 :region \"lon1\"})"
  ([client volume-id action]
   (volume-action client volume-id action {}))
  ([client volume-id action params]
   (request! client :post [:volumes volume-id :actions]
             {:body (assoc params :type (api-key-name action))})))

(defn attach-volume [client volume-id droplet-id region]
  (volume-action client volume-id :attach {:droplet-id droplet-id :region region}))

(defn detach-volume [client volume-id droplet-id region]
  (volume-action client volume-id :detach {:droplet-id droplet-id :region region}))

;; Networking

(def load-balancers (generic :get "load_balancers"))
(def get-load-balancer load-balancers)
(def create-load-balancer (generic :post "load_balancers"))
(def update-load-balancer (generic :put "load_balancers"))
(def delete-load-balancer (generic :delete "load_balancers"))

(def firewalls (generic :get :firewalls))
(def get-firewall firewalls)
(def create-firewall (generic :post :firewalls))
(def update-firewall (generic :put :firewalls))
(def delete-firewall (generic :delete :firewalls))

(def reserved-ips (generic :get "reserved_ips"))
(def get-reserved-ip reserved-ips)
(def create-reserved-ip (generic :post "reserved_ips"))
(def delete-reserved-ip (generic :delete "reserved_ips"))

(defn assign-reserved-ip
  "Assign a reserved IP to a Droplet."
  [client ip droplet-id]
  (request! client :post ["reserved_ips" ip :actions]
            {:body {:type "assign" :droplet-id droplet-id}}))

(defn unassign-reserved-ip
  "Unassign a reserved IP from its Droplet."
  [client ip]
  (request! client :post ["reserved_ips" ip :actions]
            {:body {:type "unassign"}}))

(def certificates (generic :get :certificates))
(def get-certificate certificates)
(def create-certificate (generic :post :certificates))
(def delete-certificate (generic :delete :certificates))

(def cdn-endpoints (generic :get "cdn/endpoints"))
(def get-cdn-endpoint cdn-endpoints)
(def create-cdn-endpoint (generic :post "cdn/endpoints"))
(def update-cdn-endpoint (generic :put "cdn/endpoints"))
(def delete-cdn-endpoint (generic :delete "cdn/endpoints"))

;; Kubernetes

(def kubernetes-clusters (generic :get "kubernetes/clusters"))
(def get-kubernetes-cluster kubernetes-clusters)
(def create-kubernetes-cluster (generic :post "kubernetes/clusters"))
(def update-kubernetes-cluster (generic :put "kubernetes/clusters"))
(def delete-kubernetes-cluster (generic :delete "kubernetes/clusters"))

(defn kubeconfig
  "Return the kubeconfig YAML for a cluster as a string."
  [client cluster-id]
  (request! client :get ["kubernetes/clusters" cluster-id :kubeconfig]
            {:headers {"Accept" "application/yaml"}}))

(defn kubernetes-node-pools [client cluster-id]
  (request! client :get ["kubernetes/clusters" cluster-id "node_pools"]))

;; App Platform

(def apps (generic :get :apps))
(def get-app apps)
(def create-app (generic :post :apps))
(def update-app (generic :put :apps))
(def delete-app (generic :delete :apps))

(defn app-deployments [client app-id]
  (request! client :get [:apps app-id :deployments]))

(defn create-app-deployment
  "Trigger a new deployment of an app."
  ([client app-id] (create-app-deployment client app-id {}))
  ([client app-id params]
   (request! client :post [:apps app-id :deployments] {:body params})))

;; Container registry, Spaces keys and uptime checks

(defn registry [client]
  (request! client :get [:registry]))

(def spaces-keys (generic :get "spaces/keys"))
(def create-spaces-key (generic :post "spaces/keys"))
(def delete-spaces-key (generic :delete "spaces/keys"))

(def uptime-checks (generic :get "uptime/checks"))
(def get-uptime-check uptime-checks)
(def create-uptime-check (generic :post "uptime/checks"))
(def update-uptime-check (generic :put "uptime/checks"))
(def delete-uptime-check (generic :delete "uptime/checks"))

;; Waiting for actions

(defn- action-id [action]
  (cond
    (map? action) (or (get-in action [:action :id]) (:id action))
    :else action))

(defn wait-for-action
  "Poll an action until it completes and return the final action map.

  `action` is an action id, an action map, or the response from an action
  helper such as `reboot-droplet`. Throws ex-info if the action errors or
  does not finish within :timeout-ms.

  Options:
    :timeout-ms   default 300000 (5 minutes)
    :interval-ms  default 2000"
  [client action & {:keys [timeout-ms interval-ms]
                    :or {timeout-ms 300000 interval-ms 2000}}]
  (let [id (action-id action)
        deadline (+ (System/currentTimeMillis) timeout-ms)]
    (when-not id
      (throw (ex-info "Expected an action id or action map"
                      {:type :invalid-action :value action})))
    (loop []
      (let [current (:action (request! client :get [:actions id]))]
        (case (:status current)
          "completed" current
          "errored" (throw (ex-info (str "DigitalOcean action " id " errored")
                                    {:type :action-errored :action current}))
          (if (< (System/currentTimeMillis) deadline)
            (do (Thread/sleep (long interval-ms))
                (recur))
            (throw (ex-info (str "Timed out waiting for DigitalOcean action " id)
                            {:type :action-timeout :action current}))))))))
