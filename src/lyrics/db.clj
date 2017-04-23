(ns lyrics.db
  (:require [clojure.java.jdbc :refer [with-db-transaction]]
            [environ.core :refer [env]]
            [hugsql.core :refer [def-db-fns]]))


(def db (-> env (select-keys [:dbname :dbtype :password])
                (assoc :user (:dbuser env))))

(def-db-fns "lyrics/sql/setup.sql")
(def-db-fns "lyrics/sql/tokens.sql")

(defn drop-all []
  (do
    (drop-tokens-table db)
    (drop-songs-artists-table db)
    (drop-songs-table db)
    (drop-artists-table db)
    (drop-albums-table db)
    ))

(defn setup-db []
  (do (create-albums-table db)
      (create-artists-table db)
      (create-songs-table db)
      (create-songs-artists-table db)
      (create-tokens-table db)))

(defn- unpack-returned [res] (:insert_id res))

(defn insert-or-get!* [insert select doc]
  (with-db-transaction [tx db]
    (if-let [existing-id (->> doc (insert tx) unpack-returned)]
      existing-id
      (->> doc (select tx) :id))))

(defmulti insert-or-get! (fn [table doc] table))
(defmethod insert-or-get! :album [_ doc]
  (insert-or-get!* insert-album! get-album-id doc))
(defmethod insert-or-get! :artist [_ doc]
  (insert-or-get!* insert-artist! get-artist-id doc))
(defmethod insert-or-get! :song [_ doc]
  (insert-or-get!* insert-song! get-song-id doc))

(defn- insert-t [t song-id album-id] 
   (insert-token! db (assoc t :song-id song-id :album-id album-id)))
(defn- insert-sa [{:keys [featuring] :or {featuring false} :as artist} song-id]
  (let [artist-id (insert-or-get! :artist artist)]
    (insert-song-artist! db {:song-id song-id :artist-id artist-id
                             :featuring featuring})))
(defn- add-album-id [album-id s] (assoc s :album-id album-id))

(defn write-song! [{:keys [album artists song tokens]}]
  (let [album-id (insert-or-get! album :album)
        song-id (->> song (add-album-id album-id) (insert-or-get! :song))]
    (doseq [t tokens] (insert-t t song-id album-id)
    (doseq [a artists] (insert-sa a song-id)))))

