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

;; ──────────────────────────────────────────────
;; encode / decode
;; ──────────────────────────────────────────────

(deftest test-encode
  (testing "All-zero byte array encodes to all-zero ULID"
    (let [zeros (byte-array 16 (repeat 0))]
      (is (= "00000000000000000000000000" (ulid/encode zeros)))))

  (testing "encode always returns a 26-character string"
    (dotimes [_ 10]
      (let [ba (byte-array 16 (repeatedly #(rand-int 256)))]
        (is (= 26 (count (ulid/encode ba)))))))

  (testing "A generated ULID decodes and encodes back correctly"
    (let [ulid-str "01ARZ3NDEKTSV4RRFFQ69G5FAV"
          bi (ulid/decode ulid-str)
          raw (.toByteArray bi)
          n (count raw)
          ba16 (byte-array 16)]
      (if (> n 16)
        (System/arraycopy raw 1 ba16 0 16)
        (System/arraycopy raw 0 ba16 (- 16 n) n))
      (is (= ulid-str (ulid/encode ba16)))))

  (testing "Round-trip: encode of decode result yields original ULID"
    (dotimes [_ 20]
      (let [ulid-str (ulid/gen)
            bi (ulid/decode ulid-str)
            raw (.toByteArray bi)
            n (count raw)
            ;; Convert BigInteger to exactly 16 bytes big-endian.
            ;; toByteArray strips leading zero bytes;
            ;; if bit 127 is set it prepends a 0x00 sign byte (17 bytes total).
            ba16 (byte-array 16)]
        (if (> n 16)
          (System/arraycopy raw 1 ba16 0 16)  ;; drop the sign byte
          (System/arraycopy raw 0 ba16 (- 16 n) n))  ;; pad on the left
        (is (= ulid-str (ulid/encode ba16)))))))

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

  (testing "next-ulid of ...0000V (V=27) is ...0000W (W=28)"
    (is (= "0000000000000000000000000W"
           (ulid/next-ulid "0000000000000000000000000V"))))

  (testing "next-ulid of ...0000Z (Z=31) carries into ...00010"
    (is (= "00000000000000000000000010"
           (ulid/next-ulid "0000000000000000000000000Z"))))

  (testing "Chained next-ulid calls produce strictly increasing values"
    (let [start "00000000000000000000000000"
          ids (take 50 (iterate ulid/next-ulid start))]
      (is (every? neg? (map compare ids (rest ids))))
      (is (apply not= ids))))

  (testing "next-ulid carries into the next character when lower bits overflow"
    ;; Construct a ULID with all 1s in the lower 80 bits to force carry.
    ;; 'Z' = 31 = 0b11111 (max Crockford Base32 value), so 16 Z's fills
    ;; the random component with all 1s, forcing overflow into the timestamp.
    (let [base "0000000000"
          rand-all-zs (apply str (repeat 16 \Z))
          maxed (str base rand-all-zs)
          nexted (ulid/next-ulid maxed)]
      (is (not (nil? nexted)))
      (is (= 26 (count nexted)))
      (is (pos? (compare nexted maxed)))
      ;; Carry propagates through all 16 random chars into char 9 (last timestamp char)
      (is (= \1 (nth nexted 9)))
      (is (every? #{\0} (subs nexted 10))))))

;; ──────────────────────────────────────────────
;; monotonic
;; ──────────────────────────────────────────────

(deftest test-monotonic-ulid
  (testing "monotonic returns a 26-character string"
    (let [gen (atom nil)]
      (dotimes [_ 10]
        (is (= 26 (count (ulid/monotonic gen)))))))

  (testing "monotonic updates the atom after each call"
    (let [gen (atom nil)
          ulid1 (ulid/monotonic gen)]
      (is (= ulid1 @gen))
      (let [ulid2 (ulid/monotonic gen)]
        (is (= ulid2 @gen))
        (is (not= ulid1 ulid2)))))

  (testing "monotonic returns strictly increasing values"
    (let [gen (atom nil)
          ids (repeatedly 100 #(ulid/monotonic gen))]
      (is (every? neg? (map compare ids (rest ids))))
      (is (= 100 (count (distinct ids))))))

  (testing "Two independent atoms produce independent sequences"
    (let [a (atom nil)
          b (atom nil)
          as (repeatedly 20 #(ulid/monotonic a))
          bs (repeatedly 20 #(ulid/monotonic b))]
      (is (every? neg? (map compare as (rest as))))
      (is (every? neg? (map compare bs (rest bs)))))))
