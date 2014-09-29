(ns digitalocean.v1.events-test
  (:use midje.sweet)
  (:require [digitalocean.v1.events :as doe])
  (:require [digitalocean.v1.core :as doc]))

(facts "about event"
  (doe/event "FOO" "BAR" "1234") =>  "event-info"
  (provided (doc/request "events/1234" "FOO" "BAR"  ) => {:event "event-info"}))
