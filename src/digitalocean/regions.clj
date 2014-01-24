(ns digitalocean.regions)

(defn regions
  "Fetch all Digital Ocean regions"
  ([client-id api-key]
  (->>>
    (request "regions" client-id api-key)
    :regions))
  ([creds] (apply regions (vals creds))))

(defn region-ids
  "Returns all Digital Ocean region ids"
  ([client-id api-key]
    (regions client-id api-key))
  ([creds]
    (apply region-ids (vals creds))))
