(ns digitalocean.v1.domains
  (require [digitalocean.core :as core]))

;; GET /domains

(defn domains
  "This method returns all of your current domains"
  [client-id api-key]
  (core/get-for "domains" client-id api-key))

;; GET /domains/new
(defn new-domain
  "This method creates a new domain name with an A record for the specified [ip_address]
   - name Required, string, name of the domain.
   - ip_address Required, string, ip address for the domain's initial a record"
  [client-id api-key domain-params]
  (core/when-params domain-params [:name :ip_address]
    (core/request "domains/new" client-id api-key
      domain-params)))

;; GET /domains/[domain_id]

(defn domain
  "This method returns the specified domain
   - domain_id Required, Integer or Domain Name (e.g. domain.com), specifies the domain to display"
  [client-id api-key domain-id]
  (let [response (core/request (str "domains/" domain-id) client-id api-key)]
    (->> response :domain)))

;; GET /domains/[domain_id]/destroy

;; GET /domains/[domain_id]/records

;; GET /domains/[domain_id]/records/new

;; GET /domains/[domain_id]/records/[record_id]

;; GET /domains/[domain_id]/records/[record_id]/edit

;; GET /domains/[domain_id]/records/[record_id]/destroy
