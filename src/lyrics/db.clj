(ns lyrics.db
  (:require [clojure.core.async :refer [<! <!! >! go thread] :as async]
            [com.stuartsierra.component :as component]
            [korma.core :refer [defentity database entity-fields
                                has-many many-to-many insert]]
            [korma.db :refer [defdb]]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]))

;; shared config
(def db-config {:driver "com.mysql.jdbc.Driver"
                :subprotocol "mysql"
                :user "lyrics_user"
                :password "factsaresimple"
                :subname "//127.0.0.1:3306/lyrics"})

;; korma
(declare word song album artist)
(defdb conn db-config)

(defentity word
  (database conn)
  (entity-fields :string :line_number :position))

(defentity song
  (database conn)
  (entity-fields :title :track_listing)
  (has-many word)
  (many-to-many artist :artists_songs))

(defentity album
  (database conn)
  (entity-fields :title :year :genre)
  (has-many song)
  (many-to-many artist :artists_albums))

(defentity artist
  (database conn)
  (entity-fields :artist_name)
  (many-to-many album :artists_albums)
  (many-to-many song :artists_songs)
  (many-to-many artist :artists_related))

(defentity artists_related
  (database conn)
  (entity-fields :artist1_id :artist2_id :relation_type))


(defrecord DBUpload [entity pkid fields cache])

; (defn upload [^DBUpload {:keys [entity pkid fields cache]}]
;   (if-not (get @cache pkid)
;     (insert entity (values fields))))

; (defmulti to-entity (fn [m c] (:type m)))
; (defmulti to-entity ::artist [{artist-name :artist} {cache :artists-cache}]
;   (DBUpload. artist artist-name {:artist_name artist-name} cache))
; (defmulti to-entity ::album [{:keys [title 

; (defrecord LyricsUnpacker [lyrics-chan output-chan]

;; ragtime
(def config {:datastore (jdbc/sql-database db-config)
             :migrations (jdbc/load-resources "migrations")})

(defn migrate [] (repl/migrate config))
(defn rollback [] (repl/rollback config))



