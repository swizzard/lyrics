(ns lyrics.models
  (:require [korma.core :refer :all]
            [lyrics.db :refer [conn]]))

(declare word song album artist)
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
  (entity-fields :title :year)
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
