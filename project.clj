(defproject lyrics "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.stuartsierra/component "0.3.0"]
                 [http-kit "2.1.18"]
                 [instaparse "1.3.5"]
                 [enlive "1.1.5"]
                 [hickory "0.5.4"]
                 [clj-http "1.0.1"]
                 [com.novemberain/monger "2.0.0"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [korma "0.4.2"]
                 [ragtime "0.5.1"]
                 [mysql/mysql-connector-java "5.1.18"]]
  :jvm-opts ["-Xmx4g" "-server"]
  :aliases {"migrate" ["run" "-m" "lyrics.db/migrate"]
            "rollback" ["run" "-m" "lyrics.db/rollback"]}
  :main lyrics.core)
