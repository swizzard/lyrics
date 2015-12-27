(ns lyrics.lucene
  (:require [com.stuartsierra.component :as component]
            [clojure.java.io :refer [file]]
            [clucy.core :as clucy]
            [environ.core :refer [env]]
            [slingshot.slingshot :refer [throw+]]))


(defrecord Clucy [data-dir conn running?]
  component/Lifecycle
  (start [component]
    (println "connecting to Lucene index at" data-dir)
    (swap! running? not)
    (assoc component :conn (clucy/disk-index data-dir)))
  (stop [component]
    (println "disconnecting from Lucene")
    (swap! running? not)
    (assoc component :conn nil)))

(defn new-clucy
  ([data-dir]
   (if-let [f (file data-dir)]
    (if (and (.exists f) (.isDirectory f))
      (map->Clucy {:data-dir (env :lucene-index-dir)
                   :conn nil :running? (atom false)})
      (throw+ {:reason (str "Invalid Lucene data directory" data-dir)}))
    (throw+ {:reason "No Lucene data directory provided"})))
  ([] (new-clucy (env :lucene-index-dir))))


(defrecord ClucyMem [conn running?]
  component/Lifecycle
  (start [component]
    (when-not @running?
      (println "connecting to in-memory Lucene index")
      (swap! running? not)
      (assoc component :conn (clucy/memory-index))))
  (stop [compnent]
    (when @running?
      (println "disconnecting from Lucene")
      (swap! running? not)
      (assoc component :conn nil))))

(defn new-clucy-mem []
  (map->ClucyMem {:conn nil :running? (atom false)}))

(defn index-maps [component ms]
  (apply (partial clucy/add index) ms)) 

