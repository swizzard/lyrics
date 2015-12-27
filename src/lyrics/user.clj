(ns lyrics.user
  (:require [com.stuarsierra.component :as component]
            [clojure.core.async :as async]
            [clojure.tools.namespace.repl :refer [refresh]]
            [lyrics.lucene :refer [new-clucy new-clucy-mem]]
            [lyrics.parsing :refer [new-lyrics-parser]]
            [lyrics.scraping-async (album-nodes :refer [new-album-node-extractor])
                                   (artist-links :refer [new-artist-links])
                                   (parsed-albums :refer [new-album-parser])]
            [lyrics.upload :refer [new-lyrics-uploader]]))


(defn make-system
  ([in-mem]
   (let [conn (if in-mem (new-clucy-mem) (new-clucy))]
   (component/system-map
     :lucene-conn conn
     :artist-links (new-artist-links (async/chan 1000))
     :
     ))
   ))
