(ns digitalocean.v2.core
  (:refer-clojure :exclude [keys])
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [org.httpkit.client :as http])
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
           :headers {}}
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

(defn- parse-json [body]
  (when-not (str/blank? (str body))
    (json/parse-string body true)))

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
         opts (cond-> {:method method
                       :url (request-url client path)
                       :headers req-headers
                       :timeout (or timeout (:timeout client))}
                (seq query) (assoc :query-params (normalize-api-data query))
                (some? body) (assoc :body (json/generate-string
                                           (normalize-api-data body))))
         {:keys [status headers body error]} @(http/request opts)
         parsed-body (parse-json body)
         response {:ok? (and status (<= 200 status 299))
                   :status status
                   :headers headers
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

(def vpcs (generic :get :vpcs))
(def get-vpc vpcs)
(def create-vpc (generic :post :vpcs))
(def update-vpc (generic :patch :vpcs))
(def delete-vpc (generic :delete :vpcs))

(defn list-all
  "Fetch every page for a collection endpoint.

  (list-all client :droplets :droplets)
  (list-all client [:domains \"example.com\" :records] :domain-records)"
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
