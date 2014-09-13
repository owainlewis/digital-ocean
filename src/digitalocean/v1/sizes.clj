(ns digitalocean.v1.sizes
  (require [digitalocean.v1.core :as core]))

(defn sizes
  "This method returns all the available sizes that can be used to create a droplet"
  ([client-id api-key]
     (core/get-for "sizes" client-id api-key)))
