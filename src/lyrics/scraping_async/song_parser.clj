(ns lyrics.scraping-async.song-parser
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [hickory.select :as s]
            [lyrics.scraping-async.utils :refer [coll->chan
                                                 hickorize]]))


(defrecord Lyrics [raw parsed])

(def song-cb-selector
  "Selector for song callback"
  (s/descendant (s/class :lyrics)
                (s/tag :p)))

(def lyrics-selector
  "Selector for lyrics"
  (s/descendant (s/class :lyrics-body)
                (s/tag :p)))

(def load-selector
  "Selector for load"
  (s/descendant (s/class :intro)
                (s/class :load)))

(defn prune-str [s pat]
  (if (some? s) 
    (string/replace (string/trim s) pat "")
    ""))

(defn get-title [out song]
  (let [title (as-> song %
                    (s/select (s/tag :h1) %)
                    (:content %)
                    (first %)
                    (prune-str % #" Lyrics$"))]
  (assoc-in out :title title)))

(defn get-featuring [out song]
  (let [fartists (s/select (s/descendant (s/class :featuring)
                                         (s/class :fartist)))]
    (assoc out :featuring (map (comp first :content) fartists))))

(defn get-album-name [out song]
  (let [album-name (s/select (s/descendant (s/class "album-name")
                                           (s/id "album-name-link"))
                             song)]
    (assoc out :album-name (-> album-name :content first))))

(defn- sel-lyrics [song] (s/select (s/descendant (s/id "lyrics-body")
                                   (s/tag :p)
                                   (s/not (s/tag :br)))
                         song))

(defrecord Song [copyright-year artist genre album-name 
                 album-title featuring title lyrics])

(defn get-lyrics
  ([out song parser-fn]
    (let [raw (sel-lyrics song)]
      (map->Lyrics {:raw raw :parsed (parser-fn raw)}))))

(defn parse-song [song]
  (let [song (-> {}
                 (get-title song)
                 (get-featuring song)
                 (get-album-name song)
                 (get-lyrics song))]
    (println song)
    (map->Song song)))

(defn parse-songs [{songs-chan :songs-chan copyright-year :copyrightYear
                    artist :byArtist genre :genre}
                   output-chan running? parser-fn]
  (async/go-loop []
    (when @running?
      (if-let [song (async/<!! songs-chan)]
       (do
         (async/put! output-chan (merge (parse-song song)
                                        {:copyright-year copyright-year
                                         :genre genre :artist artist}))
         (recur))
       (async/close! output-chan)))))

(defrecord SongParser [albums-chan output-chan running? parser-fn]
  component/Lifecycle
  (start [component]
    (when-not @running?
      (println "starting SongParser")
      (swap! running? not)
      (async/go-loop []
        (if-let [parsed-album (async/<!! albums-chan)]
          (do
            (parse-songs parsed-album output-chan running? parser-fn)
            (recur))
          (async/close! output-chan))))
    component)
  (stop [component]
    (when @running?
      (println "stopping SongParser")
      (-> component
         (update :running swap! not) 
         (update :output-chan async/close!)))))

(defn new-song-parser
  [albums-chan output-chan & {parser-fn :parser-fn :or {parser-fn identity}}]
   (map->SongParser {:albums-chan albums-chan :output-chan output-chan
                     :parser-fn parser-fn :running? (atom false)}))

