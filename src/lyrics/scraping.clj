(ns lyrics.scraping
    (:require [net.cgrand.enlive-html :refer [html-snippet]]
              [clj-http.client :as client]
              [clojure.string :as string]
              [lyrics.html-parsing :as hp]
              [lyrics.redis :refer [cached? clear-cache]]
              [lyrics.utils :refer [from-edn filter-contains]])
    (:import (java.lang.String)))

(defn- get-non-redirect
  [url-string]
  (try
    (let [resp (client/get url-string {:follow-redirects false})]
      (if (= 200 (:status resp))
       (do (println url-string)
           resp)))
    (catch Exception e nil)))

(defn get-resource
  "Safely try to access a website and return its
   body as an enlive snippet
   :param url-string: the url to access
   :type url-string: string (url)
   :returns: enlive snippet or nil"
  ([url-string]
    (if-let [res (get-non-redirect url-string)]
      (-> res :body html-snippet))))

(defn- valid-link?
  "Validate a url by ensuring there are no errant '//'s in it
   :param link: link to validate
   :type link: String
   :returns: boolean"
  [^String link]
  (not (re-find #"(?<!http:)//" link)))

(defn assemble-link
  "Assemble a link from a letter and an optional index
   :param letter: a letter
   :type letter: string
   :param idx: an integer
   :type idx: integer
   :returns: string"
  ([letter idx]
    {:pre [(integer? idx)]}
    (let [root "http://www.metrolyrics.com/artists-"]
      (if (zero? idx) (str root letter ".html")
                      (str root letter "-" idx ".html"))))
  ([letter] (assemble-link letter 0)))

(defn iterate-link
  "Alters a link by attaching an index to the end of it
   :param starting-link: the link to modify
   :type starting-link: string
   :param idx: the index to attach to the link
   :type idx: integer
   :returns: enlive snippet"
  ([starting-link idx]
    {:pre [(integer? idx)]}
    (if (zero? idx) starting-link
                    (string/replace starting-link
                                    #"\.html$"
                                    (str "-" idx ".html"))))
  ([starting-link]
     (sequence (comp (map (partial iterate-link starting-link))
                     (map get-resource)
                     (take-while some?))
               (range)))
  ([]
   (mapcat iterate-link)))

(defn fix-artist-link
  "Fix an artist link to point to the albums list
   :param artist-link: the link to modify
   :type artist-link: String
   :returns: String"
  [^String artist-link]
  (string/replace artist-link "-lyrics" "-albums-list"))

(defn collect-artists
  "Retrieve all the artists starting with the given letter
   :param letter: the letter to retrieve artists beginning with
   :type letter: string
   :returns: seq of strings"
  ([letter]
    (sequence (comp (map (partial assemble-link letter))
                    (map get-resource)
                    (take-while some?)
                    (mapcat hp/get-artists)
                    (map fix-artist-link))
              (range)))
  ([]
   (mapcat collect-artists)))

(defn get-all-artists
  "Get ALL the artists! NB: function because it takes a long time
   :returns: seq of maps"
  []
  (sequence (mapcat collect-artists)
            ["1" "a" "b" "c" "d" "e" "f"
             "g" "h" "i" "j" "k" "l" "m"
             "n" "o" "p" "q" "r" "s" "t"
             "u" "v" "w" "x" "y" "z"]))

(defn get-some-artists
  "Get some artists. NB: mostly for debug purposes
   :returns: seq of maps"
  []
  (collect-artists "1"))


; (def filter-processed
;   "xf to filter out locally-processed links"
;   (filter-contains (from-edn "resources/processed.edn")))

(def get-lyrics
  "Main xf"
  (comp (collect-artists)
        (iterate-link)
        hp/extract-links
        ; filter-processed
        (remove cached?)
        (map get-resource)
        (filter some?)
        (map hp/parse-lyrics)
        (take 1)))

(defn get-all-lyrics
  "Extract all lyrics from the provided artists
   :param artists: seq of artist links to process
   :type artists: seq of strings
   :returns: seq of maps"
  ([letters]
    (do (clear-cache)
        (into [] get-lyrics letters)))
  ([]
   (get-all-lyrics ["1" "a" "b" "c" "d" "e" "f"
                    "g" "h" "i" "j" "k" "l" "m"
                    "n" "o" "p" "q" "r" "s" "t"
                    "u" "v" "w" "x" "y" "z"])))

