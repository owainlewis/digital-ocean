(ns digital-ocean.core
  ^{:doc "A Clojure wrapper for the Digital Ocean API"
    :author "Owain Lewis"}
  (:require [cheshire.core :as json]
            [org.httpkit.client :as http]))

(def do "https://api.digitalocean.com")

(defn env [k] (System/getenv k))

(defn creds [] 
  {:client (env "DO_CLIENT") :key (env "DO_KEY")})

(defn make-query-params 
  [client-id api-key & params]
  (let [base-params  (format "?client_id=%s&api_key=%s" client-id api-key)
        extra-params (apply str (interpose "&" (into [] params)))]
    (if (clojure.string/blank? extra-params)
      base-params
      (format "%s&%s" base-params extra-params))))

(defn url-with-params 
  [endpoint client-id api-key]
  (let [params (make-query-params client-id api-key)
        url (format "%s/%s%s" do endpoint params)]
    url))
  
(defn request 
  "Make a simple request. We are only dealing with GET requests
   for this particular API"
  [endpoint client-id api-key & params]
  (let [url (url-with-params endpoint client-id api-key)
        {:keys [status headers body error] :as resp} @(http/get url)]
    (if error
      {:error error}
      (json/parse-string body true))))

;; (defmacro request-with-auth-map [f m & args]
;;     (partial 

(defmacro ->>> 
  "Safe thread macro returns nil if there is an error else
   applies arrows threading to the response"
  [response & forms]
  `(when-not (contains? ~response :error)
     (->> ~response ~@forms)))
 
(defn droplets 
  "Returns all droplets"
  [client-id api-key]
  (->>> (request "droplets" client-id api-key)
        :droplets))
       
 (defn droplet 
   "Get a single droplet"
   [client-id api-key id]
   (->>>
     (request (str "droplets/" id) client-id api-key)
     :droplet))

 
