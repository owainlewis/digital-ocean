(ns digitalocean.core-test
  (:refer-clojure :exclude [keys])
  (:require [clojure.test :refer [deftest is]]
            [digitalocean.core :as do]))

(deftest top-level-namespace-re-exports-v2-api
  (is (= "https://api.digitalocean.com/v2/tags/PROD"
         (do/resource-url :tags "PROD")))
  (is (fn? do/keys)))
