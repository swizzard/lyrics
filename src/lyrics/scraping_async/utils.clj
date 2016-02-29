(ns lyrics.scraping-async.utils
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [hickory.core :refer [parse as-hickory]]))

(defn hickorize
  "Convert the body of a response into a hickory data structure"
  [body]
  (-> body parse as-hickory))

(defn subs-
  "Like subs, but works from end of string"
  [s x]
  (subs s (- (count s) x)))


(defn iterate-link
  "Alters a link by attaching an index to the end of it "
  [starting-link idx]
  {:pre [(> idx 1)]}
  (format "%s-%d.html" (subs starting-link 0 (- (count starting-link) 5)) idx))
