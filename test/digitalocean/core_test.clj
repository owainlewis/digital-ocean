(ns digitalocean.core-test
  (:use midje.sweet)
  (:require [digitalocean.v2.core :as do]))

(facts "about resource urls"
  (do/resource-url :domains 1 2 3) => "https://api.digitalocean.com/v2/domains/1/2/3"
  (do/resource-url :domains) => "https://api.digitalocean.com/v2/domains/")
