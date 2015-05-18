(defproject lyrics "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [instaparse "1.3.5"]
                 [enlive "1.1.5"]
                 [clj-http "1.0.1"]
                 [com.novemberain/monger "2.0.0"]
                 [clojurewerkz/neocons "3.1.0-beta3"]
                 [environ "1.0.0"]]
  :plugins [[lein-environ "1.0.0"]]
  :jvm-opts ["-Xmx4g" "-server"]
  :main lyrics.core)
