(ns lyrics.parsing
  (:require [instaparse.core :as insta]
            [clojure.string :refer [split-lines triml]]))

(def annotation-markers "<\\[\\*")
(def header-parser (insta/parser 
                        "<HeaderLine> = Header Value
                         <Header> = Album | Artist | Song | TypedBy
                         Album = <#'^[Aa]lbum:?\\s+'>
                         Artist = <#'^[Aa]rtist:?\\s+'>
                         Song = <#'^([Ss]ong|[Tt]itle):?\\s+'>
                         TypedBy = <#'^[Tt]yped\\s+[Bb]y:?\\s+'>
                         <Value> = #'[^\\n]+$'"))
(def body-parser (insta/parser
                        (str
                          "<BodyLine> = Lyric | Annotation
                           <Lyric> = (Word | Punc)+
                           Word = #'[a-zA-Z0-9\\.\\'\\-]+'
                           Punc = #'[,\\\"\\'\\.\\?\\!]+?'
                           Annotation = #'[" annotation-markers "][^\\n]+'")
                   :auto-whitespace :standard))
                         
(defn process-word [song line-idx wd-idx word]
  (merge (apply hash-map word)
         {:song song
          :line-idx line-idx
          :wd-idx wd-idx}))

(def header-hierarchy (-> (make-hierarchy)
                          (derive :Album ::Header)
                          (derive :Artist ::Header)
                          (derive :Song ::Header)
                          (derive :TypedBy ::Header)
                          ;; annotation isn't really a "Header,"
                          ;; but it gets treated like one
                          (derive :Annotation ::Header)
                          (derive :Word ::Lyric)
                          (derive :Punc ::Lyric)))

(defmulti process-line ffirst)
(defmethod process-line ::Header [line]
  (apply hash-map (flatten line)))
(defmethod process-line ::Lyric [line]
  (map (partial apply hash-map) line))

(defn parse-line [line parser & [m]]
  (if (:parses m)
    (insta/parses parser (triml line))
    (insta/parse parser (triml line))))

(comment
(defn process-lines [lines]
  (loop [line (first lines)
         tail (next lines)
         headers {}
         words []]
    (if (nil? line) words
      (
      (recur (first tail)
             (next tail))))))
  )
