(ns lyrics.txt-parsing-test
  (:require [clojure.test :refer :all]
            [lyrics.txt-parsing :refer :all]))


(deftest split-lyrics-test
  (testing "split lyrics"
    (are [expected orig]
      (= expected (split-lyrics orig))
      '(["foo" "bar"] ["baz" "quux"]) "foo bar\nbaz quux"
      '(["foo" "bar"]) "foo bar"
      '(["foo"]) "foo"
      '(["foo" "bar"] ["baz" "quux"]) "foo\tbar\nbaz\tquux")))

(def ^:private expected-tokens
  '({:value "one" :line-no 0 :idx 0 :is-eol false}
    {:value "two" :line-no 0 :idx 1 :is-eol false}
    {:value "three" :line-no 0 :idx 2 :is-eol true}
    {:value "four" :line-no 1 :idx 0 :is-eol false}
    {:value "five" :line-no 1 :idx 1 :is-eol false}
    {:value "six" :line-no 1 :idx 2 :is-eol true}))

(def ^:private test-tokens
  '(["one" "two" "three"] ["four" "five" "six"]))

(deftest parse-tokens-test
  (testing "parse-tokens"
    (is (= expected-tokens (parse-tokens test-tokens)))))
