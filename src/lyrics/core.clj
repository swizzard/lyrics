(ns lyrics.core
  (:require [lyrics.scraping :as scraping]
            [lyrics.parsing :as parsing]
            [lyrics.neo4j :as neo4j]))

(defn lyrics-to-neo4j
  "Retrieve lyrics, parse them, and upload them to Neo4j"
  ([conn artists]
    (doseq [l (scraping/get-all-lyrics artists)]
      (let [parsed (parsing/parse-blob l)]
        (if (some? (seq (:parsed-lyrics parsed)))
          (println parsed)
          (neo4j/parse-song conn parsed)))))
  ([conn] (lyrics-to-neo4j (scraping/get-all-artists))))


(defn -main
  "Main function"
  []
  (lyrics-to-neo4j neo4j/conn))
