(ns lyrics.redis-test
  (:require [clojure.test :refer :all]
            [lyrics.redis :refer [cached? clear-cache]]))

(defn redis-fixture [f]
  (alter-var-root #'lyrics.redis/redis-key (constantly "test-redis-key"))
  (f)
  (clear-cache))

(use-fixtures :once redis-fixture)

(deftest cached?-test
  (testing "cached?"
    (are [expected k]
      (= expected (cached? k))
      false "key 1"
      false "key 2"
      true "key 1"
      true "key 2")))
