(ns lyrics.parsing
  (:require [clojure.string :refer [escape lower-case split]]))

(defn parse-load
  "Parse the load section of a blob
   :param load-line: the line to parse
   :type load-line: String
   :returns: map"
  [^String load-line]
  (let [load-pat
        (re-pattern (str "[^\\w]*\"([^\"]*)\" is track #(\\d+) on the album "
                         "(.+?)\\.( It was written by (.+?)\\.)?\t?"))]
    (try
      (if-let [parsed-load (re-matches load-pat load-line)]
        ;; re-matches returns vec, 0th elem of which is full match
        (zipmap [:title :track-number :album :written-by-line :written-by]
                (map str (next parsed-load))))
    (catch Exception e
      (do
        (println "caught exception "
                 (.getMessage e)
                 " processing "
                 load-line)
        {})))))

(def header-line-pat
  "A pattern to parse a header-line"
  #"(Album|Artist|Title): .*\n")

(defn tokenize-lyrics
  "Tokenize lyrics, splitting by whitespace after
   escaping newlines to preserve them"
  [lyrics]
  (split (escape lyrics {\newline " \\n "}) #"\s"))

(defn parse-blob
  "Parse a blob
   :param blob: the blob to parse
   :type blob: map
   :returns: map"
  [{url :url load-line :load lyrics :lyrics :or {load-line "" lyrics ""}
    :as blob}]
  (if (seq load-line)
    (-> blob
        (merge (parse-load load-line))
        (assoc :tokenized-lyrics (tokenize-lyrics (get blob :lyrics ""))))))
