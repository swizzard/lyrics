(ns lyrics.upload
  (:require [clojure.core.async :as async]
            [com.stuarsierra.component :as component]
            [lyrics.lucene :refer [index-maps]]))


(defrecord LyricsUploader [lucene-conn parsed-lyrics-chan batch-size running?]
  component/Lifecycle
  (start [component]
    (when-not @running?
      (println "starting LyricsUploader")
      (swap! running? not)
      (async/go-loop [maps []]
        (if @running?
          (if-let [m (async/<! parsed-lyrics-chan)]) 
            (if (= (count maps) batch-size)
              (do (index-maps lucene-conn maps)
                  (recur (empty maps)))
              (recur (conj maps m))))))
    component)
  (stop [component]
    (when @running?
      (println "stopping LyricsUploader")
      (swap! running? not))
    component))

(defn new-lyrics-uploader
  ([lucene-conn parsed-lyrics-chan batch-size]
   (map->LyricsUploader {:lucene-conn lucene-conn
                         :parsed-lyrics-chan parsed-lyrics-chan
                         :batch-size batch-size
                         :running? (atom false)}))
  ([lucene-conn parsed-lyrics-chan]
   (new-lyrics-uploader lucene-conn parsed-lyrics-chan 10)))
