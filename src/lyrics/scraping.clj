(ns lyrics.scraping
    (:require [net.cgrand.enlive-html :as enlive]
              [clj-http.client :as client]
              [clojure.string :as string]
              [lyrics.mongo :refer [insert]]
              [clojure.tools.reader.edn :as edn])
    (:import (java.lang.String)))


(defn from-edn [edn-file] (with-open [r (clojure.java.io/reader edn-file)]
                            (edn/read (java.io.PushbackReader. r))))

(def url-root "http://www.metrolyrics.com")

(defn get-resource [url-string]
    (try
      (-> (client/get url-string) :body enlive/html-snippet)
      (catch Exception e nil)))

(defn- valid-link? [^String link]
  (not (re-find #"(?<!http:)//" link)))

(defn- rstrip [^String s c]
  (if (.endsWith s c) (apply str (butlast s))
    s))

(defn get-link [node] (get-in node [:attrs :href]))

(defn get-links [url selector] (map get-link (enlive/select (get-resource url)
                                                            selector)))

(defn get-links-from-res [res selector] (map get-link (enlive/select res selector)))

(def letter-links (map get-link
                       (enlive/select (get-resource 
                                        (str url-root
                                             "/top-artists.html"))
                                      [:p.artist-letters :a])))

(defn get-artists [res] (map get-link (enlive/select res [:td :a])))

(defn assemble-link 
  ([letter idx] 
    (let [root "http://www.metrolyrics.com/artists-"]
      (if (= 0 idx) (str root letter ".html")
                    (str root letter "-" idx ".html"))))
  ([letter] (assemble-link letter 0)))

(defn iterate-link [starting-link idx] 
  (if (= 0 idx) starting-link
                (string/replace starting-link #"\.html$" 
                                              (str "-" idx ".html"))))

(defn- fix-artist-link [artist-link] (string/replace artist-link "-lyrics" "-albums-list"))

(defn collect-artists [letter] (let [first-page (assemble-link letter)
                                     ga (fn [resp] (distinct (get-artists (-> resp :body enlive/html-snippet))))]
                                 (loop [idx 1
                                        page (assemble-link letter idx)
                                        artists nil]
                                   (do (println "page " page)
                                   (let [resp (client/get page)]
                                     (if (= first-page (last (:trace-redirects resp)))
                                        artists
                                        (recur (inc idx)
                                               (assemble-link letter (inc idx))
                                               (concat artists (map fix-artist-link (ga resp))))))))))

(defn get-all-artists [] (mapcat collect-artists ["1" "a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m"
                                                  "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z"]))

(defn lyrics-from-page [res] (get-links-from-res res [:ul.grid_3 :li :a]))

(defn get-lyrics [start-page] (let [gl (fn [resp] (-> resp
                                                      :body
                                                      enlive/html-snippet
                                                      lyrics-from-page))
                                    lyrics (gl start-page)]
                                (loop [idx 2
                                       lyrics nil]
                                  (let [next-url (iterate-link start-page idx)
                                        u (client/get next-url)]
                                    (println "page " next-url)
                                    (if (> (count (distinct (:trace-redirects u))) 1)
                                      lyrics
                                      (recur (inc idx)
                                             (concat lyrics (gl u))))))))


(defn strify [ss] (apply str (interpose " " ss)))

(defn parse-lyrics-url [lyrics-url]
  (let [splt (split-with #(not= % "lyrics")
                         (-> lyrics-url
                             (string/replace ".html" "")
                             (string/split #"/")
                             last
                             (string/split #"-")))]
    {:url-song (strify (first splt))
     :url-artist (strify (next (second splt)))}))

(defn extract-lyrics [lyrics-url]
  (let [lp (-> lyrics-url
               client/get
               :body
               enlive/html-snippet)]
    (merge (parse-lyrics-url lyrics-url)
           {:url lyrics-url
            :load (-> lp 
                      (enlive/select [:div.load])
                      first
                      :content
                      first)
            :lyrics (apply str (map enlive/text (-> lp
                                                    (enlive/select
                                                      [:div#lyrics-body-text])
                                                    first
                                                    :content)))})))

(defn get-all-lyrics [] (into [] (comp (mapcat extract-lyrics)
                                       (mapcat get-lyrics))
                                  (get-all-artists)))

(defn escape-key [^String k] (-> k (string/replace "." "")
                                   (string/replace #"^\$" "_$")))

(defn ident-from-link [link]
  (-> link (string/split #"/") last))

(defn get-text [link] 
  (second (re-find #"<pre>([\s\S]+)</pre>"
                   (:body (client/get link)))))

(defn extract-links [link])

(defn attr-from-link [^String link ^clojure.lang.Keyword attr]
  ;; this pre is awkward, but it prevents a null pointer exception
  {:pre [(contains? #{:title :song :album :artist} attr)]}
  (let [s (string/split link #"/")
        len (count s)
        idx (get {:title 1 :song 1 
                  :album 2 :artist 3} attr)]
    (nth s (- len idx))))

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

