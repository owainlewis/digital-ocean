(ns digitalocean.core
  (:refer-clojure :exclude [keys])
  (:require [digitalocean.v2.core]))

(doseq [[sym v] (ns-publics 'digitalocean.v2.core)]
  (intern *ns* (with-meta sym (meta v)) (var-get v)))
