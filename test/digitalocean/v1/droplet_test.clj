(ns digitalocean.v1.droplet-test
  (:use midje.sweet)
  (:require [digitalocean.v1.droplet :as dod])
  (:require [digitalocean.v1.core :as doc]))

(facts "about get-for with incorrect credentials"
  (dod/snapshot-droplet "FOO" "BAR" "1234" "name") =>  "OK"
  (provided (doc/request "droplets/1234/snapshot" "FOO" "BAR" {:name "name"} ) => "OK"))
