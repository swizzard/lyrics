(defproject lyrics "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [instaparse "1.4.5"]
                 [enlive "1.1.6"]
                 [clj-http "3.4.1"]
                 [clj-http-fake "1.0.3"]
                 [environ "1.1.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.mariadb.jdbc/mariadb-java-client "2.0.0-RC"]
                 [org.clojure/java.jdbc "0.7.0-alpha3"]
                 [com.layerware/hugsql "0.4.7"]
                 [com.taoensso/carmine "2.16.0"]
                ]
  :plugins [[lein-environ "1.0.0"]]
  :jvm-opts ["-Xmx16g" "-server"]
  :repl-options {:timeout 120000}
  :main lyrics.core)
