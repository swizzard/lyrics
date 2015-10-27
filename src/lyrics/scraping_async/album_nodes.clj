(ns lyrics.scraping-async.album-nodes
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [hickory.select :as s]
            [lyrics.scraping-async.utils :refer [coll->chan
                                                 hickorize
                                                 is-running?
                                                 iterate-link]]))


(defn- select-album-nodes
  "Get hickory nodes representing albums"
  [body]
  (s/select (s/class "album-track-list") (hickorize body)))

(defn get-album-nodes
  "Put album nodes onto a channel"
  [start-page output-chan state]
  (loop [page start-page
         idx 2]
    (when (is-running? state)
      (let [{{url :url} :opts body :body} @(http/get page)]
        (when (= url page)
          (coll->chan (select-album-nodes body) output-chan)
          (recur (iterate-link page idx)
                 (inc idx)))))))

(defrecord AlbumNodeExtractor [pages-chan output-chan running?]
  component/Lifecycle
  (start [component]
    (println "starting AlbumNodeExtractor")
    (swap! running? not)
    (async/go-loop []
      (when-let [page (async/<! pages-chan)]
        (get-album-nodes page output-chan running?)))
    component)

  (stop
    [component]
    (println "stopping AlbumNodeExtractor")
    (-> component
        (update :output-chan async/close!)
        (update :running? swap! not))))

(defn make-album-node-extractor
  "Create an AlbumNodeExtractor"
  [pages-chan output-chan]
  (->AlbumNodeExtractor pages-chan output-chan (atom false)))
