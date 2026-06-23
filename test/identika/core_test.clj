(ns identika.core-test
  (:require [clojure.test :refer :all]
            [identika.core :as identika])
  (:import [java.time Instant]))

;; ──────────────────────────────────────────────
;; ULID string generation
;; ──────────────────────────────────────────────

(deftest test-ulid-generation
  (testing "ULID returns a string"
    (is (string? (identika/ulid))))

  (testing "ULID returns a random string"
    (is (not= (identika/ulid)
              (identika/ulid))))

 (testing "ULID returns a 26-character string"
    (is (= 26 (count (identika/ulid))))
    (dotimes [_ 10]
      (is (= 26 (count (identika/ulid))))))

  (testing "ULID contains only Crockford Base32 characters"
    (let [valid-chars #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9
                        \A \B \C \D \E \F \G \H \J \K
                        \L \M \N \P \Q \R \S \T \V \W \X \Y \Z}]
      (dotimes [_ 10]
        (let [u (identika/ulid)]
          (is (every? valid-chars u) (str "Invalid chars in " u))))))

  (testing "Consecutive ULIDs are unique"
    (let [ids (repeatedly 1000 identika/ulid)]
      (is (= (count ids) (count (distinct ids)))
          "All 1000 generated ULIDs should be unique")))

  (testing "ULIDs are lexicographically sortable by time"
    (let [a (identika/ulid)
          _ (Thread/sleep 2)
          b (identika/ulid)]
      (is (neg? (compare a b))
          (str "Earlier ULID " a " should sort before later " b))))

  (testing "ULIDs generate at same time are unique"
    (let [ids (repeatedly 1000 #(identika/ulid 1781290640998))]
      (is (= (count ids) (count (distinct ids)))
          "All 1000 generated ULIDs should be unique")))

  (testing "ULIDS generate at an specific time should not change that time"
    (let [timestamp 1781290640998
          ulid (identika/ulid timestamp)]
      (is (= timestamp
             (identika/ulid->time ulid)))))
  )
