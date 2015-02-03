(ns lyrics.scraping
    (:require [net.cgrand.enlive-html :as enlive])
    (:import (java.net.URL.)
             (java.lang.String)))

(def url-root "http://ohhla.com/")
(def root-page-url (str url-root "all.html"))

(defn get-resource [url-string]
    (-> url-string URL. enlive/html-resource))

(def wanted-link? 
    (every-pred #(some? %) 
                #(not (.startsWith % "http")) 
                #(not (.contains % "#"))))

(defn get-links [res]
    (filter wanted-link? (map #(get-in % [:attrs :href])
                            (html/select res [:a]))))

