(ns digitalocean.images
  (require [digitalocean.core :as core]))

;; GET /images

(defn all-images
  "List all Digital Ocean images"
  [client-id api-key]
  (core/get-for "images" client-id api-key))

(def images (memoize all-images))

(defn ubuntu-images [client-id api-key]
  (->> (images client-id api-key)
       (filter #(boolean
                 (re-find #"Ubuntu"
                   (:distribution %))))))

;; GET /images/[image_id]

;; GET /images/[image_id]/destroy

;; GET /images/[image_id]/transfer
