(ns digitalocean.v1.core-test
  (:use midje.sweet)
  (:require [digitalocean.v1.core :as do]))

(facts "about url encoding params"
  (do/url-encode-params {:name "A B"}) => {:name "A+B"}
  (do/url-encode-params {:name "Foo"}) => {:name "Foo"}
  (do/url-encode-params {:name "Foo & Bar"}) => {:name "Foo+%26+Bar"})

(facts "about generatoring query params"
  (do/make-query-params "A" "B") => "?client_id=A&api_key=B"
  (do/make-query-params "A" "B" {"C" "D"}) => "?client_id=A&api_key=B&C=D")

(facts "about url-with-params"
  (do/url-with-params "droplets" "A" "B" {"C" "D"})
    => "https://api.digitalocean.com/droplets?client_id=A&api_key=B&C=D")

(facts "about pluralization"
  (do/pluralize 1 "droplet") => "droplet"
  (do/pluralize 2 "droplet") => "droplets")

(facts "about requests with incorrect credentials"
  (do/request "droplets" "FOO" "BAR") =>
    {:error_message "Access Denied", :message "Access Denied", :status "ERROR"})
