(ns lyrics.neo4j
  (:require (clojurewerkz.neocons [rest :as nr])
            (clojurewerkz.neocons.rest [nodes :as nn]
                                       [labels :as nl]
                                       [relationships :as nrl]
                                       [records :as recs]
                                       [cypher :as cy])
            [clojure.core.match :refer [match]]
            [environ.core :refer [env]]))

(defn get-conn
  "Get a Neo4j DB connection"
  []
  (nr/connect (env :neo4j-url)))

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
  [conn {artist :url-artist :as song-blob}]
  (let [artist-node (nn/create conn {:artist artist})]
    (nl/add conn artist-node "Artist")
    (dosync (alter uploaded-artists assoc artist artist-node)
            artist-node)))

(defn get-artist-node [conn {:keys [url-artist] :as song}]
  (or (get @uploaded-artists url-artist)
      (create-artist conn song)))

(defn create-album
  "Create a Neo4j node representing an album"
  [conn {album :album artist :url-artist
         :or {album "No Album"} :as song-blob}]
  (let [album-node (nn/create conn {:album album})
        artist-node (get-artist-node conn song-blob)]
    (nl/add conn album-node "Album")
    (nrl/create conn album-node artist-node :by_artist)
    (dosync
      (alter uploaded-albums assoc (get-album-cache-name artist album)
                                   album-node))
      album-node))

(defn get-album-node [conn {:keys [url-artist album] :as song}]
  (let [album-name (get-album-cache-name url-artist album)]
    (or (get @uploaded-albums album-name)
        (create-album conn song))))

(defn create-song
  "Create a Neo4j node representing a song"
  [conn artist-node album-node song-blob]
  (let [song-node (nn/create conn (select-keys song-blob [:url-song :written-by
                                                          :title :track-number
                                                          :url]))]
    (nl/add conn song-node "Song")
    (nrl/create conn song-node artist-node :by_artist)
    (nrl/create conn song-node album-node :in_album)
    song-node))

(defn create-token
  "Create a Neo4J node representing a token"
  [conn song prev idx token]
  (let [token-node (nn/create conn {:token token :idx idx})
        token-label (if (= "\n" token) ["Token" "Newline"]
                                      "Token")]
    (println token-node)
    (nl/add conn token-node token-label)
    (nrl/create conn token-node song :in_song)
    (if prev
      (nrl/create conn prev token-node :next_word))
    token-node))

(defn parse-song
  "Parse a song-blob and upload its components to Neo4j"
  [conn song-blob]
  (let [artist-node (get-artist-node conn song-blob)
        album-node (get-album-node conn song-blob)
        song (create-song conn artist-node album-node song-blob)
        tokens (:tokenized-lyrics song-blob)]
    (loop [prev nil
           idx 0
           token (first tokens)
           tail (rest tokens)]
      (if (some? token)
        (recur
          (create-token conn song prev idx token)
          (inc idx)
          (first tail)
          (rest tail))))))
