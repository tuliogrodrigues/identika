(ns identika.uuid-test
  (:require [clojure.test :refer :all]
            [identika.uuid :as uuidka]))

;; ──────────────────────────────────────────────
;; UUID v4 generation
;; ──────────────────────────────────────────────

(deftest test-uuid-generation
  (testing "returns a string"
    (is (string? (uuidka/gen))))

  (testing "returns a valid UUID v4 format (36 chars, hex-hyphenated)"
    (let [uuid-re #"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"]
      (dotimes [_ 20]
        (is (re-matches uuid-re (uuidka/gen))))))

  (testing "produces unique values"
    (let [ids (repeatedly 1000 #(uuidka/gen))]
      (is (= (count ids) (count (distinct ids))))))

  (testing "version nibble is always 4"
    (dotimes [_ 20]
      (let [u (uuidka/gen)]
        (is (= \4 (nth u 14))))))

  (testing "variant nibble is always 8, 9, a, or b"
    (dotimes [_ 20]
      (let [u       (uuidka/gen)
            variant (nth u 19)]
        (is (contains? #{\8 \9 \a \b} variant))))))

;; ──────────────────────────────────────────────
;; UUID validation
;; ──────────────────────────────────────────────

(deftest test-uuid-validation
  (testing "a valid generated UUID passes validation"
    (dotimes [_ 10]
      (is (uuidka/valid? (uuidka/gen)))))

  (testing "known valid UUID v4 is valid"
    (is (true? (uuidka/valid? "550e8400-e29b-41d4-a716-446655440000"))))

  (testing "rejects empty string"
    (is (false? (uuidka/valid? ""))))

  (testing "rejects non-UUID strings"
    (is (false? (uuidka/valid? "not-a-uuid"))))

  (testing "rejects wrong version nibble (not 4)"
    (is (false? (uuidka/valid? "550e8400-e29b-31d4-a716-446655440000"))))

  (testing "rejects wrong variant nibble"
    (is (false? (uuidka/valid? "550e8400-e29b-41d4-c716-446655440000"))))

  (testing "rejects strings with invalid hex characters"
    (is (false? (uuidka/valid? "550e8400-e29b-41d4-a716-44665544000X"))))

  (testing "rejects ULID strings (cross-format contamination)"
    (is (false? (uuidka/valid? "01ARZ3NDEKTSV4RRFFQ69G5FAV")))))

;; ──────────────────────────────────────────────
;; encode / decode
;; ──────────────────────────────────────────────

(deftest test-uuid-encode
  (testing "encodes a byte array back into the original UUID"
    (dotimes [_ 10]
      (let [uid (uuidka/gen)
            _   (is (string? uid))
            ba  (uuidka/decode uid)
            _   (is (= 16 (count ba)))
            re  (uuidka/encode ba)]
        (is (= uid re)))))

  (testing "encode with non-16-byte array throws"
    (is (thrown? Exception (uuidka/encode (byte-array 8))))
    (is (thrown? Exception (uuidka/encode (byte-array 32))))
    (is (thrown? Exception (uuidka/encode (byte-array 0))))))

(deftest test-uuid-decode
  (testing "decode returns a 16-byte byte array"
    (let [uid (uuidka/gen)
          ba  (uuidka/decode uid)]
      (is (instance? (Class/forName "[B") ba))
      (is (= 16 (count ba)))))

  (testing "decode returns nil for invalid UUID strings"
    (is (nil? (uuidka/decode "")))
    (is (nil? (uuidka/decode "not-a-uuid")))
    (is (nil? (uuidka/decode "550e8400-e29b-41d4-a716-44665544000X"))))

  (testing "decode returns nil for ULID strings"
    (is (nil? (uuidka/decode "01ARZ3NDEKTSV4RRFFQ69G5FAV")))))

;; ──────────────────────────────────────────────
;; Round-trip (encode ∘ decode = id)
;; ──────────────────────────────────────────────

(deftest test-uuid-roundtrip
  (testing "decode then encode returns the original UUID (20 random values)"
    (dotimes [_ 20]
      (let [uid (uuidka/gen)
            ba  (uuidka/decode uid)]
        (is (= 36 (count uid)))
        (is (= 16 (count ba)))
        (is (= uid (uuidka/encode ba))))))

  (testing "encode of valid UUID bytes then decode returns original bytes"
    (dotimes [_ 20]
      (let [uid      (uuidka/gen)
            ba       (uuidka/decode uid)
            re-uid   (uuidka/encode ba)
            re-ba    (uuidka/decode re-uid)]
        (is (java.util.Arrays/equals ba re-ba))))))
