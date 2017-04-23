(ns lyrics.utils
  (:require [clojure.string :refer [capitalize join]]
            [clojure.tools.reader.edn :as edn]))

(defn from-edn
  "Read an edn file
   :param edn-file: path to edn file to read
   :type edn-file: string (filepath)
   :returns: data"
  [edn-file] (with-open [r (clojure.java.io/reader edn-file)]
                            (edn/read (java.io.PushbackReader. r))))

(defn filter-contains
  "Returns a transducer that will remove all entities that are also in coll"
  [coll]
  (remove (set coll)))

(defn take-until-nils
  "A transducer that takes non-nil values until a certain
   number of nils have been seen"
  [max-nils]
    (fn [xf]
      (let [nils-count (atom 0)]
        (fn
          ([] (xf))
          ([res] (xf res))
          ([res input]
           (if (nil? input)
             (if (= max-nils (swap! nils-count inc))
               (reduced res)
               res)
             (xf res input)))))))

(defn strify
  "Join a sequence of strings with spaces
   :param ss: the sequence of strings to join
   :type ss: seq
   :returns: string"
  [ss]
  (join " " ss))

