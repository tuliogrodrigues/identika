(ns identika.uuid7-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [identika.uuid7 :as uuid7]))

(def ^:private uuid7-re
  #"^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

;; Canonical example from RFC 9562 §5.7.
(def ^:private rfc-example "017f22e2-79b0-7cc3-98c4-dc0c0c07398f")

(def ^:private rfc-example-ms 1645557742000)

;; The 23 characters of a UUID v7 that follow the 48-bit timestamp field: a
;; hyphen, the version nibble with rand_a, a hyphen, the variant bits with the
;; top of rand_b, a hyphen, and the remaining 56 bits of rand_b.
(def ^:private tail-lowest      "-7000-8000-000000000000")
(def ^:private tail-rand-b-max  "-7000-bfff-ffffffffffff")
(def ^:private tail-rand-a-max  "-70ff-bfff-ffffffffffff")
(def ^:private tail-all-ones    "-7fff-bfff-ffffffffffff")
(def ^:private tail-rand-b-near "-7000-b7ff-ffffffffffff")

(defn- uuid7-with-tail
  "A UUID v7 carrying `millis` as its timestamp, with the 23 trailing
  characters described above replaced by `tail`."
  ^String [millis tail]
  (str (subs (uuid7/gen millis) 0 13) tail))

(defn- future-ms
  "A timestamp far enough ahead of the wall clock that generation stays pinned
  to it, making counter behaviour deterministic."
  ^long []
  (+ (System/currentTimeMillis) 120000))

;; ──────────────────────────────────────────────
;; UUID v7 generation
;; ──────────────────────────────────────────────

(deftest test-uuid7-generation
  (testing "returns a string"
    (is (string? (uuid7/gen))))

  (testing "returns a valid UUID v7 format (36 chars, hex-hyphenated)"
    (dotimes [_ 20]
      (is (re-matches uuid7-re (uuid7/gen)))))

  (testing "produces unique values"
    (let [ids (repeatedly 1000 uuid7/gen)]
      (is (= (count ids) (count (distinct ids))))))

  (testing "version nibble is always 7"
    (dotimes [_ 20]
      (let [u (uuid7/gen)]
        (is (= \7 (nth u 14))))))

  (testing "variant nibble is always 8, 9, a, or b"
    (dotimes [_ 20]
      (let [u       (uuid7/gen)
            variant (nth u 19)]
        (is (contains? #{\8 \9 \a \b} variant))))))

(deftest test-uuid7-sortable
  (testing "UUIDs are lexicographically sortable by creation time"
    (let [a  (uuid7/gen)
          ta (uuid7/timestamp a)]
      ;; Busy-wait past the current millisecond so ordering is deterministic.
      (while (<= (System/currentTimeMillis) ta))
      (let [b (uuid7/gen)]
        (is (neg? (compare a b))
            (str "Earlier UUID " a " should sort before later " b)))))

  (testing "timestamps never regress across a burst of generations"
    (let [ts (map uuid7/timestamp (repeatedly 500 uuid7/gen))]
      (is (apply <= ts))))

  (testing "explicitly timestamped UUIDs sort lexicographically"
    (let [base 1781290640000
          ids  (mapv #(uuid7/gen (+ base (* 1000 %))) (range 200))]
      (is (= ids (sort ids)))
      (is (= (sort-by uuid7/timestamp ids) ids))))

  (testing "sorting a shuffled collection recovers timestamp order"
    (let [base    1781290640000
          ordered (mapv #(uuid7/gen (+ base (* 1000 %))) (range 200))
          ids     (shuffle ordered)]
      (is (= ordered (vec (sort ids))))
      (is (= ordered (vec (sort-by uuid7/timestamp ids)))))))

(deftest test-uuid7-thread-safety
  (testing "concurrent generation produces unique values"
    (let [ids (vec (mapcat deref
                          (map (fn [_]
                                 (future (repeatedly 250 uuid7/gen)))
                               (range 8))))]
      (is (= 2000 (count ids)))
      (is (= (count ids) (count (distinct ids)))))))

;; ──────────────────────────────────────────────
;; UUID validation
;; ──────────────────────────────────────────────

(deftest test-uuid7-validation
  (testing "a valid generated UUID passes validation"
    (dotimes [_ 10]
      (is (uuid7/valid? (uuid7/gen)))))

  (testing "known valid UUID v7 is valid"
    (is (true? (uuid7/valid? rfc-example))))

  (testing "accepts uppercase hex"
    (is (true? (uuid7/valid? (str/upper-case rfc-example)))))

  (testing "rejects empty string"
    (is (false? (uuid7/valid? ""))))

  (testing "rejects non-UUID strings"
    (is (false? (uuid7/valid? "not-a-uuid"))))

  (testing "rejects wrong version nibble (not 7)"
    (is (false? (uuid7/valid? "0190f0e2-3b9a-4c4d-9e5f-8a1b2c3d4e5f"))))

  (testing "rejects wrong variant nibble"
    (is (false? (uuid7/valid? "0190f0e2-3b9a-7c4d-de5f-8a1b2c3d4e5f"))))

  (testing "rejects strings with invalid hex characters"
    (is (false? (uuid7/valid? "0190f0e2-3b9a-7c4d-9e5f-8a1b2c3d4e5X"))))

  (testing "rejects truncated and over-long strings"
    (is (false? (uuid7/valid? (subs rfc-example 0 35))))
    (is (false? (uuid7/valid? (str rfc-example "0")))))

  (testing "rejects UUID v4 strings (cross-format contamination)"
    (is (false? (uuid7/valid? "550e8400-e29b-41d4-a716-446655440000"))))

  (testing "rejects ULID strings (cross-format contamination)"
    (is (false? (uuid7/valid? "01ARZ3NDEKTSV4RRFFQ69G5FAV")))))

;; ──────────────────────────────────────────────
;; Timestamp extraction
;; ──────────────────────────────────────────────

(deftest test-uuid7-timestamp
  (testing "extracts the millisecond timestamp of a generated UUID"
    (dotimes [_ 20]
      (let [ts 1781290640998
            u  (uuid7/gen ts)]
        (is (= ts (uuid7/timestamp u))))))

  (testing "extracts the timestamp of the RFC 9562 example"
    (is (= rfc-example-ms (uuid7/timestamp rfc-example))))

  (testing "extracts zero timestamp correctly"
    (is (zero? (uuid7/timestamp (uuid7/gen 0)))))

  (testing "extracts the maximum 48-bit timestamp correctly"
    (let [max-ms 281474976710655]
      (is (= max-ms (uuid7/timestamp (uuid7/gen max-ms))))))

  (testing "generated UUID carries the current system time"
    (let [before (System/currentTimeMillis)
          ts     (uuid7/timestamp (uuid7/gen))
          after  (System/currentTimeMillis)]
      (is (<= before ts after))))

  (testing "returns nil for invalid UUID strings"
    (is (nil? (uuid7/timestamp "")))
    (is (nil? (uuid7/timestamp "not-a-uuid")))
    (is (nil? (uuid7/timestamp "550e8400-e29b-41d4-a716-446655440000")))
    (is (nil? (uuid7/timestamp "01ARZ3NDEKTSV4RRFFQ69G5FAV")))))

(deftest test-uuid7-timestamp-bounds
  (testing "gen rejects timestamps outside the 48-bit field"
    (is (thrown? Exception (uuid7/gen -1)))
    (is (thrown? Exception (uuid7/gen 281474976710656))))

  (testing "gen accepts the boundary values"
    (is (uuid7/valid? (uuid7/gen 0)))
    (is (uuid7/valid? (uuid7/gen 281474976710655)))))

;; ──────────────────────────────────────────────
;; encode / decode
;; ──────────────────────────────────────────────

(deftest test-uuid7-encode
  (testing "encodes a byte array back into the original UUID"
    (dotimes [_ 10]
      (let [uid (uuid7/gen)
            ba  (uuid7/decode uid)
            _   (is (= 16 (count ba)))
            re  (uuid7/encode ba)]
        (is (= uid re)))))

  (testing "encode of the RFC 9562 example bytes round-trips"
    (is (= rfc-example (uuid7/encode (uuid7/decode rfc-example)))))

  (testing "sequential byte array encodes to the expected hex"
    (is (= "00010203-0405-0607-0809-0a0b0c0d0e0f"
           (uuid7/encode (byte-array 16 (range 16))))))

  (testing "encode with non-16-byte array throws"
    (is (thrown? Exception (uuid7/encode (byte-array 8))))
    (is (thrown? Exception (uuid7/encode (byte-array 32))))
    (is (thrown? Exception (uuid7/encode (byte-array 0))))))

(deftest test-uuid7-decode
  (testing "decode returns a 16-byte byte array"
    (let [uid (uuid7/gen)
          ba  (uuid7/decode uid)]
      (is (instance? (Class/forName "[B") ba))
      (is (= 16 (count ba)))))

  (testing "decode returns nil for invalid UUID strings"
    (is (nil? (uuid7/decode "")))
    (is (nil? (uuid7/decode "not-a-uuid")))
    (is (nil? (uuid7/decode "0190f0e2-3b9a-7c4d-9e5f-8a1b2c3d4e5X"))))

  (testing "decode returns nil for UUID v4 and ULID strings"
    (is (nil? (uuid7/decode "550e8400-e29b-41d4-a716-446655440000")))
    (is (nil? (uuid7/decode "01ARZ3NDEKTSV4RRFFQ69G5FAV")))))

(deftest test-uuid7-roundtrip
  (testing "decode then encode returns the original UUID (20 random values)"
    (dotimes [_ 20]
      (let [uid (uuid7/gen)
            ba  (uuid7/decode uid)]
        (is (= 36 (count uid)))
        (is (= uid (uuid7/encode ba))))))

  (testing "encode of valid UUID bytes then decode returns original bytes"
    (dotimes [_ 20]
      (let [uid   (uuid7/gen)
            ba    (uuid7/decode uid)
            re-ba (uuid7/decode (uuid7/encode ba))]
        (is (java.util.Arrays/equals ba re-ba))))))

;; ──────────────────────────────────────────────
;; monotonic
;; ──────────────────────────────────────────────

(deftest test-uuid7-monotonic
  (testing "returns a valid 36-character UUID"
    (let [state (atom nil)]
      (dotimes [_ 10]
        (let [u (uuid7/monotonic state)]
          (is (= 36 (count u)))
          (is (uuid7/valid? u))))))

  (testing "updates the atom after each call"
    (let [state (atom nil)
          u1    (uuid7/monotonic state)]
      (is (= u1 @state))
      (let [u2 (uuid7/monotonic state)]
        (is (= u2 @state))
        (is (not= u1 u2)))))

  (testing "returns strictly increasing values (100 calls)"
    (let [state (atom nil)
          ids   (repeatedly 100 #(uuid7/monotonic state))]
      (is (= 100 (count (distinct ids))))
      (is (every? neg? (map compare ids (rest ids))))))

  (testing "stays valid and strictly increasing over a long burst"
    (let [state (atom nil)
          ids   (repeatedly 5000 #(uuid7/monotonic state))]
      (is (every? uuid7/valid? ids))
      (is (every? neg? (map compare ids (rest ids))))))

  (testing "two independent atoms produce independent sequences"
    (let [a  (atom nil)
          b  (atom nil)
          as (repeatedly 50 #(uuid7/monotonic a))
          bs (repeatedly 50 #(uuid7/monotonic b))]
      (is (every? neg? (map compare as (rest as))))
      (is (every? neg? (map compare bs (rest bs))))))

  (testing "ignores an atom holding a non-UUID value"
    (let [state (atom "garbage")
          u     (uuid7/monotonic state)]
      (is (uuid7/valid? u))
      (is (= u @state))))

  (testing "never regresses when the wall clock moves backwards"
    (let [ahead (+ (System/currentTimeMillis) 60000)
          prev  (uuid7/gen ahead)
          state (atom prev)
          u     (uuid7/monotonic state)]
      (is (pos? (compare u prev)))
      (is (>= (uuid7/timestamp u) ahead)))))

(deftest test-uuid7-monotonic-counter
  (testing "hand-crafted payloads are well-formed UUID v7s"
    (doseq [tail [tail-lowest tail-rand-b-max tail-rand-a-max
                  tail-all-ones tail-rand-b-near]]
      (is (uuid7/valid? (uuid7-with-tail 0 tail))
          (str "expected a valid 36-char UUID for tail " tail))))

  (testing "increments the payload in place within a single millisecond"
    (let [far   (future-ms)
          state (atom (uuid7-with-tail far tail-lowest))
          u1    (uuid7/monotonic state)
          u2    (uuid7/monotonic state)]
      (is (= (uuid7-with-tail far "-7000-8000-000000000001") u1))
      (is (= (uuid7-with-tail far "-7000-8000-000000000002") u2))))

  (testing "rand_b carries into rand_a, preserving version and variant bits"
    (let [far   (future-ms)
          state (atom (uuid7-with-tail far tail-rand-b-max))]
      (is (= (uuid7-with-tail far "-7001-8000-000000000000")
             (uuid7/monotonic state)))))

  (testing "rand_a carries into its own high nibble, preserving the version nibble"
    (let [far   (future-ms)
          state (atom (uuid7-with-tail far tail-rand-a-max))]
      (is (= (uuid7-with-tail far "-7100-8000-000000000000")
             (uuid7/monotonic state)))))

  (testing "an exhausted payload advances the timestamp by one millisecond"
    (let [far   (future-ms)
          prev  (uuid7-with-tail far tail-all-ones)
          state (atom prev)
          u     (uuid7/monotonic state)]
      (is (uuid7/valid? u))
      (is (= (+ far 1) (uuid7/timestamp u)))
      (is (pos? (compare u prev)))))

  (testing "counter increments stay valid and ordered across repeated rand_b wraps"
    (let [far   (future-ms)
          state (atom (uuid7-with-tail far tail-rand-b-near))
          ids   (repeatedly 1000 #(uuid7/monotonic state))]
      (is (every? uuid7/valid? ids))
      (is (every? #(= far (uuid7/timestamp %)) ids))
      (is (every? neg? (map compare ids (rest ids)))))))
