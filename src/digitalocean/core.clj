(ns digitalocean.core
  ^{:doc "A Clojure wrapper for the Digital Ocean API"
    :author "Owain Lewis"}
  (:require [cheshire.core :as json]
	    [schema.core :as scm]
	    [org.httpkit.client :as http]))

(defonce digital-ocean "https://api.digitalocean.com")

(def ^:dynamic auth-client nil)
(def ^:dynamic auth-key nil)

(defmacro with-auth
  "DSL helper for easy authentication"
  [client k & body]
  `(binding [auth-client ~client
	     auth-key ~k]
     (do ~@body)))

(defn env
  "Fetch an env var"
  [k] (System/getenv k))

(def CredsType (scm/enum :client :key))

(defn make-creds
  "Utility function for building a credentials map"
  [client api-key]
  { :client client :key api-key })

(defn creds []
  { :client (env "DO_CLIENT")
    :key    (env "DO_KEY") })

;; Helper methods
;; **************************************************************

(defn url-encode-params
  "Utility function for url encoding HTTP form params"
  [params-map]
  (into {}
    (for [[k v] params-map]
      [k (java.net.URLEncoder/encode v)])))

(defn make-query-params
  "Build query params"
  [client-id api-key & params]
  (let [base-params  (format "?client_id=%s&api_key=%s" client-id api-key)
	extra-params (apply str
		       (for [[k v] (url-encode-params (into {} params))]
			 (str "&" (if (keyword? k)
				    (name k) k) "=" v)))]
    (if (clojure.string/blank? extra-params)
      base-params
      (format "%s%s" base-params extra-params))))

(defn url-with-params
  [endpoint client-id api-key & params]
  (let [query-params (make-query-params client-id api-key (into {} params))
	url (format "%s/%s%s" digital-ocean endpoint query-params)]
    url))

;; HTTP request
;; **************************************************************

(defn request
  "Make a simple request. We are only dealing with GET requests
   for this particular API"
  [endpoint client-id api-key & params]
  (let [url (url-with-params endpoint client-id api-key (into {} params))
	{:keys [status headers body error] :as resp} @(http/get url)]
    (if error
      {:error error}
      (json/parse-string body true))))

;; **************************************************************

(defmacro ->>>
  "Safe thread macro
   Returns err message if there is an error
   else applies arrow threading to the response"
  [response & forms]
  `(if-not (contains? ~response :error)
     (->> ~response ~@forms)
     ~response))

(defn get-for
  "Helper function/abstraction to reduce duplication"
  ([resource client-id api-key]
  (let [k (keyword resource)]
    (->>> (request resource client-id api-key)
	  k))))

(defn enforce-params
  "Helper which throws assertion error if required params are
   missing"
  [params-map & keys]
  (let [f (partial contains? params-map)]
    (assert
      (every? true? (map f (into [] keys))))))

(defn simple-id-action
  "A helper function for id based urls i.e /droplets/:droplet_id/reboot"
  [target target-id action]
    (let [endpoint (format "%s/%s/%s" target target-id action)]
      (partial request endpoint)))

(defn pluralize
  "Helper function for pluralizing a string"
  [n s]
  (if (= 1 n) s (str s "s")))

(defmacro when-params
  "Takes a map {:a 1 :b 2} and a vector of required keys
   If any required keys are missing an exception is thrown
   else proceed with the computation"
  [subject-map keys & body]
  `(let [f# (partial contains? ~subject-map)]
     (if (every? true?
	   (map f# ~keys))
       (do ~@body)
	 (let [missing-params#
		(into []
		  (clojure.set/difference
		    (set ~keys)
		    (set (keys ~subject-map))))
	       key-list# (apply str
			   (map name
			     (interpose " " ~keys)))
	       msg# (format "Missing required %s %s"
		      (pluralize (count missing-params#) "param")
			missing-params#)]
	   (throw (Exception. msg#))))))
