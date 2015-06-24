(ns lyrics.neo4j
  (:require (clojurewerkz.neocons [rest :as nr])
            (clojurewerkz.neocons.rest [nodes :as nn]
                                       [labels :as nl]
                                       [relationships :as nrl]
                                       [records :refer
                                        [instantiate-record-from]])
            [environ.core :refer [env]]
            [lyrics.cypher :refer [find-record]])
  (:import clojurewerkz.neocons.rest.Connection))

(defn get-conn
  "Get a Neo4j DB connection"
  []
  (nr/connect (env :neo4j-url)))

(def cache
  "A cache of uploaded nodes"
  {:artist (atom {})
   :album (atom {})})

(defn get-album-cache-name
  "Get the unique artist+album key for caching purposes"
  [artist-name album-name]
  (str artist-name ":" album-name))

(declare make-node)

(defn get-node
  "Get a node by either getting it from the local cache, retrieving it from
   the DB, or creating it. The node is cached locally and then returned
  :param conn: Neo4J DB connection
  :param node-type: keyword representing the node type (:artist, :album)
  :param song: the song blob to retrieve information from"
  [^Connection conn node-type song]
  (swap! (get cache node-type)
         (or (get @(get cache node-type) node-type)
             (find-record conn node-type song)
             (make-node conn node-type song))))

(defmulti make-node
  "Make a Neo4J node of the appropriate type, along with its
   label and relationships
  :param conn: Neo4J DB connection
  :param node-type: keyword representing the node type
                    (:album, :artist, :song)
  :param song: song-blob to parse"
  (fn [^Connection conn node-type song] node-type))

(defmethod make-node :artist [^Connection conn node-type song]
  (let [node (nn/create conn (select-keys song [:artist]))]
    (nl/add conn node "Artist")
    node))

(defmethod make-node :album [^Connection conn node-type song]
  (let [album-node (nn/create conn (select-keys song [:album]))
        artist-node (get-node conn :artist song)]
    (nl/add conn album-node "Album")
    (nrl/create conn album-node artist-node :by_artist)
    album-node))

(defmethod make-node :song [^Connection conn node-type song]
  (let [artist-node (get-node conn :artist song)
        album-node (get-node conn :album song)
        song-node (nn/create conn (select-keys song [:url-song :written-by
                                                     :title :track-number
                                                     :url]))]
    (nl/add conn song-node "Song")
    (nrl/create conn song-node artist-node :by_artist)
    (nrl/create conn song-node album-node :in_album)
    song-node))

(defn create-token
  "Create a Neo4J node representing a token
  :param conn: Neo4J DB connection
  :param song: song-blob to parse
  :param prev: node representing the previous word in the song"
  [^Connection conn song prev ^Long idx ^String token]
  (let [token-node (nn/create conn {:token token :idx idx})
        token-label (if (= "\n" token) ["Token" "Newline"]
                                       "Token")]
    (nl/add conn token-node token-label)
    (nrl/create conn token-node song :in_song)
    (if prev
      (nrl/create conn prev token-node :next_word))
    (println token-node)
    token-node))

(defn parse-song
  "Parse a song-blob and upload its components to Neo4j"
  [^Connection conn song-blob]
  (let [artist-node (get-node conn :artist song-blob)
        album-node (get-node conn :album song-blob)
        song (make-node conn :song song-blob)
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
