(ns lyrics.scraping-async
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [clojure.tools.reader.edn :as edn]
            [com.stuartsierra.component :as component]
            (hickory [core :refer [parse as-hickory]]
                     [select :as s])
            [lyrics.macros :refer [-*>]]
            [org.httpkit.client :as http])
  (:import (java.lang.String)))

(defn- is-running [state]
  (= :running @state))

(defn- hickorize [body]
  (-> body parse as-hickory))

(def url-root
  "The root url"
  "http://www.metrolyrics.com")

(def artists-root
  "Root url for artists"
  (str url-root "/top-artists.html"))

(defn iterate-link
  "Alters a link by attaching an index to the end of it
   :param starting-link: the link to modify
   :type starting-link: string
   :param idx: the index to attach to the link
   :type idx: integer
   :returns: string"
  [^String starting-link ^Integer idx]
  (if (zero? idx) starting-link
                (string/replace starting-link #"\.html$"
                                              (str "-" idx ".html"))))

(defn get-letter-links [url]
  (let [selector (s/descendant (s/and (s/tag :p)
                                      (s/class "artist-letters"))
                               (s/tag :a))
        out-chan (async/chan)]
    (->> @(http/get url) :body
                         hickorize
                         (s/select selector)
                         (map (fn [n] (async/put! out-chan
                                           (get-in n [:attrs :href])))))
    out-chan))

(defn- format-link [raw-link]
  (-> raw-link
      (get-in [:attrs :href])
      (string/replace "-lyrics" "-albums-list")))

(defn- extract-links [raw-body]
  (-> raw-body
      hickorize
      (s/descendant (s/tag :td) (s/tag :a))))

(defn get-artist-letter [output-chan starting-link]
  (loop [page starting-link
         idx 2]
    (let [{{url :url} :opts body :body} @(http/get page)]
      (when (= url page)
        (map (fn [link] (async/put! output-chan (format-link link)))
             extract-links body)
        (recur (iterate-link page idx)
               (inc idx))))))

(defn get-artist-links [letter-links-chan output-chan]
  (async/go-loop [letter-link (async/<! letter-links-chan)]
           (get-artist-letter output-chan letter-link)))

(defrecord ArtistLinks [start-url output-chan state]
  component/Lifecycle
  (start [component]
    (println "starting ArtistLinks")
    (reset! state :running)
    (let [letter-links (get-letter-links start-url)]
      (get-artist-links letter-links output-chan))
    component)

  (stop [component]
    (println "stopping ArtistLinks")
    (reset! state :stopped)
    (async/close! output-chan)
    component))

(defn make-artist-links
  ([start-url output-chan]
    (->ArtistLinks start-url output-chan (atom :stopped)))
  ([output-chan]
   (make-artist-links artists-root output-chan)))

(defrecord AlbumNode [title year genre artist title url])
(defrecord LyricsResponse [album year url res])

(defn parse-album [album-node]
  (let [defaults {:copyrightYear nil :genre nil :byArtist nil
                  :title nil :url nil}]
    (reduce merge
           (map (fn [{{:keys [content itemprop]} :attrs}]
                  {(keyword itemprop) content})
                (s/select (s/tag :meta) album-node))
            defaults)))

(defn get-raw-albums [start-page output-chan]
  (loop [page start-page idx 2]
    (let [{{url :url} :opts body :body} @(http/get page)]
      (when (= url page)
        (map #(async/>! output-chan %)
             (->> body hickorize (s/select (s/class "album-track-list"))))
        (recur (iterate-link artist-link idx)
               (inc idx))))))

(defn get-albums [{:keys [artist-links-chan output-chan state]}]
  (loop [page (async/<! artist-links-chan)]
    (when (some? page)
      (async/go (get-raw-albums output-chan))
      (recur (async/<! artist-links-chan)))))

(defrecord AlbumExtractor [artist-links-chan output-chan state]
  component/Lifecycle
  (start [component]
    (println "starting AlbumExtractor")
    (reset! state :running)
    (go (get-albums component))
    component)

  (stop [component]
    (println "stopping AlbumExtractor")
    (reset! state :stopped)
    (async/close! output-chan)
    component))

