(ns lyrics.core
  (:require [lyrics.scraping :as scraping]
            [lyrics.parsing :as parsing]
            [lyrics.mongo :refer [insert]]))

(defn lyrics-to-mongo [artists]
  (doseq [l (scraping/get-all-lyrics artists)]
    (prn (parsing/parse-blob l))
    (if (some? (seq (:parsed-lyrics l)))
        (insert l))))
