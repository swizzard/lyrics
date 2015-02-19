(ns lyrics.mongo
  (:require (monger [core :as mg] 
                    [collection :as mc]
                    [operators :as mo]))
  (:import [org.bson.types ObjectId]))


(def db (-> (mg/connect) (mg/get-db "lyrics")))
(def coll "lyrics")

(defn insert [m] (mc/update db coll {:artist (:artist m)}
                                    {mo/$push {:albums 
                                               {"$each" (:albums m)}}}
                                    {:upsert true}))
