(ns lyrics.scraping
    (:require [net.cgrand.enlive-html :as enlive]
              [clj-http.client :as client]
              [clojure.string :as string]
              [clojure.tools.reader.edn :as edn])
    (:import (java.lang.String)))


(defn from-edn
  "Read an edn file
   :param edn-file: path to edn file to read
   :type edn-file: string (filepath)
   :returns: data"
  [edn-file] (with-open [r (clojure.java.io/reader edn-file)]
                            (edn/read (java.io.PushbackReader. r))))

(def url-root
  "The root url"
  "http://www.metrolyrics.com")

(defn get-resource
  "Safely try to access a website and return its
   body as an enlive snippet
   :param url-string: the url to access
   :type url-string: string (url)
   :returns: enlive snippet or nil"
  ([url-string]
    (try
      (-> (client/get url-string) :body enlive/html-snippet)
      (catch Exception e nil)))
  ([]
   (map get-resource)))

(defn- valid-link?
  "Validate a url by ensuring there are no errant '//'s in it
   :param link: link to validate
   :type link: String
   :returns: boolean"
  [^String link]
  (not (re-find #"(?<!http:)//" link)))

(defn- rstrip
  "Strip a character from the end of a string
   :param s: the string to strip
   :type s: String
   :param c: the character to strip off
   :type c: string
   :return: String"
  [^String s c]
  (if (.endsWith s c) (string/join (butlast s))
    s))

(defn get-link
  "Retrieve a link from a node
   :param node: enlive node
   :type node: enlive node (string or map, see enlive_html.clj 316-317)
   :returns: string"
  [node]
  (get-in node [:attrs :href]))

(defn get-links-from-res
  "Extract all links from a snippet of a resource
   :param resource: the resource to extract from
   :type resource: Ring response map
   :param selector: the selector to limit the extraction to
   :type selector: Enlive selector
   :returns: seq of strings"
  [res selector]
  (map get-link (enlive/select res selector)))

(defn get-links
  "Extract all links from a snippet of a resource retrieved from a url
   :param url: the url to access
   :type url: string (url)
   :param selector: the enlive selector to limit the link extraction to
   :type selector: enlive selector
   :returns: seq of strings"
  [url selector]
  (get-links-from-res (get-resource url) selector))

(def letter-links
  "Starting links"
  (map get-link (enlive/select (get-resource
                                 (str url-root
                                      "/top-artists.html"))
                                 [:p.artist-letters :a])))

(defn get-artists
  "Retrieve all artist links from a resource
   :param res: the resource to extract from
   :type res: Ring response map
   :returns: seq of strings"
  [resp]
  (distinct (map get-link (enlive/select resp [:td :a]))))

(defn take-until-nils
  [max-nils]
    (fn [xf]
      (let [nils-count (atom 0)]
        (fn
          ([] (xf))
          ([res] (xf res))
          ([res input]
           (if (nil? input)
             (if (compare-and-set! nils-count (dec max-nils) 0)
               (reduced res)
               (swap! nils-count inc))
             (xf res input)))))))

(defn filter-redirected
  "Takes a url string and returns it if it doesn't redirect
   :param url: the address of the resource to retrieve
   :type url: String
   :returns: String or nil"
  ([url]
    (if-let [resp (and (seq url) (client/get url))]
      (if (= 1 (-> resp :trace-redirects distinct count))
        url)))
  ([]
   (comp (take-while some?) (map filter-redirected))))

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
   :returns: string"
  ([starting-link idx]
    {:pre [(integer? idx)]}
    (if (zero? idx) starting-link
                  (string/replace starting-link #"\.html$"
                                                (str "-" idx ".html"))))
  ([starting-link]
     (sequence (comp (map (partial iterate-link starting-link))
                     (filter-redirected)
                     (take-until-nils 2))
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
                    (map filter-redirected)
                    (take-while some?)
                    (map get-resource)
                    (mapcat get-artists)
                    (map fix-artist-link))
              (range 1000)))
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

(defn strify
  "Join a sequence of strings with spaces
   :param ss: the sequence of strings to join
   :type ss: seq
   :returns: string"
  [ss]
  (string/join " " ss))

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

(defn extract-lyrics
  "Extract the lyrics from a resource represented by a lyrics url
   :param lyrics-url: the url of the resource to extract lyrics from
   :type lyrics-url: string
   :returns: map"
  ([lyrics-map]
    (let [lp (get-resource (:url lyrics-map))]
      (merge lyrics-map
            {:load (-> lp
                        (enlive/select [:div.load])
                        first
                        :content
                        first)
              :featuring (mapv enlive/text (-> lp
                                              (enlive/select [:span.fartist])))
              :lyrics (apply str (map enlive/text (-> lp
                                                      (enlive/select
                                                      [:div#lyrics-body-text])
                                                      first
                                                      :content)))})))
  ([]
   (map extract-lyrics)))

(def get-lyrics
  (comp (collect-artists)
        (iterate-link)
        (get-resource)
        (mapcat #(get-links-from-res % [:ul.grid_3 :li :a]))
        (parse-lyrics-url)
        (extract-lyrics)
        ))

(defn get-all-lyrics
  "Extract all lyrics from the provided artists
   :param artists: seq of artist links to process
   :type artists: seq of strings
   :returns: seq of maps"
  ([letters]
    (sequence get-lyrics letters))
  ([]
   (get-all-lyrics ["1" "a" "b" "c" "d" "e" "f"
                    "g" "h" "i" "j" "k" "l" "m"
                    "n" "o" "p" "q" "r" "s" "t"
                    "u" "v" "w" "x" "y" "z"])))
