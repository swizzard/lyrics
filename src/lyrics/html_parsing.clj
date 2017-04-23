(ns lyrics.html-parsing
  (:require [net.cgrand.enlive-html :refer [select text]]
            [clojure.string :as string] [lyrics.utils :refer [strify]]))

(defn get-link
  "Retrieve a link from a node
   :param node: enlive node
   :type node: enlive node (string or map, see enlive_html.clj 316-317)
   :returns: string"
  [node]
  (get-in node [:attrs :href]))

(defn get-links-from-res
  [snippet selector]
  (map get-link (select snippet selector)))

(def extract-links
  "xf to extract links from res"
  (mapcat #(get-links-from-res % [:ul.grid_3 :li :a])))

(defn get-artists
  "Retrieve all artist links from a resource
   :param res: the resource to extract from
   :type res: Ring response map
   :returns: seq of strings"
  [resp]
  (distinct (map get-link (select resp [:td :a]))))

(defn parse-lyrics-url
  "Parse a lyrics url
   :param lyrics-url: the url to parse
   :returns: map"
  ([^String lyrics-url]
    (let [splt (split-with #(not= % "lyrics")
                          (-> lyrics-url
                              (string/replace ".html" "")
                              (string/split #"/")
                              last
                              (string/split #"-")))]
      {:url lyrics-url
      :url-song (strify (first splt))
      :url-artist (strify (next (second splt)))}))
  ([]
   (map parse-lyrics-url)))

(defn extract-txt [snippet sels]
  (as-> snippet %
        (select % sels)
        (mapv text %)))

(def ^:private root-sel [:section#lyrics-main])
(def ^:private header-sel (into root-sel [:header]))
(def ^:private lyrics-sel (into root-sel [:div.lyrics :div#lyrics-body-text :p]))

(defn- filter-artist-meta [ms]
  (filter #(= "name" (get-in % [:attrs :itemprop])) ms))

(defn extract-artist [snippet]
  {:artist (-> snippet (select [:meta])
                       filter-artist-meta
                       first
                       (get-in [:attrs :content]))})

(defn extract-load [snippet]
    {:featuring (extract-txt snippet (into header-sel [:p.featuring :span.fartist]))
     :album (-> snippet (select (into header-sel [:a#album-name-link])) first :content first)})

(defn- get-lyric [snippet]
  {:section (get-in snippet [:attrs :class])
   :content (text snippet)})

(defn extract-lyrics [snippet]
  (let [s (select snippet lyrics-sel)]
    {:lyrics (map get-lyric s)}))

(defn parse-lyrics
  ([res]
    (merge (extract-lyrics res)
           (extract-load res)
           (extract-artist res))))

