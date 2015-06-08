(ns lyrics.parsing
  (:require [clojure.string :refer [escape lower-case split]]))

(def load-pat
  "The pattern to parse the load"
  (re-pattern (str "\n?\"([^\"]*)\" is track #(\\d+) on the album "
                   "(.+?)\\.( It was written by (.+?)\\.)?\t?")))

(defn parse-load
  "Parse the load section of a blob
   :param load-line: the line to parse
   :type load-line: String
   :returns: map"
  [^String load-line]
  (if-let [parsed-load (re-matches load-pat load-line)]
    ;; re-matches returns vec, 0th elem of which is full match
    (zipmap [:title :track-number :album :written-by-line :written-by]
            (map str (next parsed-load)))))

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
  [blob]
  (if (seq (:url blob))
    (-> blob
        (merge (parse-load (:load blob)))
        (assoc :tokenized-lyrics (tokenize-lyrics (:lyrics blob))))))
