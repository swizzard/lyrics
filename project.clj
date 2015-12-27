(defproject lyrics "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [com.stuartsierra/component "0.3.0"]
                 [environ "1.0.1"]
                 [http-kit "2.1.18"]
                 [instaparse "1.3.5"]
                 [hickory "0.5.4"]
                 [clucy "0.4.0"]
                 [slingshot "0.12.2"]
                 ]
  :jvm-opts ["-Xmx4g" "-server"]
  :main lyrics.core)
