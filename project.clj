(defproject lyrics "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [instaparse "1.3.5"]
                 [clj-http "1.0.1"]
                 [enlive "1.1.5"]]
  :plugins [[lein-gorilla "0.3.4"]]
  :jvm-opts ["-Xmx1g" "-server"])
