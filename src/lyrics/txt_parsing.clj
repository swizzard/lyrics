(ns lyrics.txt-parsing
  (:require [clojure.core.match :refer [match]]
            [clojure.string :refer [escape lower-case split]]))

(defn split-lyrics [lyrics]
  (map #(split % #"\s+") (split lyrics #"\n+")))

(defn- make-token [tkn line ix is-eol section]
  {:value tkn :line-no line :idx ix :is-eol is-eol :section section})

(defn parse-token-section [t]
  (re-find #"(\[(\w+)\])?(\S+)?" t))

(defn parse-line [line line-no sec]
  (loop [[[t1 t2] & ts] (partition-all 2 line) ix 0 section sec acc []]
    (let [[_ _ s r] (parse-token-section t1)
          eol (nil? ts)
          new-sec (or s sec)]
      (match [r t2]
        [nil nil] [new-sec acc]
        [nil t] (let [new-acc (conj acc (make-token t line-no ix eol new-sec))]
                  (if eol [new-sec new-acc]
                          (recur ts (inc ix) new-sec new-acc)))
        [t nil] [new-sec (conj acc (make-token t line-no ix true new-sec))]
        [ta tb] (recur ts (+ ix 2) new-sec (conj acc (make-token ta line-no ix false new-sec)
                                                     (make-token tb line-no (inc ix)
                                                                 eol new-sec)))))))

(defn parse-tokens [lines sec]
  (loop [[line & more-lines] lines section sec line-no 0 acc []]
    (let [[parsed-sec parsed-line] (parse-line line line-no section)
          new-line-no (if (empty? parsed-line) line-no (inc line-no))
          new-acc (into acc parsed-line)
          new-sec (or parsed-sec section)]
      (if (some? more-lines)
        (recur more-lines new-sec new-line-no new-acc)
        new-acc))))


(defn parse-lyrics [{:keys [content section]}]
  (let [splt (split-lyrics content)]
    (parse-tokens splt section)))
