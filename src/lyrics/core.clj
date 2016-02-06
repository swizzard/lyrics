 (ns lyrics.core
   (:require [clojure.core.async :refer [chan]]
             [com.stuartsierra.component :as component]
             (lyrics.scraping-async [artist-links :refer [new-artist-links]]
                                    [album-nodes :refer [new-album-node-extractor]]
                                    [parsed-albums :refer [new-album-parser]]
                                    [song-parser :refer [new-song-parser]])
             [lyrics.lucene :refer [new-clucy new-clucy-mem]]))


(defn system []
  (let [artist-links-chan (chan 10)
        album-nodes-chan (chan 10)
        parsed-albums-chan (chan 10)
        songs-chan (chan 10)]
    (component/system-map
        :artist-links (new-artist-links artist-links-chan)
        :album-node-extractor (new-album-node-extractor artist-links-chan
                                                        album-nodes-chan)
        :album-parser (new-album-parser album-nodes-chan parsed-albums-chan)
        :song-parser (new-song-parser parsed-albums-chan songs-chan))))

