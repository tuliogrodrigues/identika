(ns identika.ulid-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [identika.ulid :as ulid]))

;; ──────────────────────────────────────────────
;; ULID string generation
;; ──────────────────────────────────────────────

(deftest test-ulid-generation
  (testing "ULID returns a string"
    (is (string? (ulid/gen))))

  (testing "ULID returns a random string"
    (is (not= (ulid/gen)
              (ulid/gen))))

 (testing "ULID returns a 26-character string"
    (is (= 26 (count (ulid/gen))))
    (dotimes [_ 10]
      (is (= 26 (count (ulid/gen))))))

  (testing "ULID contains only Crockford Base32 characters"
    (let [valid-chars #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9
                        \A \B \C \D \E \F \G \H \J \K
                        \L \M \N \P \Q \R \S \T \V \W \X \Y \Z}]
      (dotimes [_ 10]
        (let [u (ulid/gen)]
          (is (every? valid-chars u) (str "Invalid chars in " u))))))

  (testing "ULIDs are lexicographically sortable by time"
    (let [a (ulid/gen)
          _ (Thread/sleep 2)
          b (ulid/gen)]
      (is (neg? (compare a b))
          (str "Earlier ULID " a " should sort before later " b))))

  (testing "ULIDs generate at same time are unique"
    (let [ids (repeatedly 1000 #(ulid/gen 1781290640998))]
      (is (= (count ids) (count (distinct ids)))
          "All 1000 generated ULIDs should be unique")))

  (testing "ULIDS generate at an specific time should not change that time"
    (let [timestamp 1781290640998
          ulid (ulid/gen timestamp)]
      (is (= timestamp
             (ulid/timestamp ulid)))))

    (testing "Consecutive ULIDs are unique"
    (let [ids (repeatedly 1000 ulid/gen)]
      (is (= (count ids) (count (distinct ids)))
          "All 1000 generated ULIDs should be unique")))
  )

(deftest test-ulid-validation
  (testing "Empty string is not a valid ULID"
    (is (not (ulid/valid? ""))
        "ULID cannot be empty"))

  (testing "ULID cannot contains"
    (testing "More than 26 valid characters"
      (let [valid-ulid "01KVWFN1PF8N3GTDD2J98P3GXK"]
        (is (and (ulid/valid? valid-ulid)
                 (not (ulid/valid? (str/join [valid-ulid "A"])))))))

    (testing "Less than 26 valid characters"
      (let [valid-ulid "01KVWFN1PF8N3GTDD2J98P3GXK"]
        (is (and (ulid/valid? valid-ulid)
                 (not (ulid/valid? (subs valid-ulid 1)))))))

    (testing "Dubious characters as I L O U"
      (let [invalid-ulid "0IKVWFN1PF8N3GTDD2J98P3GXK"]
        (is (not (ulid/valid? invalid-ulid)))))

    (testing "Cannot have special characters"
      (let [invalid-ulid "01KVWFN1PF8N3GTDD2J98P3GX&"]
        (is (not (ulid/valid? invalid-ulid)))))))
