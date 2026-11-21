(ns identika.ksuid-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [identika.ksuid :as ksuid]))

(def ^:private base62-re #"^[0-9A-Za-z]{27}$")

;; Known KSUID from Segment's documentation/tests.
(def ^:private known "0ujtsYcgvSTl8PAuAdqWYSMnLOv")
(def ^:private known-ts 1507608047)
(def ^:private known-bytes
  [0x06 0x69 0xf7 0xef 0xb5 0xa1 0xcd 0x34 0xb5 0xf9 0x9d 0x11
   0x54 0xfb 0x68 0x53 0x34 0x5c 0x97 0x35])

(def ^:private zero-ksuid "000000000000000000000000000")
(def ^:private max-ksuid "aWgEPTl1tmebfsQzFP4bxwgy80V")
(def ^:private epoch 1400000000)
(def ^:private max-ts 5694967295)

(defn- ksuid-with-payload
  "A KSUID carrying `unix-seconds` as its timestamp and `payload` as its
  16-byte random component."
  [unix-seconds ^bytes payload]
  (let [ba (ksuid/decode (ksuid/gen unix-seconds))]
    (System/arraycopy payload 0 ba 4 16)
    (ksuid/encode ba)))

(defn- future-seconds
  "A timestamp far enough ahead of the wall clock that generation stays pinned
  to it, making counter behaviour deterministic."
  ^long []
  (+ (quot (System/currentTimeMillis) 1000) 120))

;; ──────────────────────────────────────────────
;; KSUID generation
;; ──────────────────────────────────────────────

(deftest test-ksuid-generation
  (testing "returns a string"
    (is (string? (ksuid/gen))))

  (testing "returns a 27-character Base62 string"
    (dotimes [_ 20]
      (let [k (ksuid/gen)]
        (is (= 27 (count k)))
        (is (re-matches base62-re k)))))

  (testing "produces unique values"
    (let [ids (repeatedly 1000 ksuid/gen)]
      (is (= (count ids) (count (distinct ids))))))

  (testing "generated KSUID carries the current time"
    (let [before (quot (System/currentTimeMillis) 1000)
          ts     (ksuid/timestamp (ksuid/gen))
          after  (quot (System/currentTimeMillis) 1000)]
      (is (<= before ts after))))

  (testing "KSUIDs are lexicographically sortable by creation time"
    (let [a  (ksuid/gen)
          ta (ksuid/timestamp a)]
      ;; Busy-wait past the current second so ordering is deterministic.
      (while (<= (quot (System/currentTimeMillis) 1000) ta))
      (let [b (ksuid/gen)]
        (is (neg? (compare a b))
            (str "Earlier KSUID " a " should sort before later " b)))))

  (testing "explicit timestamp is preserved"
    (dotimes [_ 20]
      (let [ts 1507608047]
        (is (= ts (ksuid/timestamp (ksuid/gen ts)))))))

  (testing "accepts the epoch and maximum timestamps"
    (is (= epoch (ksuid/timestamp (ksuid/gen epoch))))
    (is (= max-ts (ksuid/timestamp (ksuid/gen max-ts)))))

  (testing "rejects timestamps outside the 32-bit KSUID range"
    (is (thrown? Exception (ksuid/gen (dec epoch))))
    (is (thrown? Exception (ksuid/gen (inc max-ts))))))

(deftest test-ksuid-thread-safety
  (testing "concurrent generation produces unique values"
    (let [ids (vec (mapcat deref
                           (map (fn [_]
                                  (future (repeatedly 250 ksuid/gen)))
                                (range 8))))]
      (is (= 2000 (count ids)))
      (is (= (count ids) (count (distinct ids)))))))

;; ──────────────────────────────────────────────
;; KSUID validation
;; ──────────────────────────────────────────────

(deftest test-ksuid-validation
  (testing "generated KSUIDs are valid"
    (dotimes [_ 10]
      (is (ksuid/valid? (ksuid/gen)))))

  (testing "known vectors are valid"
    (is (true? (ksuid/valid? known)))
    (is (true? (ksuid/valid? zero-ksuid)))
    (is (true? (ksuid/valid? max-ksuid))))

  (testing "rejects empty and wrong-length strings"
    (is (false? (ksuid/valid? "")))
    (is (false? (ksuid/valid? "not-a-ksuid")))
    (is (false? (ksuid/valid? (subs known 0 26))))
    (is (false? (ksuid/valid? (str known "0")))))

  (testing "rejects invalid Base62 characters"
    (is (false? (ksuid/valid? (str (subs known 0 26) "!"))))
    (is (false? (ksuid/valid? (str "-" (subs known 1))))))

  (testing "rejects values that overflow 160 bits"
    (is (false? (ksuid/valid? (apply str (repeat 27 \z)))))
    (is (false? (ksuid/valid? "aWgEPTl1tmebfsQzFP4bxwgy80W")))
    (is (false? (ksuid/valid? (str "b" (apply str (repeat 26 \0)))))))

  (testing "rejects UUID and ULID strings (cross-format contamination)"
    (is (false? (ksuid/valid? "550e8400-e29b-41d4-a716-446655440000")))
    (is (false? (ksuid/valid? "01ARZ3NDEKTSV4RRFFQ69G5FAV")))))

;; ──────────────────────────────────────────────
;; Timestamp extraction
;; ──────────────────────────────────────────────

(deftest test-ksuid-timestamp
  (testing "extracts the known timestamp"
    (is (= known-ts (ksuid/timestamp known))))

  (testing "the all-zero KSUID reports the KSUID epoch"
    (is (= epoch (ksuid/timestamp zero-ksuid))))

  (testing "the maximum KSUID reports the maximum timestamp"
    (is (= max-ts (ksuid/timestamp max-ksuid))))

  (testing "returns nil for invalid input"
    (is (nil? (ksuid/timestamp "")))
    (is (nil? (ksuid/timestamp "not-a-ksuid")))
    (is (nil? (ksuid/timestamp "550e8400-e29b-41d4-a716-446655440000")))
    (is (nil? (ksuid/timestamp "01ARZ3NDEKTSV4RRFFQ69G5FAV")))))

;; ──────────────────────────────────────────────
;; encode / decode
;; ──────────────────────────────────────────────

(deftest test-ksuid-encode
  (testing "all-zero bytes encode to the all-zero KSUID"
    (is (= zero-ksuid (ksuid/encode (byte-array 20)))))

  (testing "all-0xff bytes encode to the maximum KSUID"
    (is (= max-ksuid (ksuid/encode (byte-array 20 (repeat 0xff))))))

  (testing "sequential bytes encode to the expected string"
    (is (= "0029sS3yqsuDyR1vGFhTuuRAq1b"
           (ksuid/encode (byte-array 20 (range 20))))))

  (testing "known byte sequence encodes to the known KSUID"
    (is (= known (ksuid/encode (byte-array (map unchecked-byte known-bytes))))))

  (testing "encode with a non-20-byte array throws"
    (is (thrown? Exception (ksuid/encode (byte-array 19))))
    (is (thrown? Exception (ksuid/encode (byte-array 21))))
    (is (thrown? Exception (ksuid/encode (byte-array 0))))))

(deftest test-ksuid-decode
  (testing "decode returns a 20-byte byte array"
    (let [ba (ksuid/decode known)]
      (is (instance? (Class/forName "[B") ba))
      (is (= 20 (count ba)))))

  (testing "decode returns the expected bytes"
    (is (= known-bytes (mapv #(bit-and % 0xFF) (ksuid/decode known)))))

  (testing "decode returns nil for invalid input"
    (is (nil? (ksuid/decode "")))
    (is (nil? (ksuid/decode "not-a-ksuid")))
    (is (nil? (ksuid/decode (apply str (repeat 27 \z)))))
    (is (nil? (ksuid/decode "550e8400-e29b-41d4-a716-446655440000")))))

(deftest test-ksuid-roundtrip
  (testing "decode then encode returns the original KSUID (20 random values)"
    (dotimes [_ 20]
      (let [k (ksuid/gen)]
        (is (= 27 (count k)))
        (is (= k (ksuid/encode (ksuid/decode k)))))))

  (testing "encode then decode returns the original bytes (20 random arrays)"
    (dotimes [_ 20]
      (let [ba (byte-array 20 (repeatedly #(rand-int 256)))]
        (is (java.util.Arrays/equals ba (ksuid/decode (ksuid/encode ba)))))))

  (testing "the known KSUID round-trips"
    (is (= known (ksuid/encode (ksuid/decode known))))))

;; ──────────────────────────────────────────────
;; next-ksuid
;; ──────────────────────────────────────────────

(deftest test-ksuid-next
  (testing "next-ksuid is lexicographically greater than the original"
    (dotimes [_ 20]
      (let [k (ksuid/gen)]
        (is (pos? (compare (ksuid/next-ksuid k) k))))))

  (testing "next-ksuid increments the last Base62 digit"
    (is (= "000000000000000000000000001"
           (ksuid/next-ksuid zero-ksuid)))
    (is (= "00000000000000000000000000a"
           (ksuid/next-ksuid "00000000000000000000000000Z"))))

  (testing "next-ksuid carries across Base62 digits"
    (is (= "000000000000000000000000010"
           (ksuid/next-ksuid "00000000000000000000000000z"))))

  (testing "next-ksuid of the known KSUID"
    (is (= "0ujtsYcgvSTl8PAuAdqWYSMnLOw"
           (ksuid/next-ksuid known))))

  (testing "next-ksuid of the maximum KSUID is nil"
    (is (nil? (ksuid/next-ksuid max-ksuid))))

  (testing "next-ksuid returns nil for invalid input"
    (is (nil? (ksuid/next-ksuid "")))
    (is (nil? (ksuid/next-ksuid "not-a-ksuid"))))

  (testing "chained next-ksuid calls produce strictly increasing values"
    (let [ids (take 50 (iterate ksuid/next-ksuid zero-ksuid))]
      (is (every? neg? (map compare ids (rest ids)))))))

;; ──────────────────────────────────────────────
;; monotonic
;; ──────────────────────────────────────────────

(deftest test-ksuid-monotonic
  (testing "returns a valid 27-character KSUID"
    (let [state (atom nil)]
      (dotimes [_ 10]
        (let [k (ksuid/monotonic state)]
          (is (= 27 (count k)))
          (is (ksuid/valid? k))))))

  (testing "updates the atom after each call"
    (let [state (atom nil)
          k1    (ksuid/monotonic state)]
      (is (= k1 @state))
      (let [k2 (ksuid/monotonic state)]
        (is (= k2 @state))
        (is (not= k1 k2)))))

  (testing "returns strictly increasing values (100 calls)"
    (let [state (atom nil)
          ids   (repeatedly 100 #(ksuid/monotonic state))]
      (is (= 100 (count (distinct ids))))
      (is (every? neg? (map compare ids (rest ids))))))

  (testing "two independent atoms produce independent sequences"
    (let [a  (atom nil)
          b  (atom nil)
          as (repeatedly 20 #(ksuid/monotonic a))
          bs (repeatedly 20 #(ksuid/monotonic b))]
      (is (every? neg? (map compare as (rest as))))
      (is (every? neg? (map compare bs (rest bs))))))

  (testing "ignores an atom holding a non-KSUID value"
    (let [state (atom "garbage")
          k     (ksuid/monotonic state)]
      (is (ksuid/valid? k))
      (is (= k @state))))

  (testing "never regresses when the wall clock moves backwards"
    (let [ahead (+ (quot (System/currentTimeMillis) 1000) 120)
          prev  (ksuid/gen ahead)
          state (atom prev)
          k     (ksuid/monotonic state)]
      (is (pos? (compare k prev)))
      (is (>= (ksuid/timestamp k) ahead)))))

(deftest test-ksuid-monotonic-counter
  (testing "increments the previous KSUID within the same second"
    (let [ahead (future-seconds)
          prev  (ksuid/gen ahead)
          state (atom prev)
          k     (ksuid/monotonic state)]
      (is (= (ksuid/next-ksuid prev) k))
      (is (= ahead (ksuid/timestamp k)))))

  (testing "a full payload carries into the timestamp"
    (let [ahead (future-seconds)
          prev  (ksuid-with-payload ahead (byte-array 16 (repeat 0xff)))
          state (atom prev)
          k     (ksuid/monotonic state)]
      (is (= (ksuid-with-payload (inc ahead) (byte-array 16)) k))
      (is (= (inc ahead) (ksuid/timestamp k)))
      (is (pos? (compare k prev)))))

  (testing "counter increments stay valid and ordered across repeated wraps"
    (let [ahead (future-seconds)
          ;; Payload just below all-ones, so the first increments wrap quickly.
          prev  (ksuid-with-payload ahead
                                    (byte-array (concat (repeat 15 0xff) [0xfe])))
          state (atom prev)
          ids   (repeatedly 1000 #(ksuid/monotonic state))]
      (is (every? ksuid/valid? ids))
      (is (every? neg? (map compare ids (rest ids)))))))
