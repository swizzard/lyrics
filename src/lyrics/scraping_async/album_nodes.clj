(ns lyrics.scraping-async.album-nodes
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [hickory.select :as s]
            [lyrics.scraping-async.utils :refer [coll->chan
                                                 hickorize
                                                 iterate-link]]))


(defn- select-album-nodes
  "Get hickory nodes representing albums"
  [body]
  (s/select (s/class "album-track-list") (hickorize body)))

(defn get-album-nodes
  "Put album nodes onto a channel"
  [start-page output-chan running?]
  (loop [page start-page
         idx 2]
    (when @running?
      (let [{{url :url} :opts body :body} @(http/get page)]
        (when (= url page)
          (coll->chan (select-album-nodes body) output-chan)
          (recur (iterate-link page idx)
                 (inc idx)))))))

(defrecord AlbumNodeExtractor [pages-chan output-chan running?]
  component/Lifecycle
  (start [component]
    (when-not @running?
      (println "starting AlbumNodeExtractor")
      (swap! running? not)
      (async/go-loop []
        (when-let [page (async/<! pages-chan)]
          (get-album-nodes page output-chan running?))))
    component)

  (stop
    [component]
    (when @running?
      (println "stopping AlbumNodeExtractor")
      (-> component
          (update :output-chan async/close!)
          (update :running? swap! not)))))

(defn new-album-node-extractor
  "Create an AlbumNodeExtractor"
  [pages-chan output-chan]
  (->AlbumNodeExtractor pages-chan output-chan (atom false)))
