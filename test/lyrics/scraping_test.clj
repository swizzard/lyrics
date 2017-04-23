(ns lyrics.scraping-test
  (:require [clojure.test :refer :all]
            [clj-http.fake :refer [with-fake-routes]]
            [net.cgrand.enlive-html :as e]
            [lyrics.scraping :refer :all]))


(deftest get-resource-test
  (testing "get-resource"
    (let [body "test body"]
      (with-fake-routes
        {"http://no.com" (fn [r] {:status 304 :headers {} :body body})
        "http://yes.com" (fn [r] {:status 200 :headers {} :body body})}
        (are [expected url-str]
          (= expected (get-resource url-str))
          nil "http://no.com"
          `(~body) "http://yes.com")))))

(deftest assemble-link-test
  (testing "assemble-link"
    (is (= "http://www.metrolyrics.com/artists-a-1.html" (assemble-link "a" 1)))
    (is (= "http://www.metrolyrics.com/artists-a.html" (assemble-link "a" 0)))
    (is (= "http://www.metrolyrics.com/artists-a.html" (assemble-link "a")))))

(deftest iterate-link-with-idx-test
  (testing "iterate-link with idx"
    (let [starting-link "http://test.com/test.html"]
      (are [expected idx]
        (= expected (iterate-link starting-link idx))
        "http://test.com/test.html" 0
        "http://test.com/test-1.html" 1))))

(deftest iterate-link-no-idx-test
  (testing "iterate-link without idx"
    (let [body "foo"
          ok-fn (fn [r] {:status 200 :body body :headers {}})
          bad-fn (fn [r] {:status 304 :body body :headers {}})
          expected (repeat 2 (e/html-snippet body))]
     (with-fake-routes
      {"http://test.com/test.html" ok-fn
       "http://test.com/test-1.html" ok-fn
       "http://test.com/test-2.html" bad-fn
       "http://test.com/test-3.html" bad-fn}
       (is (= expected (iterate-link "http://test.com/test.html")))))))

(deftest fix-artist-link-test
  (testing "fix-artist-link"
    (is (= "http://test.com/foo-albums-list" (fix-artist-link "http://test.com/foo-lyrics")))))
