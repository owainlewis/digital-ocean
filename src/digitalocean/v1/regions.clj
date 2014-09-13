(ns digitalocean.v1.regions
  (require [digitalocean.v1.core :as core]))

(defn regions
  "Fetch all Digital Ocean regions"
  ([client-id api-key]
   (core/get-for "regions" client-id api-key))
  ([creds] (apply regions (vals creds))))

(defn region-ids
  "Returns all Digital Ocean region ids"
  ([client-id api-key]
   (regions client-id api-key))
  ([creds]
   (apply region-ids (vals creds))))
