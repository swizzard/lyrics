(ns lyrics.sky
  (:require [clojure.string :as string]
            [net.cgrand.enlive :as e]
            [skyscraper :refer :all]
            [skyscraper.cache :as cache]))

(declare safe-conj parse-letter-url parse-artist-url)

(defn seed [& _]
  [{:url "http://www.metrolyrics.com/artists-1.html"
    :processor :letter-page
    :letter "1"
    :idx 1
    :start true}])

(defn get-letters [res]
  (sequence (comp (map href)
                  (map #(string/replace % #".html$" "-1.html"))
                  (map #(assoc (merge {:start false :processor :letter-page}
                                      (parse-letter-url %))
                                :url %)))
            (e/select r [:p.artist-letters :a])))

(defn get-letter-artists [res]
  (let [get-content (fn [n] (get-in n [:attrs :content]))]
    (for [[link-node name-node]
          (partition 2 (e/select res [[:meta
                                       (e/but (e/attr? :name))
                                       (e/but (e/attr? :http-equiv))
                                       (e/but (e/attr? :charset))]]))]
      (assoc (parse-artist-url (get-content link-node))
             :name (get-content name-node)
             :processor :artist-page))))

(defn get-next-url [res]
  (let [url (-> res (e/select [:a.button.next]) href)]
    (if (some? (re-find #"http://" url)) url)))

(defn next-letter-page [res]
  (if-let [next-url (get-next-url res)]
    (assoc-in (parse-letter-url next-url)
              :processor :letter-page)))

(defprocessor letter-page
  :cache-template "lyrics/letter/:letter/:idx"
  :process-fn (fn [res {:keys [url start] :as context}]
                (let [ctxs (safe-conj (get-letter-artists res)
                                      (next-letter-page res))]
                  (if-not start
                    ctxs
                    (into ctxs (get-letters res))))))

(defn next-artist-page [res]
  (assoc-in (parse-artist-url (get-next-url res))
            :processor :artist-page))

(defprocessor artist-page
  :cache-template "lyrics/artist/:artist/:idx"
  :
  )

;; helpers

(defn parse-letter-url [url]
  (let [p (re-pattern (str "http://www\.metrolyrics\.com/"
                           "[\w\d/-]+?-([\w\d])-(\d+)\.html"))
        [_ letter idx] (re-matches p url)]
    {:letter letter
      :idx (int idx)
      :url url}))

(defn parse-artist-url [url]
  (let [album-list-pat #".*/([\w\d-]+?)-album-list(-(\d+))?.html$"
        fixed-url (string/replace url #"-lyrics.html" "-albums-list.html")
        [_ artist _ idx] (re-matches album-list-pat fixed-url)]
    {:url fixed-url
     :artist artist
     :idx (if (nil? idx) 1 (int idx))}))

(defn safe-conj [coll new-val]
  (if (some? new-val) (conj coll new-val) coll))

