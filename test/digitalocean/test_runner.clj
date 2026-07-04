(ns digitalocean.test-runner
  (:require [clojure.test :as test]
            [digitalocean.core-test]
            [digitalocean.v2.core-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'digitalocean.core-test
                                             'digitalocean.v2.core-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
