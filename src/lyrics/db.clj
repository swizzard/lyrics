(ns lyrics.db
  (:require [clojure.core.async :refer [<! <!! >! go thread] :as async]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [korma.core :refer [defentity database entity-fields
                                has-many many-to-many insert]]
            [korma.db :refer [defdb]]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]))

;; shared config
(def db-config
  "DB config"
  (merge (env :db)
         {:driver "com.mysql.jdbc.Driver"
          :subprotocol "mysql"}))

;; korma
(defdb conn db-config)

(defrecord DBUpload [entity pkid fields cache])

;; ragtime
(def config
  "Config for ragtime"
  {:datastore (jdbc/sql-database db-config)
              :migrations (jdbc/load-resources "migrations")})

(defn migrate
  "Ragtime migrate"
  []
  (repl/migrate config))
(defn rollback
  "Ragtime rollback"
  []
  (repl/rollback config))
