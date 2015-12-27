(ns lyrics.parsing
  (:require [clojure.string :refer [capitalize split split-lines lower-case]]
            [clojure.core.async :refer [<! <!! >! go thread] :as async]
            [com.stuartsierra.component :as component]))


(defrecord ParsedLyrics [url title track-number
                         album written-by parsed-lyrics])


(def url-pat
  "Pattern to parse artist"
 #"http://www\.metrolyrics\.com/([\w\d-]+?)(?:-)lyrics-([\w\d-]+)\.html")

(defn parse-url
  "Extract the artist from a url"
  [url]
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

(defn make-parsed-lyrics
  "Make a ParsedLyrics record"
  [{:keys [url raw-load raw-lyrics]}]
  (map->ParsedLyrics
    (assoc (parse-load raw-load) :url url
                                 :lyrics raw-lyrics
                                 :artist (parse-url url))))

(defrecord LyricsParser [raw-lyrics-chan output-chan pipeline-size running?]
  component/Lifecycle
  (start [component]
    (println "starting LyricsParser")
    (swap! running? not)
    (async/pipeline pipeline-size
                    output-chan
                    (comp (map make-parsed-lyrics) (filter some?))
                    raw-lyrics-chan)
    component)

  (stop [component]
    (println "stopping LyricsParser")
    (-> component
        (update :running? swap! not)
        (update :output-chan async/close!))))

(defn new-lyrics-parser
  ([raw-lyrics-chan output-chan pipeline-size]
    (map->LyricsParser {:raw-lyrics-chan raw-lyrics-chan
                        :output-chan output-chan
                        :pipeline-size pipeline-size
                        :running? (atom false)}))
  ([raw-lyrics-chan output-chan]
   (new-lyrics-parser raw-lyrics-chan output-chan 10)))

