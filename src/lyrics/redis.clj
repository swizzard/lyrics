(ns lyrics.redis
  (:require [taoensso.carmine :as car :refer [wcar]]
            [environ.core :refer [env]]))

(def ^:private redis-host (:redis-host env))

(def ^:private redis-port (->> env :redis-port (re-find #"\d+") Integer.))

(def ^:private conn {:pool {}
                     :spec {:host redis-host
                            :port redis-port}})

(def ^:private redis-key (:redis-key env))

(defn cached? [v] (if (= 0 (wcar conn (car/sadd redis-key v)))
                      true
                      false))

(defn- clear-cache* [k] (wcar conn (car/del k)))

(defn clear-cache [] (clear-cache* redis-key))
