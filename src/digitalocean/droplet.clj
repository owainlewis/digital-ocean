(ns digitalocean.droplet
  (require [digitalocean.core :as core]))

;; GET /droplets

(defn droplets
  "This method returns all active droplets that are currently running in your account.
   All available API information is presented for each droplet"
  ([client-id api-key]
    (core/get-for "droplets" client-id api-key)))

(defn droplets-with-status
  "Finds all droplets matching a given status"
  ([client-id api-key status]
    (filter (fn [droplet]
              (= (:status droplet)
                 (if (keyword status)
                   (name status)
                     status)))
      (droplets client-id api-key)))
  ([creds status]
    (let [[k t] ((juxt :client :key) creds)]
      (droplets-with-status k t status))))

;; GET /droplets/new

(defn new-droplet
  "This method allows you to create a new droplet. See the required parameters section below
   for an explanation of the variables that are needed to create a new droplet
   - name Required, String, this is the name of the droplet - must be formatted by hostname rules
   - size_id Required, Numeric, this is the id of the size you would like the droplet created at
   - image_id Required, Numeric, this is the id of the image you would like the droplet created with
   - region_id Required, Numeric, this is the id of the region you would like your server in
   - ssh_key_ids Optional, Numeric CSV, comma separated list of ssh_key_ids that you would like to be added to the server
   - private_networking Optional, Boolean, enables a private network interface if the region supports private networking
   - backups_enabled Optional, Boolean, enables backups for your droplet."
  [client-id api-key droplet-params]
  (core/when-params droplet-params [:name :size_id :image_id :region_id]
    (core/request "droplets/new" client-id api-key
      droplet-params)))

(comment
  (new-droplet client-id api-key
    {:name "Demo"
     :size_id "66"
     :image_id "473123"
     :region_id "1"}))

;; GET /droplets/[droplet_id]

(defn droplet
  "This method returns full information for a specific droplet ID that is passed in the URL." ()
  ([client-id api-key droplet-id]
    (let [response (core/request (str "droplets/" droplet-id) client-id api-key)]
      (->> response :droplet))))

(defn droplet-by-name
  "Case sensitive exact match name lookup"
  ([client-id api-key droplet-name]
    (let [droplets (droplets client-id api-key)]
      (first
        (for [d droplets :when (= (:name d) droplet-name)] d))))
    ([creds droplet-name]
      (let [[k t] ((juxt :client :key) creds)]
        (droplet-by-name k t droplet-name))))

(defn droplet-id-action
  "Abstraction for the shape of these repetitive functions"
  ([action]
  (fn [client-id api-key droplet-id]
      (let [f (core/simple-id-action "droplets" droplet-id action)]
        (f client-id api-key)))))

;; GET /droplets/[droplet_id]/reboot

(def reboot-droplet
  "This method allows you to reboot a droplet. This is the preferred method to use
   if a server is not responding."
  (droplet-id-action "reboot"))

;; GET /droplets/[droplet_id]/power_cycle

(def power-cycle-droplet
  "This method allows you to power cycle a droplet.
   This will turn off the droplet and then turn it back on"
  (droplet-id-action "power_cycle"))

;; GET /droplets/[droplet_id]/shutdown

(def shutdown-droplet
  "This method allows you to shutdown a running droplet. The droplet will remain in your account"
  (droplet-id-action "shutdown"))

;; GET /droplets/[droplet_id]/power_off

(def power-off-droplet
  "This method allows you to poweroff a running droplet.
   The droplet will remain in your account"
  (droplet-id-action "power_off"))

;; GET /droplets/[droplet_id]/power_on

(def power-on-droplet
  "This method allows you to poweron a powered off droplet"
  (droplet-id-action "power_on"))

;; GET /droplets/[droplet_id]/password_reset

(def power-on-droplet (droplet-id-action "password_reset"))

;; GET /droplets/[droplet_id]/resize

(defn resize-droplet
  "This method allows you to resize a specific droplet to a different size.
   This will affect the number of processors and memory allocated to the droplet"
  [client-id api-key droplet-id size-id]
  (core/request
    (format "droplets/%/resize" droplet-id)
      client-id api-key {:size_id size-id}))

;; GET /droplets/[droplet_id]/snapshot

(defn snapshot-droplet
  "droplet_id Required, Numeric, this is the id of your droplet that you want to snapshot
   name Optional, String, this is the name of the new snapshot you want to create.
   If not set, the snapshot name will default to date/time"
  ([client-id api-key droplet-id name]
    (core/request
      (format "droplets/%/snapshot" droplet-id)
        client-id api-key {:name name})))

;; GET /droplets/[droplet_id]/restore

(defn restore-droplet
  "This method allows you to restore a droplet with a previous image or snapshot.
   This will be a mirror copy of the image or snapshot to your droplet.
   Be sure you have backed up any necessary information prior to restore"
  [client-id api-key droplet-id image_id]
  (core/request
    (format "droplets/%/restore" droplet-id)
      client-id api-key {:image_id image_id}))

;; GET /droplets/[droplet_id]/rebuild

(defn rebuild-droplet
  "This method allows you to reinstall a droplet with a default image. This is useful if you
   want to start again but retain the same IP address for your droplet"
  [client-id api-key droplet-id image_id]
  (core/request
    (format "droplets/%/rebuild" droplet-id)
      client-id api-key {:image_id image_id}))

;; GET /droplets/[droplet_id]/rename

(defn rename-droplet
  [client-id api-key droplet-id name]
  (core/request
    (format "droplets/%/rename" droplet-id)
      client-id api-key {:name name}))

;; GET /droplets/[droplet_id]/destroy

(def destroy-droplet (droplet-id-action "destroy"))