(defn make-album-extractor [artist-links-chan output-chan]
  (->AlbumExtractor artist-links-chan output-chan (atom :stopped)))

(defrecord Album [title year genre artist song-links])

(defmulti make-album #(-> % :content second :attrs :class some?))

(defmethod make-album true [album]
  (let [songs (s/select (s/child (s/tag :li) s/first-child) album)
        artist (-> (s/select (s/child (s/class "grid_2")
                                       s/first-child)
                             album)
                   first
                   (get-in [:attrs :alt])
                   (string/replace #" Singles$" ""))]
    (->Album "Singles" nil nil artist songs)))

(defn- get-meta [album]
  (let [album-meta (s/select (s/tag :meta) album)]
    (reduce merge (map (fn [{attrs :attrs}] {(keyword (:itemprop attrs))
                                             (:content attrs)})
                       album-meta))))

(defmethod make-album false [album]
  (let [{:keys [byArtist copyrightYear genre]} (get-meta album)
        song-links (map #(get-in % [:attrs :href])
                        (s/select (s/child (s/tag :li)
                                           (s/tag :a))
                                  album))
        title (->> album (s/select (s/child (s/tag :h3) (s/tag :span)))
                         first :content first)]
    (->Album title copyrightYear genre byArtist song-links)))

(defn get-albums [{:keys [raw-albums-chan output-chan]}]
  (go-loop [raw-album (<! raw-albums-chan)]
           (when (some? raw-album)
             (>! output-chan (make-album raw-album))
             (recur (<! raw-albums-chan)))))

(defrecord AlbumParser [raw-albums-chan output-chan state]
  component/Lifecycle
  (start [component]
    (println "starting AlbumParser")
    (reset! state :running)
    (get-albums component)
    component)
  (stop [component]
    (println "stopping AlbumParser")
    (reset! state :stopped)
    (close! output-chan)
    component))

(defn make-album-parser [raw-albums-chan output-chan]
  (->AlbumParser raw-albums-chan output-chan (atom :stopped)))

(defrecord RetrievedAlbum [title year genre artist songs-chan])

(defn get-songs [{song-links :song-links} output-chan]
  k

(defrecord SongLinks [album-chan output-chan state]
  component/Lifecycle
  (start [component]
    (println "starting SongLinks")
    (reset! (:state component) :running)
    (dotimes [_ num-workers]
      (async/thread (get-song-links component)))
    component)

  (stop [component]
    (println "stopping SongLinks")
    (reset! (:state component) :stopped)
    (close! output-chan)
    component))

(defn make-song-links
  [{links-chan :output-chan} output-chan num-workers]
  (SongLinks. links-chan output-chan num-workers (atom :stopped)))

(defn- split-n [string] (if (= (first string) \newline)
                          ["\n" (subs string 1)]
                          [string]))

(defn- lyrics-xf (comp (mapcat :content) (filter string?)
                       (mapcat #(string/split % #" "))
                       (mapcat split-n)))

(defn- extract-lyrics [body]
  (cons nil (sequence lyrics-xf (s/select (s/class "verse") body))))

(defn- extract-load [body]
  (->> body (s/select (s/class "load")) first :content first))

(defrecord RawLyrics [url res raw-load raw-lyrics])

(defn extract-raw-lyrics [{:keys [url res]}]
  (let [body (-> res :body hickorize)]
      (RawLyrics. url res (extract-load body) (extract-lyrics body))))

(defrecord LyricsExtractor [pages-chan output-chan state]
  component/Lifecycle
  (start [component]
    (println "starting LyricsExtractor")
    (reset! state :running)
    (async/go-loop [{:keys [res] :as lyrics-resource} (async/<! pages-chan)]
        (when lyrics-resource
          (if (realized? res)
              (async/>! output-chan (extract-raw-lyrics lyrics-resource))
            (async/>! pages-chan raw-lyrics))
          (recur (async/<! pages-chan)))))

  (stop [component]
    (println "stopping LyricsExtractor")
    (async/close! output-chan)
    (assoc component :state :stopped)))

(defn make-lyrics-extractor [{pages-chan :output-chan} output-chan]
  (LyricsExtractor. pages-chan output-chan (atom :stopped)))

