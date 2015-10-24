(ns lyrics.parsing
  (:require [clojure.string :refer [capitalize split split-lines lower-case]]
            [clojure.core.async :refer [<! <!! >! go thread] :as async]
            [com.stuartsierra.component :as component]
            [lyrics.scraping-async :refer [RawLyrics]]))


(defrecord ParsedLyrics [url title track-number
                         album written-by parsed-lyrics])


(def url-pat
  "Pattern to parse artist"
 #"http://www\.metrolyrics\.com/([\w\d-]+?)(?:-)lyrics-([\w\d-]+)\.html")

(defn parse-url [url]
  (let [[_ _ raw-artist] (re-find url-pat url)]
    (-> raw-artist
        (split #"-")
        capitalize)))

(def load-pat
  "The pattern to parse the load"
  (re-pattern (str "\n?\"([^\"]*)\" is track #(\\d+) on the album "
                   "(.+?)\\. It was written by (.+?)\\.\t?")))

(defn parse-load
  "Parse the load section of a blob
   :param load-line: the line to parse
   :type load-line: String
   :returns: map"
  [^String load-line]
  (if-let [parsed-load (re-matches load-pat load-line)]
    ;; re-matches returns vec, 0th elem of which is full match
    (zipmap [:title :track-number :album :written-by]
            (map str (next parsed-load)))))

(def header-line-pat
  "A pattern to parse a header-line"
  #"(Album|Artist|Title): .*\n")

(defn parse-line
  "Parse a line of the lyrics section of a blob
   :param lyrics-line: the line to parse
   :type lyrics-line: String
   :returns: map"
  [^String lyrics-line]
  (if (nil? (re-find header-line-pat lyrics-line))
            (into {}
                  (keep-indexed (fn [idx w] [idx w])
                                (map lower-case (split lyrics-line #"\s"))))))

(defn parse-lyrics
  "Parse the lyrics component of a blob
  :param lyrics: the lyrics to parse
  :type lyrics: String
  :returns: map"
  [^String lyrics]
  (into {} (keep-indexed (fn [idx l] (if-let [parsed (parse-line l)]
                                       [idx parsed]))
                         (split-lines lyrics))))

(defn parse-lyrics [^RawLyrics {:keys [url raw-load raw-lyrics]}]
  (map->ParsedLyrics
    (assoc (parse-load raw-load) :url url
                                 :parsed-lyrics (parse-lyrics raw-lyrics)
                                 :artist (parse-url url))))

(defrecord LyricsParser [raw-lyrics-chan output-chan pipeline-size state]
  component/Lifecycle
  (start [component]
    (println "starting LyricsParser")
    (reset! state :running)
    (async/pipeline pipeline-size
                    output-chan
                    (comp (map parse-lyrics) (filter some?))
                    raw-lyrics-chan)
    component)

  (stop [component]
    (println "stopping LyricsParser")
    (reset! state :stopped)
    (close! output-chan)
    component))


