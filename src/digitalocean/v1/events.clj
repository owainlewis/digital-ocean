(ns digitalocean.v1.events
  (require [digitalocean.v1.core :as core]))

(defn wait-seconds [seconds]
  (Thread/sleep (* 1000 seconds)))


(defn event
  "This method returns full information for a specific event"
  ([client-id api-key event-id]
   (let [response (core/request (str "events/" event-id) client-id api-key)]
     (->> response :event))))

(defn wait-for-event-finish-limited
  "This methods wait until a an events reached 100% or max tries are reached"
  [client-id api-key event-id m s]
  (loop [max-tries m]
    (wait-seconds s)
    (let [resp (event client-id api-key event-id)]
      (if (and (> max-tries 0) (not (= "100" (:percentage resp))))
        (recur (dec max-tries))))))

(defn wait-for-event-finish
  "This methods wait until a an events reached 100% "
  [client-id api-key event-id]
  (if (not (= "100" (:percentage (event client-id api-key event-id))))
           (wait-for-event-finish-limited client-id api-key event-id 360 10))) ; max wait one hour, justified by https://www.digitalocean.com/community/questions/snapshot-time
