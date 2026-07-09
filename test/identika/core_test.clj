(ns identika.core-test
  (:require [clojure.test :refer :all]
            [identika.core :as identika]))

;; ──────────────────────────────────────────────
;; Core API: generate
;; ──────────────────────────────────────────────

(deftest test-generate
  (testing "generate with no args returns UUID v4 string"
    (let [id (identika/generate)]
      (is (string? id))
      (is (= 36 (count id)))))

  (testing "generate :uuid returns UUID v4 string"
    (let [id (identika/generate :uuid)]
      (is (string? id))
      (is (= 36 (count id)))))

  (testing "generate :ulid returns ULID string"
    (let [id (identika/generate :ulid)]
      (is (string? id))
      (is (= 26 (count id)))))

  (testing "Unknown strategy throws"
    (is (thrown? IllegalArgumentException
                 (identika/generate :nanoid)))))

;; ──────────────────────────────────────────────
;; Core API: valid?
;; ──────────────────────────────────────────────

(deftest test-valid
  (testing "valid? on valid IDs"
    (is (true? (identika/valid? :uuid (identika/generate :uuid))))
    (is (true? (identika/valid? :ulid (identika/generate :ulid)))))

  (testing "valid? on invalid IDs"
    (is (false? (identika/valid? :uuid "not-a-uuid")))
    (is (false? (identika/valid? :ulid "not-a-ulid"))))

  (testing "Unknown strategy throws"
    (is (thrown? IllegalArgumentException
                 (identika/valid? :nanoid "anything")))))

;; ──────────────────────────────────────────────
;; Delegation to unsupported operations
;; ──────────────────────────────────────────────

(deftest test-unsupported-operations
  (testing "get-timestamp returns nil for non-time-sortable strategies"
    (is (nil? (identika/get-timestamp :uuid (identika/generate :uuid)))))

  (testing "next-id returns nil for non-monotonic strategies"
    (is (nil? (identika/next-id :uuid (identika/generate :uuid)))))

  (testing "monotonic-gen returns nil for non-monotonic strategies"
    (is (nil? (identika/monotonic-gen :uuid (atom nil)))))

  (testing "Unknown strategy throws for all operation multimethods"
    (is (thrown? IllegalArgumentException
                 (identika/get-timestamp :nanoid "x")))
    (is (thrown? IllegalArgumentException
                 (identika/next-id :nanoid "x")))
    (is (thrown? IllegalArgumentException
                 (identika/monotonic-gen :nanoid (atom nil))))
    (is (thrown? IllegalArgumentException
                 (identika/encode :nanoid (byte-array 16))))
    (is (thrown? IllegalArgumentException
                 (identika/decode :nanoid "x")))))
