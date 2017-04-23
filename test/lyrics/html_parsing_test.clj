(ns lyrics.html-parsing-test
  (:require [clojure.test :refer :all]
            [clojure.string :refer [join]]
            [net.cgrand.enlive-html :as e]
            [lyrics.html-parsing :refer :all]
            ))

(defn- make-link-str [lnk] (str "<a href=\"" lnk "\">test</a>"))

(deftest test-get-link
  (testing "get-link"
    (let [expected "http://test.com"
          snippet (e/html-snippet (make-link-str expected))]
      (is (= expected (-> snippet first get-link))))))

(deftest test-get-links-from-res
  (testing "get-links-from-res"
    (let [expected '("http://test.com/1"
                     "http://test.com/2")
          snippet  (e/html-snippet
                     "<div id=\"one\">
                        <a href=\"http://test.com/1\">test one</a>
                        <a href=\"http://test.com/2\">test two</a>
                      </div>
                      <div id=\"two\">
                        <a href=\"http://test.com/3\">test three</a>
                        <a href=\"http://test.com/4\">test four</a>
                      </div>")]
      (is (= expected (-> snippet (get-links-from-res [:div#one :a])))))))

(deftest test-extract-artist
  (testing "extract-artist"
    (let [expected {:artist "test name"}
          snippet (e/html-snippet
                    "<div itemscope itemtype=\"http://schema.org/MusicGroup\">
                      <meta itemprop=\"name\" content=\"test name\"/>
                      <meta itemprop=\"description\" content=\"test description\"/>
                    </div>")]
      (is (= expected (extract-artist snippet))))))

(deftest test-extract-load
  (testing "extract-load"
    (let [expected {:featuring ["featured artist 1" "featured artist 2"]
                    :album "album" }
          snippet (e/html-snippet
                    "<section id=\"lyrics-main\">
                      <header>
                        <h1 style='font-size:2.3em;'>
                          Test Lyrics
                        </h1>
                          <p class=\"featuring\">
                            feat. <span class=\"fartist\">featured artist 1</span>,
                                  <span class=\"fartist\">featured artist 2</span>
                          </p>
                          <p class=\"album-name\">
                            <span aria-hidden=\"true\" class=\"icon icon-cd\"></span>
                            <em>from
                              <a href=\"\" id=\"album-name-link\">album</a>
                            </em>
                          </p>
                      </header>
                     </section>")]
      (is (= expected (-> snippet first extract-load))))))

(deftest test-extract-lyrics
  (testing "extract-lyrics"
    (let [expected {:lyrics '({:section "verse" :content "foo"}
                              {:section "chorus" :content "bar"})}
          snippet (e/html-snippet
                    "<section id=\"lyrics-main\">
                      <div class=\"lyrics\">
                        <div id=\"lyrics-body-text\">
                          <p class=\"verse\">foo</p>
                          <p class=\"chorus\">bar</p>
                        </div>
                      </div>
                     </section>")]
    (is (= expected (-> snippet first extract-lyrics))))))

