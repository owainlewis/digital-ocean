(ns digitalocean.droplet
  (require [digitalocean.core :as core]))

;; GET /droplets

(defn droplets
  "Returns all droplets"
  ([client-id api-key]
    (core/get-for "droplets" client-id api-key)))

;; GET /droplets/new

(defn new-droplet
  "Create a new Digital Ocean droplet. Droplets params is a simple map
   Required params
     :name
     :size_id
     :image_id
     :region_id"
  [client-id api-key droplet-params]
  (core/when-params droplet-params [:name :size_id :image_id :region_id]
    (core/request "droplets/new" client-id api-key
      droplet-params)))

;; GET /droplets/[droplet_id]

(defn droplet
  ([client-id api-key droplet-id]
    (let [response (core/request (str "droplets/" droplet-id) client-id api-key)]
      (->> response :droplet))))

;; GET /droplets/[droplet_id]/reboot

;; GET /droplets/[droplet_id]/power_cycle

;; GET /droplets/[droplet_id]/shutdown

;; GET /droplets/[droplet_id]/power_off

;; GET /droplets/[droplet_id]/power_on

;; GET /droplets/[droplet_id]/password_reset

;; GET /droplets/[droplet_id]/resize

;; GET /droplets/[droplet_id]/snapshot

;; GET /droplets/[droplet_id]/restore

;; GET /droplets/[droplet_id]/rebuild

;; GET /droplets/[droplet_id]/rename

;; GET /droplets/[droplet_id]/destroy
