(ns lyrics.scraping-async.song-parser
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [hickory.select :as s]
            [lyrics.scraping-async.utils :refer [hickorize]]))


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
                    (first %)
                    (:content %)
                    (first %)
                    (prune-str % #" Lyrics$"))]
  (assoc out :title title)))

(defn get-featuring [out song]
  (let [fartists (s/select (s/descendant (s/class :featuring)
                                         (s/class :fartist)) song)]
    (assoc out :featuring (map (comp :content first) fartists))))

(defn get-album-name [out song]
  (let [album-name (s/select (s/descendant (s/class "album-name")
                                           (s/id "album-name-link"))
                             song)]
    (assoc out :album-title (-> album-name first :content first))))

(defn- sel-lyrics [song] (s/select (s/descendant (s/id "lyrics-body")
                                   (s/tag :p)
                                   (s/not (s/tag :br)))
                         song))

(defrecord Song [copyright-year artist genre album-title
                 featuring title lyrics raw])

(defn get-lyrics
  ([out song parser-fn]
    (let [raw (sel-lyrics song)]
      (assoc out :lyrics (map->Lyrics {:raw raw :parsed (parser-fn raw)})))))

(defn parse-song [song parser-fn]
  (let [hs (hickorize song)
        song-map (-> {:raw hs}
                 (get-title hs)
                 (get-featuring hs)
                 (get-album-name hs)
                 (get-lyrics hs parser-fn))]
    (map->Song song-map)))

(defn parse-songs [{songs-chan :songs-chan copyright-year :copyrightYear
                    artist :byArtist genre :genre}
                   output-chan running? parser-fn]
  (async/go-loop []
    (when @running?
      (when-let [song (async/<!! songs-chan)]
         (async/put! output-chan (merge (parse-song song parser-fn)
                                        {:copyright-year copyright-year
                                         :genre genre :artist artist}))
         (recur)))))

(defrecord SongParser [albums-chan output-chan running? parser-fn]
  component/Lifecycle
  (start [component]
    (when-not @running?
      (println "starting SongParser")
      (swap! running? not)
      (async/go-loop []
        (when-let [parsed-album (async/<!! albums-chan)]
            (parse-songs parsed-album output-chan running? parser-fn)
            (recur))))
    component)
  (stop [component]
    (when @running?
      (println "stopping SongParser")
      (-> component
         (update :running? swap! not) 
         (update :output-chan async/close!)))))

(defn new-song-parser
  [albums-chan output-chan & {parser-fn :parser-fn :or {parser-fn identity}}]
   (map->SongParser {:albums-chan albums-chan :output-chan output-chan
                     :parser-fn parser-fn :running? (atom false)}))

