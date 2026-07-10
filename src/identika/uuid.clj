(ns identika.uuid
  "UUID v4 generation, parsing, and validation.

  Implements RFC 4122 UUID v4 (random):
  - 36-char hex-hyphenated string (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
  - 122 bits of randomness, version 4 (0100), variant 1 (10xx)
  - Pure Clojure, zero dependencies beyond java.security.SecureRandom"
  (:require [identika.protocols :as pct])
  (:import [java.security SecureRandom]
           [java.math BigInteger]))

(defonce ^:private uuid-rng (SecureRandom.))

(def ^:private hex-chars
  (char-array "0123456789abcdef"))

(defn- random-uuid-bytes
  "Generate 16 random bytes for a UUID v4, setting version and variant bits."
  ^bytes []
  (let [ba (byte-array 16)]
    (.nextBytes uuid-rng ba)
    ;; Set version 4 (0100) in byte 6, upper nibble
    (aset-byte ba 6 (unchecked-byte (bit-or (bit-and (aget ba 6) 0x0f) 0x40)))
    ;; Set variant 10xx in byte 8, upper nibble
    (aset-byte ba 8 (unchecked-byte (bit-or (bit-and (aget ba 8) 0x3f) 0x80)))
    ba))

(defn- bytes->hex-str
  "Format a 16-byte array as a UUID hex string with hyphens.
  Positions: 8-4-4-4-12 = 36 chars total."
  ^String [^bytes ba]
  (let [sb (StringBuilder. 36)]
    (dotimes [i 16]
      (when (or (= i 4) (= i 6) (= i 8) (= i 10))
        (.append sb \-))
      (.append sb (nth hex-chars (bit-and (bit-shift-right (aget ba i) 4) 0x0f)))
      (.append sb (nth hex-chars (bit-and (aget ba i) 0x0f))))
    (.toString sb)))

(defn- hex-str->bigint
  "Parse a UUID hex string (with or without hyphens) into a BigInteger."
  ^BigInteger [^String s]
  (BigInteger. (.toString (reduce (fn [^StringBuilder sb c]
                                    (if (= c \-) sb (.append sb c)))
                                  (StringBuilder.)
                                  s))
               16))

(def ^:private uuid-v4-re
  #"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

;; ──────────────────────────────────────────────
;; UUID v4 Public API
;; ──────────────────────────────────────────────

(defn gen
  "Generate a new UUID v4 string.
  Returns a 36-character RFC 4122 UUID string.

    (gen)
    ;; => \"550e8400-e29b-41d4-a716-446655440000\""
  {:added "0.1.0"}
  []
  (bytes->hex-str (random-uuid-bytes)))

(defn valid?
  "Return true if s is a valid UUID v4 string (36-char hex-hyphenated, version 4).

    (valid? \"550e8400-e29b-41d4-a716-446655440000\")
    ;; => true

    (valid? \"not-a-uuid\")
    ;; => false"
  {:added "0.1.0"}
  [^String s]
  (boolean (re-matches uuid-v4-re (.toLowerCase (str s)))))

(defn- bigint->bytes-16
  "Convert a BigInteger (0 to 2^128 - 1) to a 16-byte big-endian byte array."
  [^java.math.BigInteger bi]
  (let [ba (.toByteArray bi)
        n (count ba)]
    (cond
      (= n 16) ba
      (= n 17) (java.util.Arrays/copyOfRange ba 1 17)
      :else    (let [result (byte-array 16)]
                 (System/arraycopy ba 0 result (- 16 n) n)
                 result))))

(defn decode
  "Decode a UUID v4 string into a 16-byte byte array.
  Returns nil if s is not a valid UUID.

    (decode \"550e8400-e29b-41d4-a716-446655440000\")
    ;; => #object[\"[B\" 0x...]"
  {:added "0.1.0"}
  [^String s]
  (when (valid? s)
    (bigint->bytes-16 (hex-str->bigint s))))

(defn encode
  "Encode a 16-byte byte array into a UUID v4 string.

    (encode (byte-array 16 (range 16)))
    ;; => \"00010203-0405-0607-0809-0a0b0c0d0e0f\""
  {:added "0.1.0"}
  [^bytes byte-arr]
  (let [n (count byte-arr)]
    (when-not (= n 16)
      (throw (IllegalArgumentException.
               (str "UUID byte array must be exactly 16 bytes, got " n)))))
  (bytes->hex-str byte-arr))

(defrecord UUIDGenerator []
  pct/IdGenerator
  (generate [this opts] (gen))
  (valid? [this id-str] (valid? id-str))
  (decode [this id-str] (decode id-str))
  (encode [this byte-arr] (encode byte-arr)))
