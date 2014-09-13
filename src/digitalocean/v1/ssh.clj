(ns digitalocean.v1.ssh
  (require [digitalocean.core :as core]))

;; GET /ssh_keys

(defn ssh-keys
  "This method lists all the available public SSH keys in your account that can be added to a droplet"
  ([client-id api-key]
    (core/get-for "ssh_keys" client-id api-key))
  ([creds]
    (apply ssh-keys (vals creds))))

;; GET /ssh_keys/new

;; GET /ssh_keys/[ssh_key_id]

(defn ssh-key
  "This method shows a specific public SSH key in your account that can be added to a droplet"
  [client-id api-key ssh-key-id]
  (let [response (core/request (str "ssh_keys/" ssh-key-id) client-id api-key)]
    (->> response :ssh_key)))

;; GET /ssh_keys/[ssh_key_id]/edit

;; GET /ssh_keys/[ssh_key_id]/destroy
