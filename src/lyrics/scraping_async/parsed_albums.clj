(ns lyrics.scraping-async.parsed-albums
  (:require [clojure.core.async :as async]
            [clojure.string :refer [trim]]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [hickory.select :as s]
            [lyrics.scraping-async.utils :refer [hickorize]]))


(defrecord ParsedAlbum [copyrightYear byArtist genre title songs-chan])

(defn make-parsed-album
  "Create a ParsedAlbum"
  [album-title
   {:keys [copyrightYear byArtist genre numTracks]
                          :or {numTracks 100}}
   songs]
  (let [num-tracks (Integer. numTracks)
        songs-chan (async/chan num-tracks)]
    (async/go-loop [s songs]
      (if-let [song (first songs)]
        (do
          (as-> song %
                (get-in % [:attrs :href])
                (http/get %)
                (deref %)
                (:body %)
                (async/put! songs-chan %))
          (recur (rest songs)))
        (async/close! songs-chan)))
    (->ParsedAlbum copyrightYear byArtist genre album-title songs-chan)))

(defn get-meta
  "Create a map from meta tags"
  [album-node]
  (let [meta-attrs (s/select (s/tag :meta) album-node)]
    (transduce (map (fn [{{:keys [content itemprop]} :attrs}]
                      {(keyword itemprop) content}))
               merge {} meta-attrs)))

(def song-selector
  "Selector for songs"
  (s/child (s/and (s/class :grid_6) (s/class :omega))
           (s/and (s/class :content) (s/class :song-list))
           (s/and (s/class :grid_3) (s/class :alpha))
           (s/tag :li)
           (s/tag :a)))

(defn get-songs
  "Extract songs from a hickory node"
  [album-node]
  (s/select song-selector album-node))

(defn get-title
  "Get an album-node's title"
  [album-node]
  (-> (s/select (s/tag :h3) album-node)
      first
      :content
      first 
      :content
      first))

(defn parse-album
  "Create a ParsedAlbum and put it on a channel"
  [album-node output-chan]
  (async/put! output-chan
              (make-parsed-album (get-title album-node)
                                 (get-meta album-node)
                                 (get-songs album-node))))

(defn parse-albums
  "Create ParsedAlbums from nodes taken from a chan and put them on another
   chan"
  [{:keys [album-nodes-chan output-chan running?]}]
  (async/go-loop [album-node (async/<! album-nodes-chan)]
   (if (and (some? album-node) @running?)
     (do
       (parse-album album-node output-chan)
       (recur (async/<! album-nodes-chan)))
     (async/close! output-chan))))

(defrecord AlbumParser [album-nodes-chan output-chan running?]
  component/Lifecycle
  (start [component]
    (when-not @running?
      (println "starting AlbumParser")
      (swap! running? not)
      (parse-albums component))
    component)

  (stop [component]
    (when @running?
      (println "stopping AlbumParser")
      (-> component
          (update :running? swap! not)
          (update :output-chan async/close!)))))


(defn new-album-parser [album-nodes-chan output-chan]
  (map->AlbumParser {:album-nodes-chan album-nodes-chan
                     :output-chan output-chan
                     :running? (atom false)}))

