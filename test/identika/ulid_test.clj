(ns identika.ulid-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [identika.ulid :as ulid]))

;; ──────────────────────────────────────────────
;; ULID string generation
;; ──────────────────────────────────────────────

(deftest test-ulid-generation
  (testing "returns a string"
    (is (string? (ulid/gen))))

  (testing "returns a different string on each call"
    (is (not= (ulid/gen) (ulid/gen))))

  (testing "returns a 26-character string"
    (dotimes [_ 10]
      (is (= 26 (count (ulid/gen))))))

  (testing "contains only Crockford Base32 characters"
    (let [valid-chars (set "0123456789ABCDEFGHJKMNPQRSTVWXYZ")]
      (dotimes [_ 10]
        (let [u (ulid/gen)]
          (is (every? valid-chars u) (str "Invalid chars in " u))))))

  (testing "ULIDs are lexicographically sortable by time"
    (let [a (ulid/gen)
          _ (Thread/sleep 2)
          b (ulid/gen)]
      (is (neg? (compare a b))
          (str "Earlier ULID " a " should sort before later " b))))

  (testing "ULIDs with the same timestamp are unique"
    (let [ids (repeatedly 1000 #(ulid/gen 1781290640998))]
      (is (= (count ids) (count (distinct ids)))
          "All 1000 generated ULIDs should be unique")))

  (testing "ULID generated at a specific time preserves that timestamp"
    (let [ts   1781290640998
          ulid (ulid/gen ts)]
      (is (= ts (ulid/timestamp ulid)))))

  (testing "Consecutive ULIDs are unique"
    (let [ids (repeatedly 1000 ulid/gen)]
      (is (= (count ids) (count (distinct ids)))
          "All 1000 generated ULIDs should be unique"))))

;; ──────────────────────────────────────────────
;; ULID validation
;; ──────────────────────────────────────────────

(deftest test-ulid-validation
  (testing "a valid generated ULID passes validation"
    (dotimes [_ 10]
      (is (ulid/valid? (ulid/gen)))))

  (testing "empty string is rejected"
    (is (not (ulid/valid? ""))))

  (testing "too-long string is rejected"
    (let [valid-ulid "01KVWFN1PF8N3GTDD2J98P3GXK"]
      (is (ulid/valid? valid-ulid))
      (is (not (ulid/valid? (str valid-ulid "A"))))))

  (testing "too-short string is rejected"
    (let [valid-ulid "01KVWFN1PF8N3GTDD2J98P3GXK"]
      (is (ulid/valid? valid-ulid))
      (is (not (ulid/valid? (subs valid-ulid 1))))))

  (testing "ambiguous characters (I, L, O, U) are rejected"
    (are [ulid-str] (not (ulid/valid? ulid-str))
      "0IKVWFN1PF8N3GTDD2J98P3GXK"   ;; I
      "0LKVWFN1PF8N3GTDD2J98P3GXK"   ;; L
      "0OKVWFN1PF8N3GTDD2J98P3GXK"   ;; O
      "0UKVWFN1PF8N3GTDD2J98P3GXK")) ;; U

  (testing "special characters are rejected"
    (are [ulid-str] (not (ulid/valid? ulid-str))
      "01KVWFN1PF8N3GTDD2J98P3GX&"
      "01KVWFN1PF8N3GTDD2J98P3GX@"
      "01KVWFN1PF8N3GTDD2J98P3GX!"))

  (testing "UUID strings are rejected (cross-format contamination)"
    (let [uuid-str "550e8400-e29b-41d4-a716-446655440000"]
      (is (not (ulid/valid? uuid-str))))))

;; ──────────────────────────────────────────────
;; Timestamp extraction
;; ──────────────────────────────────────────────

(deftest test-ulid-timestamp
  (testing "extracts the correct millisecond timestamp"
    (let [ts   1781290640998
          ulid (ulid/gen ts)]
      (is (= ts (ulid/timestamp ulid)))))

  (testing "extracts zero timestamp correctly"
    (let [ulid (ulid/gen 0)]
      (is (zero? (ulid/timestamp ulid)))))

  (testing "returns nil for invalid ULID string"
    (is (nil? (ulid/timestamp "")))
    (is (nil? (ulid/timestamp "not-a-ulid")))))

;; ──────────────────────────────────────────────
;; encode / decode
;; ──────────────────────────────────────────────

(deftest test-encode
  (testing "all-zero byte array encodes to all-zero ULID"
    (let [zeros (byte-array 16)]
      (is (= "00000000000000000000000000" (ulid/encode zeros)))))

  (testing "encode always returns a 26-character string"
    (dotimes [_ 10]
      (let [ba (byte-array 16 (repeatedly #(rand-int 256)))]
        (is (= 26 (count (ulid/encode ba)))))))

  (testing "encode with non-16-byte array throws"
    (is (thrown? Exception (ulid/encode (byte-array 8))))
    (is (thrown? Exception (ulid/encode (byte-array 32))))
    (is (thrown? Exception (ulid/encode (byte-array 0))))))

(deftest test-decode
  (testing "decode returns a 16-byte byte array"
    (let [ulid-str (ulid/gen)
          ba (ulid/decode ulid-str)]
      (is (instance? (Class/forName "[B") ba))
      (is (= 16 (count ba)))))

  (testing "decode returns nil for invalid input"
    (is (nil? (ulid/decode "")))
    (is (nil? (ulid/decode "not-a-ulid")))
    (is (nil? (ulid/decode "01KVWFN1PF8N3GTDD2J98P3GX&"))))

  (testing "decode returns nil for UUID strings"
    (is (nil? (ulid/decode "550e8400-e29b-41d4-a716-446655440000")))))

(deftest test-encode-decode-roundtrip
  (testing "decode(known-ulid) then encode back yields the original"
    (let [known "01ARZ3NDEKTSV4RRFFQ69G5FAV"
          ba    (ulid/decode known)]
      (is (= known (ulid/encode ba)))))

  (testing "round-trip for 20 random ULIDs"
    (dotimes [_ 20]
      (let [ulid-str (ulid/gen)
            ba       (ulid/decode ulid-str)]
        (is (= 16 (count ba)))
        (is (= ulid-str (ulid/encode ba))))))

  (testing "round-trip for 20 random byte arrays"
    (dotimes [_ 20]
      (let [ba       (byte-array 16 (repeatedly #(rand-int 256)))
            ulid-str (ulid/encode ba)
            decoded  (ulid/decode ulid-str)]
        (is (= 26 (count ulid-str)))
        (is (java.util.Arrays/equals ba decoded))))))

;; ──────────────────────────────────────────────
;; next-ulid
;; ──────────────────────────────────────────────

(deftest test-next-ulid
  (testing "next-ulid is lexicographically greater than the original"
    (dotimes [_ 20]
      (let [ulid (ulid/gen)]
        (is (pos? (compare (ulid/next-ulid ulid) ulid))))))

  (testing "next-ulid returns a 26-character string"
    (dotimes [_ 10]
      (let [ulid (ulid/gen)]
        (is (= 26 (count (ulid/next-ulid ulid)))))))

  (testing "next-ulid returns nil for invalid input"
    (is (nil? (ulid/next-ulid "")))
    (is (nil? (ulid/next-ulid "not-a-ulid")))
    (is (nil? (ulid/next-ulid "01KVWFN1PF8N3GTDD2J98P3GX&"))))

  (testing "next-ulid of all-zero ULID is 00000000000000000000000001"
    (is (= "00000000000000000000000001"
           (ulid/next-ulid "00000000000000000000000000"))))

  (testing "next-ulid of ...00001 is ...00002"
    (is (= "00000000000000000000000002"
           (ulid/next-ulid "00000000000000000000000001"))))

  (testing "next-ulid of ...0000V (V=27 decimal) is ...0000W (W=28)"
    (is (= "0000000000000000000000000W"
           (ulid/next-ulid "0000000000000000000000000V"))))

  (testing "next-ulid of ...0000Z (Z=31, max) carries into ...00010"
    (is (= "00000000000000000000000010"
           (ulid/next-ulid "0000000000000000000000000Z"))))

  (testing "Chained next-ulid calls produce strictly increasing values"
    (let [start "00000000000000000000000000"
          ids   (take 50 (iterate ulid/next-ulid start))]
      (is (every? neg? (map compare ids (rest ids))))
      (is (apply not= ids))))

  (testing "next-ulid carries into the next character when lower bits overflow"
    ;; 16 Z's (max Crockford Base32 = 31 = 0b11111) forces carry into timestamp
    (let [base   "0000000000"
          maxed  (str base (apply str (repeat 16 \Z)))
          nexted (ulid/next-ulid maxed)]
      (is (not (nil? nexted)))
      (is (= 26 (count nexted)))
      (is (pos? (compare nexted maxed)))
      (is (= \1 (nth nexted 9)))
      (is (every? #{\0} (subs nexted 10))))))

;; ──────────────────────────────────────────────
;; monotonic
;; ──────────────────────────────────────────────

(deftest test-monotonic-ulid
  (testing "returns a 26-character string"
    (let [state (atom nil)]
      (dotimes [_ 10]
        (is (= 26 (count (ulid/monotonic state)))))))

  (testing "updates the atom after each call"
    (let [state (atom nil)
          ulid1 (ulid/monotonic state)]
      (is (= ulid1 @state))
      (let [ulid2 (ulid/monotonic state)]
        (is (= ulid2 @state))
        (is (not= ulid1 ulid2)))))

  (testing "returns strictly increasing values (100 calls)"
    (let [state (atom nil)
          ids   (repeatedly 100 #(ulid/monotonic state))]
      (is (every? neg? (map compare ids (rest ids))))
      (is (= 100 (count (distinct ids))))))

  (testing "two independent atoms produce independent sequences"
    (let [a  (atom nil)
          b  (atom nil)
          as (repeatedly 20 #(ulid/monotonic a))
          bs (repeatedly 20 #(ulid/monotonic b))]
      (is (every? neg? (map compare as (rest as))))
      (is (every? neg? (map compare bs (rest bs)))))))
