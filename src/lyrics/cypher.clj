(ns lyrics.cypher
  (:require [clojurewerkz.neocons.rest.cypher :as cy]
           [lyrics.utils :refer [get-query-str]])
  (:import clojurewerkz.neocons.rest.Connection))

(defn get-already-processed
  "Get the urls of songs already in the DB"
  [^Connection conn]
  (map #(-> % first :data :url)
       (:data (cy/query conn "MATCH (s:Song) RETURN s"))))

(defn get-query-attrs
  "Extract relevant entries from a song blob
  :param song: the song blob to process"
  [song]
  (select-keys song [:album :artist]))

(defn find-record
  "Retrieve a node from the DB, cast it to a Neocons record
  :param record-type: keyword representing the record type
  :param conn: Neo4J db connection
  :param song: the song-blob to process"
  [record-type conn song]
  (if-let [res (-> (cy/tquery conn
                              (get-query-str (name record-type)
                                             [record-type]
                                             (get-query-attrs song)))
                   first
                   (get "res"))]
    res))
