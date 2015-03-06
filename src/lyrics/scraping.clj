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
                                 (do (println "page " first-page)
                                 (loop [idx 1
                                        page (assemble-link letter idx)
                                        artists nil]
                                   (do (println "page " page)
                                   (let [resp (client/get page)]
                                     (if (= first-page (last (:trace-redirects resp)))
                                        artists
                                        (recur (inc idx)
                                               (assemble-link letter (inc idx))
                                               (concat artists (map fix-artist-link (ga resp)))))))))))

(defn get-all-artists [] (mapcat collect-artists ["1" "a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m"
                                                  "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z"]))

(defn get-some-artists [] (collect-artists "1"))

(defn lyrics-from-page [res] (get-links-from-res res [:ul.grid_3 :li :a]))

(defn get-lyrics [start-page] (let [gl (fn [resp] (-> resp
                                                      :body
                                                      enlive/html-snippet
                                                      lyrics-from-page))
                                    lyrics (gl start-page)]
                                (do (println "page " start-page)
                                (loop [idx 2
                                       lyrics nil]
                                  (let [next-url (iterate-link start-page idx)
                                        u (client/get next-url)]
                                    (if (> (count (distinct (:trace-redirects u))) 1)
                                      lyrics
                                      (do (println "page " next-url)
                                      (recur (inc idx)
                                             (concat lyrics (gl u))))))))))


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

(defn filter-redirected [url]
  (let [resp (client/get url)]
  (if (= 1 (-> resp :trace-redirects distinct count))
    (enlive/html-snippet (:body resp)))))

(defn extract-lyrics [lyrics-url]
  (let [lp (filter-redirected lyrics-url)]
    (do (println "lyrics " lyrics-url)
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
                                                 :content)))}))))

(defn get-all-lyrics [artists] (map extract-lyrics
                                    (mapcat get-lyrics artists)))


