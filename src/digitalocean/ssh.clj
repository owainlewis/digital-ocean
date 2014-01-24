(ns digitalocean.ssh)

;; GET /ssh_keys

(defn ssh-keys
  "Fetch all SSH keys for your account"
  ([client-id api-key]
    (get-for "ssh_keys" client-id api-key))
  ([creds]
    (apply ssh-keys (vals creds))))

;; GET /ssh_keys/new

;; GET /ssh_keys/[ssh_key_id]

;; GET /ssh_keys/[ssh_key_id]/edit

;; GET /ssh_keys/[ssh_key_id]/destroy
