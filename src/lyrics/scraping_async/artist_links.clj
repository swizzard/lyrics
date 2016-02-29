(ns lyrics.scraping-async.artist-links
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [hickory.select :as s]
            [hickory.core :refer [as-hickory]]
            [org.httpkit.client :as http]
            [lyrics.scraping-async.utils :refer [hickorize
                                                 iterate-link]]))


(def url-root
  "The root url"
  "http://www.metrolyrics.com")

(def artists-root
  "Root url for artists"
  (str url-root "/top-artists.html"))

(defn get-letter-links
  "Return a channel onto which have been put letter links"
  [url]
  (let [selector (s/descendant (s/and (s/tag :p)
                                      (s/class "artist-letters"))
                               (s/tag :a))
        out-chan (async/chan)]
    (->> @(http/get url) :body
                         hickorize
                         (s/select selector)
                         (map #(get-in % [:attrs :href]))
                         (async/onto-chan out-chan))
    out-chan))

(defn extract-links
  "Extract links from a hickory body"
  [raw-body]
  (->> raw-body
       hickorize
       (s/select (s/descendant (s/tag :td) (s/tag :a)))))

(defn format-link
  "Fix scraped links"
  [raw-link]
  (-> raw-link
      (get-in [:attrs :href])
      (string/replace "-lyrics" "-albums-list")))

(defn get-artist-letter
  "Put artist links onto a channel"
  [output-chan starting-link]
  (loop [page starting-link
         idx 2]
     (let [{{url :url} :opts body :body} @(http/get page)]
        (when (and (= url page) (not (empty? body)))
            (async/onto-chan output-chan (map format-link (extract-links body))
                             false)
            (recur (iterate-link page idx)
                    (inc idx))))))

(defn get-artist-links
  "Process letter links, putting the results onto a channel"
  [letter-links-chan output-chan running?]
  (async/go-loop [letter-link (async/<! letter-links-chan)]
    (if (and @running? (some? letter-link))
      (do
        (println letter-link)
        (get-artist-letter output-chan letter-link)
        (recur (async/<! letter-links-chan)))
      (async/close! output-chan))))

(defrecord ArtistLinks [start-url output-chan running?]
  component/Lifecycle
  (start [component]
    (when-not @running?
      (println "starting ArtistLinks")
      (swap! running? not)
      (let [letter-links (get-letter-links start-url)]
        (get-artist-links letter-links output-chan running?)))
    component)

  (stop [component]
    (when running?
      (println "stopping ArtistLinks")
      (-> component
          (update :output-chan async/close!)
          (update :running? swap! not)))))


(defn new-artist-links
  "Make an ArtistLinks component"
  ([start-url output-chan]
    (->ArtistLinks start-url output-chan (atom false)))
  ([output-chan]
   (new-artist-links artists-root output-chan)))

