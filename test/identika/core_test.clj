(ns identika.core-test
  (:require [clojure.test :refer :all]
            [identika.core :as identika]
            [identika.ulid :as ulid]))

;; ──────────────────────────────────────────────
;; Factory
;; ──────────────────────────────────────────────

(deftest test-generator-factory
  (testing "Default generator is UUID"
    (let [g (identika/generator)]
      (is (satisfies? identika/IdGenerator g))
      (is (not (satisfies? identika/TimeSortable g)))))

  (testing ":uuid strategy returns a UUID generator"
    (let [g (identika/generator :uuid)]
      (is (satisfies? identika/IdGenerator g))))

  (testing ":ulid strategy returns a ULID generator"
    (let [g (identika/generator :ulid)]
      (is (satisfies? identika/IdGenerator g))
      (is (satisfies? identika/TimeSortable g))))

  (testing "Unknown strategy throws"
    (is (thrown? IllegalArgumentException
                 (identika/generator :nanoid)))))

;; ──────────────────────────────────────────────
;; Protocol satisfaction
;; ──────────────────────────────────────────────

(deftest test-protocol-satisfaction
  (testing "UUIDGenerator satisfies IdGenerator only"
    (let [g (identika/generator :uuid)]
      (is (satisfies? identika/IdGenerator g))
      (is (not (satisfies? identika/TimeSortable g)))
      (is (not (satisfies? identika/MonotonicId g)))))

  (testing "ULIDGenerator satisfies all three protocols"
    (let [g (identika/generator :ulid)]
      (is (satisfies? identika/IdGenerator g))
      (is (satisfies? identika/TimeSortable g))
      (is (satisfies? identika/MonotonicId g)))))

;; ──────────────────────────────────────────────
;; UUID generation and validation
;; ──────────────────────────────────────────────

(deftest test-uuid-generate
  (testing "generate returns a string"
    (is (string? (identika/generate :uuid))))

  (testing "generate returns a valid UUID format"
    (let [uuid-re #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"]
      (dotimes [_ 20]
        (is (re-matches uuid-re (identika/generate :uuid))))))

  (testing "generate with default produces UUID"
    (let [uuid-re #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"]
      (dotimes [_ 20]
        (is (re-matches uuid-re (identika/generate))))))

  (testing "generate produces unique values"
    (let [ids (repeatedly 1000 #(identika/generate :uuid))]
      (is (= (count ids) (count (distinct ids))))))

  (testing "generate from existing record"
    (let [g (identika/generator :uuid)]
      (dotimes [_ 10]
        (is (string? (identika/generate g)))))))

(deftest test-uuid-valid?
  (testing "valid? returns true for valid UUID"
    (is (true? (identika/valid? "550e8400-e29b-41d4-a716-446655440000"))))

  (testing "valid? returns false for invalid strings"
    (is (false? (identika/valid? "not-a-uuid")))
    (is (false? (identika/valid? "")))
    (is (false? (identika/valid? "550e8400-e29b-41d4-a716-44665544000X"))))

  (testing "valid? with explicit strategy keyword"
    (is (true? (identika/valid? :uuid "550e8400-e29b-41d4-a716-446655440000"))))

  (testing "valid? with generator record"
    (let [g (identika/generator :uuid)]
      (is (true? (identika/valid? g "550e8400-e29b-41d4-a716-446655440000")))
      (is (false? (identika/valid? g "not-a-uuid"))))))

(deftest test-uuid-roundtrip
  (testing "to-bytes and bytes->id round-trip"
    (dotimes [_ 20]
      (let [uid (identika/generate :uuid)
            bi  (identika/to-bytes :uuid uid)
            raw (.toByteArray bi)
            n   (count raw)
            ba16 (byte-array 16)]
        ;; Pad or trim to exactly 16 bytes
        (if (> n 16)
          (System/arraycopy raw 1 ba16 0 16)
          (System/arraycopy raw 0 ba16 (- 16 n) n))
        (is (= uid (identika/bytes->id :uuid ba16)))))))

;; ──────────────────────────────────────────────
;; ULID generation and validation
;; ──────────────────────────────────────────────

(deftest test-ulid-generate
  (testing "generate :ulid returns a 26-char Crockford Base32 string"
    (let [u (identika/generate :ulid)]
      (is (string? u))
      (is (= 26 (count u)))))

  (testing "generate :ulid with timestamp opts"
    (let [u (identika/generate :ulid {:timestamp 1781290640998})]
      (is (= 26 (count u)))))

  (testing "generate from ULIDGenerator record"
    (let [g (identika/generator :ulid)]
      (dotimes [_ 10]
        (is (= 26 (count (identika/generate g)))))))

  (testing "ULIDs are unique"
    (let [ids (repeatedly 100 #(identika/generate :ulid))]
      (is (= (count ids) (count (distinct ids)))))))

(deftest test-ulid-valid?
  (testing "valid? :ulid returns true for valid ULID"
    (let [u (identika/generate :ulid)]
      (is (true? (identika/valid? :ulid u)))))

  (testing "valid? :ulid returns false for invalid strings"
    (is (false? (identika/valid? :ulid "")))
    (is (false? (identika/valid? :ulid "not-a-ulid")))
    (is (false? (identika/valid? :ulid "01KVWFN1PF8N3GTDD2J98P3GX&"))))

  (testing "valid? rejects UUID with ULID strategy (and vice versa)"
    (is (false? (identika/valid? :ulid "550e8400-e29b-41d4-a716-446655440000")))
    (is (false? (identika/valid? (identika/generate :ulid))))))

(deftest test-ulid-timestamp
  (testing "get-timestamp returns correct timestamp"
    (let [ts 1781290640998
          u  (identika/generate :ulid {:timestamp ts})]
      (is (= ts (identika/get-timestamp :ulid u)))))

  (testing "get-timestamp returns nil for non-time-sortable IDs"
    (is (nil? (identika/get-timestamp :uuid (identika/generate :uuid))))))

(deftest test-ulid-next-id
  (testing "next-id returns a larger ULID"
    (let [u     (identika/generate :ulid)
          nxt   (identika/next-id :ulid u)]
      (is (pos? (compare nxt u)))))

  (testing "next-id returns nil for non-monotonic types"
    (is (nil? (identika/next-id :uuid (identika/generate :uuid))))))

(deftest test-ulid-monotonic-gen
  (testing "monotonic-gen produces increasing values"
    (let [state (atom nil)
          ids   (repeatedly 50 #(identika/monotonic-gen :ulid state))]
      (is (every? neg? (map compare ids (rest ids))))))

  (testing "monotonic-gen throws for non-monotonic types"
    (is (thrown? IllegalArgumentException
                 (identika/monotonic-gen :uuid (atom nil))))))

;; ──────────────────────────────────────────────
;; Cross-strategy consistency
;; ──────────────────────────────────────────────

(deftest test-polymorphic-dispatch
  (testing "generate dispatches on keyword or record"
    (let [uuid-str (identika/generate :uuid)
          ulid-str (identika/generate :ulid)
          uuid-gen (identika/generator :uuid)
          ulid-gen (identika/generator :ulid)]
      (is (string? (identika/generate uuid-gen)))
      (is (string? (identika/generate ulid-gen)))
      (is (= 36 (count uuid-str)))
      (is (= 26 (count ulid-str)))))

  (testing "valid? dispatches on keyword or record"
    (let [u (identika/generate :uuid)]
      (is (true? (identika/valid? :uuid u)))
      (is (true? (identika/valid? (identika/generator :uuid) u))))))
