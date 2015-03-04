(ns lyrics.parsing
  (:require [clojure.string :refer [split split-lines lower-case]]))

(def load-pat #"\n?\"([^\"]*)\" is track #(\d+) on the album (.+?)\. It was written by (.+?)\.\t?")

(defn parse-load [load-line]
  (if-let [parsed-load (re-matches load-pat load-line)]
    ;; re-matches returns vec, 0th elem of which is full match
    (zipmap [:title :album :track-number :written-by] (next parsed-load))))

(def header-line-pat #"(Album|Artist|Title): .*\n")

(defn parse-line [^String line]
  (if (nil? (re-find header-line-pat line))
            (into {} 
                  (keep-indexed (fn [idx w] [idx w])
                                (map lower-case (split line #"\s"))))))

(defn parse-lyrics [^String lyrics]
  (into {} (keep-indexed (fn [idx l] (if-let [parsed (parse-line l)]
                                       [idx parsed]))
                         (split-lines lyrics))))

(defn parse-blob [blob]
  (merge blob
         (assoc (parse-load (:load blob))
                :lyrics (parse-lyrics (:lyrics blob)))))
