(ns lyrics.core
  (:require [lyrics.scraping :as scraping]
            [clojure.tools.reader.edn :as edn]))

; (defn lyrics-to-neo4j
;   "Retrieve lyrics, parse them, and upload them to Neo4j"
;   ([conn letters]
;     (doseq [l (scraping/get-all-lyrics letters)]
;       (if-let [parsed (parsing/parse-blob l)]
;         (neo4j/parse-song conn parsed)))))

(defn -main
  "Main function"
  [])
  ; (lyrics-to-neo4j (neo4j/get-conn) ["1" "a" "b" "c" "d" "e" "f" "g" "h" "i"
  ;                                    "j" "k" "l" "m" "n" "o" "p" "q" "r" "s"
  ;                                    "t" "u" "v" "w" "x" "y" "z"]))
