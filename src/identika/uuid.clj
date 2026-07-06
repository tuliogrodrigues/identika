(ns identika.uuid
  "UUID v4 generation, parsing, and validation.

  Implements RFC 4122 UUID v4 (random):
  - 36-char hex-hyphenated string (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
  - 122 bits of randomness, version 4 (0100), variant 1 (10xx)
  - Pure Clojure, zero dependencies beyond java.security.SecureRandom"
  (:import [java.security SecureRandom]
           [java.math BigInteger]
           [java.util UUID]))

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

(defn to-bytes
  "Decode a UUID v4 string into its BigInteger representation.
  Returns nil if s is not a valid UUID.

    (to-bytes \"550e8400-e29b-41d4-a716-446655440000\")
    ;; => 2347923874092379847239847923874923874"
  {:added "0.1.0"}
  [^String s]
  (when (valid? s)
    (hex-str->bigint s)))

(defn bytes->id
  "Encode a 16-byte byte array into a UUID v4 string.

    (bytes->id (byte-array 16 (range 16)))
    ;; => \"00010203-0405-0607-0809-0a0b0c0d0e0f\""
  {:added "0.1.0"}
  [^bytes byte-arr]
  (bytes->hex-str byte-arr))
