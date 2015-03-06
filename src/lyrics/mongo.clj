(ns lyrics.mongo
  (:require (monger [core :as mg] 
                    [collection :as mc]
                    [operators :as mo]))
  (:import [org.bson.types ObjectId]))


(def ^:private db (-> (mg/connect) (mg/get-db "lyrics")))
(def ^:private coll "lyrics")

(defn insert [m] (mc/insert db coll m))
