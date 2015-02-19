(ns lyrics.scraping
    (:require [net.cgrand.enlive-html :as enlive]
              [clj-http.client :as client]
              [clojure.string :as string]
              [lyrics.mongo :refer [insert]])
    (:import (java.net.URL)
             (java.lang.String)))

(def url-root "http://ohhla.com/")

(defn get-resource [url-string]
    (try
      (-> url-string java.net.URL. enlive/html-resource)
      (catch Exception e nil)))

(defn wanted-link? [^String link]
  (seq (remove #(= "/anonymous/" %) 
                 (re-find #"(\.txt/?|/[\w&&[^&=]]+/|YFA_\w+\.html)$" link))))

(defn valid-link? [^String link]
  (not (re-find #"(?<!http:)//" link)))

(defn rstrip [^String s c]
  (if (.endsWith s c) (apply str (butlast s))
    s))

(defn blm [root suffix]
  (let [root (if (.endsWith root ".html")
                 (string/replace root #"\w+\.html$" "")
               root)]
    (str root suffix)))

(defn links-from-resource [res] (map #(get-in % [:attrs :href])
                                     (enlive/select res [:a])))

(defn links-from-string [link] (-> link get-resource links-from-resource))

(defn extract-links [root] (filter (every-pred some? wanted-link?)
                                   (map (partial blm root) 
                                        (links-from-string root))))

(defn escape-key [^String s] (let [fixed (-> s
                                             (string/replace ".txt" "")
                                             (string/replace #"\." "-"))]
                               (println s " -> " fixed)
                               fixed))

(defn ident-from-link [link]
  (-> link (string/split #"/") last))

(defn get-text [link] 
  (second (re-find #"<pre>([\s\S]+)</pre>"
                   (:body (client/get link)))))

(defn attr-from-link [^String link ^clojure.lang.Keyword attr]
  ;; this pre is awkward, but it prevents a null pointer exception
  {:pre [(contains? #{:title :song :album :artist} attr)]}
  (let [s (string/split link #"/")
        len (count s)
        idx (get {:title 1 :song 1 
                  :album 2 :artist 3} attr)]
    (nth s (- len idx))))

(comment
(defn process-unstructured [links]
  (let [artists (group-by #(attr-from-link % :artist) links)]))
)

(defn get-song-dict [song-link]
  (let [song-ident (escape-key (ident-from-link song-link))]
    {:raw-text (get-text song-link) 
     :title song-ident
     :song-link song-link}))

(defn process-song [m song-link idx]
    (let [song-ident (escape-key (ident-from-link song-link))]
    (println "song-ident: " song-ident)
    (update-in m [:songs] conj
               (assoc (get-song-dict song-link)
                      :idx idx))))

(defn process-album [m album-link]
  (let [song-links (filter #(.endsWith % ".txt")
                           (extract-links album-link))
        album-ident (escape-key (ident-from-link album-link))]
    (println "album-ident: " album-ident)
    (println "song-links: " song-links)
    (loop [h (first song-links)
           t (next song-links)
           album-map {:title album-ident
                      :album-link album-link}
           song-idx 1]
          (if (nil? h) (update-in m [:albums] conj album-map)
                       (do (println "h (process-album): " h)
                       (recur (first t)
                              (next t)
                              (process-song album-map 
                                            h 
                                            song-idx)
                              (inc song-idx)))))))

(defn process-artist [link]
  (let [album-links (remove #(= % link) 
                            (extract-links link))
        artist-ident (escape-key (ident-from-link link))]
    (do (println "artist-ident: " artist-ident)
        (println "album-links: " album-links)
    (loop [h (first album-links)
           t (next album-links)
           m {:artist-link link
              :artist artist-ident}]
          (if (nil? h) m
                (do 
                  (println "h (process-artist): "h)
                  (recur (first t)
                         (next t)
                         (process-album m h))))))))

(defn links-to-mongo [root]
  (let [links (extract-links root)]
    (doseq [link links]
      (println link)
      (insert (process-artist link)))))

(defn is-link-to-txt [^String url-string] (.endsWith url-string ".txt"))

(def starting-links ["all.html" "all_two.html" "all_three.html"
                     "all_four.html" "all_five.html"])

