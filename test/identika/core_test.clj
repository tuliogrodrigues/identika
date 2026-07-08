(ns identika.core-test
  (:require [clojure.test :refer :all]
            [identika.core :as identika]
            [identika.protocols :as proto]))

;; ──────────────────────────────────────────────
;; Factory & protocol contracts
;; ──────────────────────────────────────────────
;; Each generator is created ONCE; all protocol satisfactions and factory
;; behaviors are asserted together so we don't instantiate redundant objects.

(deftest test-generator-factory
  (testing "Default (no args) creates UUID — IdGenerator only"
    (let [g (identika/generator)]
      (is (satisfies? proto/IdGenerator g))
      (is (not (satisfies? proto/TimeSortable g)))
      (is (not (satisfies? proto/MonotonicId g)))))

  (testing ":uuid creates UUID — IdGenerator only"
    (let [g (identika/generator :uuid)]
      (is (satisfies? proto/IdGenerator g))
      (is (not (satisfies? proto/TimeSortable g)))
      (is (not (satisfies? proto/MonotonicId g)))))

  (testing ":ulid creates ULID — all three protocols"
    (let [g (identika/generator :ulid)]
      (is (satisfies? proto/IdGenerator g))
      (is (satisfies? proto/TimeSortable g))
      (is (satisfies? proto/MonotonicId g))))

  (testing "Unknown strategy throws"
    (is (thrown? IllegalArgumentException
                 (identika/generator :nanoid)))))

;; ──────────────────────────────────────────────
;; Polymorphic dispatch (keyword vs record)
;; ──────────────────────────────────────────────

(deftest test-polyglot-dispatch
  (testing "generate via keyword or protocol dispatch"
    (let [uuid-str (identika/generate :uuid)
          ulid-str (identika/generate :ulid)
          uuid-gen (identika/generator :uuid)
          ulid-gen (identika/generator :ulid)]
      (is (string? (proto/generate uuid-gen nil)))
      (is (string? (proto/generate ulid-gen nil)))
      (is (= 36 (count uuid-str)))
      (is (= 26 (count ulid-str)))))

  (testing "valid? via keyword or protocol dispatch"
    (let [u (identika/generate :uuid)]
      (is (true? (identika/valid? :uuid u)))
      (is (true? (proto/valid? (identika/generator :uuid) u))))))

;; ──────────────────────────────────────────────
;; Delegation to unsupported protocols
;; ──────────────────────────────────────────────

(deftest test-unsupported-protocols
  (testing "get-timestamp returns nil for non-time-sortable strategies"
    (is (nil? (identika/get-timestamp :uuid (identika/generate :uuid)))))

  (testing "next-id returns nil for non-monotonic strategies"
    (is (nil? (identika/next-id :uuid (identika/generate :uuid)))))

  (testing "monotonic-gen returns nil for non-monotonic strategies"
    (is (nil? (identika/monotonic-gen :uuid (atom nil))))))
