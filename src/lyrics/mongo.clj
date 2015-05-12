(ns lyrics.mongo
  (:require (monger [core :as mg]
                    [collection :as mc]
                    [operators :as mo]))
  (:import [org.bson.types ObjectId]))

(def db
  "The database connection"
  (mg/get-db (mg/connect) "lyrics"))

(def coll
  "The MongoDB collection"
  "lyrics")

(defn insert
  "Insert a map into the database
   :param m: the map to insert
   :type m: map
   :returns: nil (side-effecting)"
  [m] (mc/insert db coll m))
