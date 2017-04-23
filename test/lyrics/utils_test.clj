(ns lyrics.utils-test
  (:require [clojure.test :refer :all]
            [lyrics.utils :refer :all]))

(deftest take-until-nils-test
  (testing "take until nils"
    (are [expected orig]
         (= expected (into [] (take-until-nils 2) orig))
         [1 2 3] [1 2 nil 3 nil 4]
         [] [nil nil 1 2 3]
         [1 2 3] [1 2 3])))
