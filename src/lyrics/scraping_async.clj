(ns lyrics.scraping-async
  (:require [clojure.core.async :as async]
            [clojure.core.match :refer [match]]
            [clojure.string :as string]
            [clojure.tools.reader.edn :as edn]
            [com.stuartsierra.component :as component]
            (hickory [core :refer [parse as-hickory]]
                     [select :as s])
            [lyrics.macros :refer [-*>]]
            [org.httpkit.client :as http])
  (:import (java.lang.String)))

(defn- hickorize [body]
  (-> body parse as-hickory))

(def url-root
  "The root url"
  "http://www.metrolyrics.com")

(def artists-root
  "Root url for artists"
  (str url-root "/top-artists.html"))


(defn- subs- [s x]
  (subs s (- (count s) x)))

(defn iterate-link
  "Alters a link by attaching an index to the end of it
   :param starting-link: the link to modify
   :type starting-link: string
   :param idx: the index to attach to the link
   :type idx: integer
   :returns: string"
  [starting-link idx]
  {:pre [(> 1 idx)]}
  (format "%s-%d.html" (subs starting-link 0 (- (count starting-link) 5)) idx))

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
             (extract-links body))
        (recur (iterate-link link idx)
               (inc idx))))))

(defn get-artist-links [letter-links-chan output-chan]
  (go-loop [letter-link (async/<! letter-links-chan)]
           (get-artist-letter output-chan letter-link)))

(defrecord ArtistLinks [start-url output-chan running?]
  component/Lifecycle
  (start [component]
    (println "starting ArtistLinks")
    (swap! running? not)
    (let [letter-links (get-letter-links start-url)]
      (get-artist-links letter-links output-chan))
    component))

  (stop [component]
    (println "stopping ArtistLinks")
    (-> component
        (update :output-chan close!)
        (update :running? swap! not)))

(defn make-artist-links
  ([start-url output-chan]
    (->ArtistLinks start-url output-chan (atom false)))
  ([output-chan]
   (make-artist-links artists-root output-chan (atom false))))

(defrecord AlbumNode [title year genre artist])
(defrecord LyricsResponse [album year url res])

(defn- meta->map [{{:keys [content itemprop]} :attrs}]
  {(keyword itemprop) content})

(defn- get-meta-map [meta-attrs]
  (transduce (map meta->map) reduce {} meta-attrs))

(defn parse-album [output-chan album-node]
  (async/put! output-chan
              (-> (s/select (s/tag :meta) album-node)

              (select-keys
                (map (fn [{{:keys [content itemprop]} :attrs}]
                      {(keyword itemprop) content})
                    (s/select (s/tag :meta) album-node))
                [:copyrightYear :byArtist :genre])))

(defn- select-album-nodes [body]
  (s/select (s/class "album-track-list") (hickorize body)))

(defn get-album-nodes [start-page output-chan running?]
  (when @running?
    (loop [page start-page
           idx 2
           is-running? @running]
      (let [{{url :url} :opts body :body} @(http/get page)]
        (when (= url page)
          (doseq [album-node (select-album-nodes body)]
            (async/put! output-chan album-node))
          (recur (iterate-link artist-link idx)
                  (inc idx)))))))

(defrecord AlbumNodeExtractor [pages-chan output-chan running?]
  component/Lifecycle
  (start [component]
    (println "starting AlbumNodeExtractor")
    (swap! running? not)
    (go-loop [page (async/<! pages-chan)]
      (when (some? page)
        (get-album-nodes page output-chan running?)))
    component)

  (stop [component]
    (println "stopping AlbumNodeExtractor")
    (-> component
        (update :output-chan async/close!)
        (update :running? swap! not)))

(defn make-album-node-extractor [pages-chan output-chan]
  (->AlbumNodeExtractor pages-chan output-chan (atom false)))


(defrecord SongLinks [artist-links-chan output-chan num-workers state]
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
    (go-loop [{:keys [res] :as lyrics-resource} (<! pages-chan)]
        (when lyrics-resource
          (if (realized? res)
              (>! output-chan (extract-raw-lyrics lyrics-resource))
            (>! pages-chan raw-lyrics))
          (recur (<! pages-chan)))))

  (stop [component]
    (println "stopping LyricsExtractor")
    (close! output-chan)
    (assoc component :state :stopped)))

(defn make-lyrics-extractor [{pages-chan :output-chan} output-chan]
  (LyricsExtractor. pages-chan output-chan (atom :stopped)))

