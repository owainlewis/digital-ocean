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

(defn image-id-action
  "Helper function for images with an image_id"
  ([action]
  (fn [client-id api-key droplet-id]
      (let [f (core/simple-id-action "images" droplet-id action)]
        (f client-id api-key)))))

;; GET /images/[image_id]

(defn image
  "Fetch a single image"
  [client-id api-key image-id]
  (let [response (core/request (str "images/" image-id) client-id api-key)]
    (->> response :image)))

(def destroy-droplet (image-id-action "destroy"))

;; GET /images/[image_id]/destroy

(def destroy-droplet (image-id-action "destroy"))

;; GET /images/[image_id]/transfer
