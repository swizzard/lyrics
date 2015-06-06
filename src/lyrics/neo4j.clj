(ns lyrics.neo4j
  (:require (clojurewerkz.neocons [rest :as nr])
            (clojurewerkz.neocons.rest [nodes :as nn]
                                       [relationships :as nrl]
                                       [records :as recs]
                                       [cypher :as cy])
            [clojure.core.match :refer [match]]
            [environ.core :refer [env]]))

(defn get-conn
  "Get a Neo4j DB connection"
  []
  (nr/connect (env :neo4j-url)))

(def conn
  "Our connection"
  (get-conn))

(defn window-tokens
  "Pad a seq of tokens and convert it to a seq of 2-token 'windows'"
  [tokens]
  (partition 2 1 (keep-indexed (fn [x y] [x y]) (concat [:start]
                                                        tokens
                                                        [:end]))))

(def uploaded-artists
  "A ref holding a local cache of uploaded artists"
  (ref {}))

(def uploaded-albums
  "A ref holding a local cache of uploaded albums"
  (ref {}))

(defn get-album-cache-name
  "Get the unique artist+album key for caching purposes"
  [artist-name album-name]
  (str artist-name ":" album-name))

(defn create-artist
  "Create a Neo4j node representing an artist"
  [conn song-blob]
  (let [artist (:url-artist song-blob)
        artist-node (nn/create conn {:artist artist})]
    (dosync (alter uploaded-artists assoc artist (:id artist-node))
            artist-node)))

(defn create-album
  "Create a Neo4j node representing an album"
  [conn song-blob]
  (let [album (:album song-blob)
        artist (:url-artist song-blob)
        album-node (nn/create conn {:album album})
        artist-node (or (nn/get conn (get @uploaded-artists artist))
                        (create-artist conn song-blob))]
    (dosync
      (nrl/create conn album-node artist-node :by_artist)
      (alter uploaded-albums assoc (get-album-cache-name artist album)
                                   (:id album-node))
      album-node)))

(defn create-song
  "Create a Neo4j node representing a song"
  [conn album-node song-blob]
  (let [song-node (nn/create conn (select-keys song-blob [:url-song :written-by
                                                          :title :track-number
                                                          :url]))]
    (nrl/create conn album-node song-node :in_album)
    song-node))

(defn upload-bigram
  "Create Neo4j nodes representing words and connect them to the graph"
  [conn song bigram]
  (let [query-str
        "MATCH (artist {artist: {artist}})
          <-[:by_artist]-(album {album: {album}})
          <-[:on_album]-(song: {title: {song_title} track_no: {track_no}})
          <-[:in_song]-(token1 {token: {token} idx: {idx}})
         RETURN token1"]
    (match [(first bigram) (second bigram)]
          [[0 :start] [1 token]] (let [token-node (nn/create conn {:token token
                                                                    :idx 1})]
                                    (nrl/create conn token-node song :in_song))
          [[_ token]  [_ :end]] nil
          [[idx1 token1] [idx2 token2]]
              (let [token1-node (recs/instantiate-record-from
                                  (-> (cy/query conn
                                                query-str
                                                {:artist (:url-artist song)
                                                 :album (:album song)
                                                 :song_title (:title song)
                                                 :track_no (:track-number song)
                                                 :token token1
                                                 :idx idx1})
                                      :data
                                      ffirst))
                    token2-node (nn/create conn {:token token2 :idx idx2})]
                (nrl/create conn token1-node token2-node :followed-by)
                (nrl/create conn token2-node song :in-song)))))

(defn parse-song
  "Parse a song-blob and upload its components to Neo4j"
  [conn song-blob]
  (let [artist-node (or (get @uploaded-artists (:url-artist song-blob))
                        (create-artist conn song-blob))
        album-node (or (get @uploaded-albums (:album song-blob))
                       (create-album conn song-blob))
        song (create-song conn album-node song-blob)]
    (nrl/create conn song album-node :in-album)
    (map (partial upload-bigram conn song)
          (window-tokens (:tokenized-lyrics song-blob)))))
