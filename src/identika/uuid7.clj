(ns identika.uuid7
  "UUID v7 generation, parsing, and validation.

  Implements RFC 9562 UUID v7 (time-ordered):
  - 36-char hex-hyphenated string (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
  - 48 bits of big-endian Unix timestamp in milliseconds (bytes 0-5)
  - 12 bits of randomness (rand_a, bytes 6-7) with version 7 (0111) in byte 6
  - 62 bits of randomness (rand_b, bytes 8-15) with variant 10 in byte 8
  - Lexicographically sortable by creation time
  - Pure Clojure, zero dependencies beyond java.security.SecureRandom"
  (:import [java.security SecureRandom]))

(defonce ^:private uuid7-rng (SecureRandom.))

(def ^:private hex-chars
  (char-array "0123456789abcdef"))

(def ^:private max-timestamp
  "Largest millisecond timestamp representable in the 48-bit unix_ts_ms field."
  0xFFFFFFFFFFFF)

(def ^:private uuid7-re
  #"^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

;; ──────────────────────────────────────────────
;; Hex / byte plumbing
;; ──────────────────────────────────────────────

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

(defn- strip-hyphens
  "Remove every hyphen from a UUID string, leaving its 32 hex digits."
  ^String [^String s]
  (.replace s "-" ""))

(defn- hex-nibble
  "Read the hex digit at index i as a 0-15 long. Throws on non-hex characters."
  ^long [^String s i]
  (let [c   (.charAt s (int i))
        nib (Character/digit c 16)]
    (when (neg? nib)
      (throw (IllegalArgumentException.
               (str "Invalid hex character '" c "' at index " i))))
    nib))

(defn- hex->bytes
  "Parse a 32-char hex string (hyphens already stripped) into a 16-byte
  big-endian byte array. Throws IllegalArgumentException on non-hex input."
  ^bytes [^String s]
  (let [ba (byte-array 16)]
    (dotimes [i 16]
      (aset-byte ba i (unchecked-byte (bit-or (bit-shift-left (hex-nibble s (* 2 i)) 4)
                                              (hex-nibble s (inc (* 2 i)))))))
    ba))

(defn- parse-hex
  "Parse a hex string of at most 16 digits into a long. Throws on non-hex input."
  ^long [^String s]
  (reduce (fn [acc i]
            (bit-or (bit-shift-left acc 4) (hex-nibble s i)))
          0
          (range (count s))))

(defn- timestamp->bytes
  "Split a millisecond Unix timestamp into its 6 big-endian bytes (48 bits).
  Throws IllegalArgumentException when the value does not fit in 48 bits."
  ^bytes [^long millis]
  (when-not (<= 0 millis max-timestamp)
    (throw (IllegalArgumentException.
             (str "Timestamp must fit in 48 bits (0 to " max-timestamp "), got " millis))))
  (let [ba (byte-array 6)]
    (dotimes [i 6]
      (aset-byte ba i (unchecked-byte
                       (bit-and (bit-shift-right millis (* 8 (- 5 i))) 0xFF))))
    ba))

(defn- random-payload-bytes
  "Fill a fresh 16-byte array with randomness and stamp the version 7 (0111)
  and variant 10xx bits, leaving the 48-bit timestamp field as zeroes."
  ^bytes []
  (let [ba (byte-array 16)]
    (.nextBytes uuid7-rng ba)
    (aset-byte ba 6 (unchecked-byte (bit-or (bit-and (aget ba 6) 0x0f) 0x70)))
    (aset-byte ba 8 (unchecked-byte (bit-or (bit-and (aget ba 8) 0x3f) 0x80)))
    ba))

(defn- increment-payload
  "Increment the 74-bit random payload of a UUID v7 byte array in place: first
  the 62 bits of rand_b (bytes 9-15, then the low 6 bits of byte 8), then the
  12 bits of rand_a (byte 7, then the low nibble of byte 6). The version nibble
  in byte 6 and the variant bits in byte 8 are fixed, so the counter never
  carries into them.

  Returns true on success. Returns false when all 74 payload bits are already 1,
  i.e. the payload is exhausted for this millisecond."
  [^bytes ba]
  (let [carry-b? (loop [i 15]
                   (if (< i 9)
                     true
                     (let [b (bit-and (aget ba i) 0xFF)]
                       (if (< b 0xFF)
                         (do (aset-byte ba i (unchecked-byte (inc b))) false)
                         (do (aset-byte ba i (unchecked-byte 0x00)) (recur (dec i)))))))]
    (if-not carry-b?
      true
      ;; rand_b is full: roll over into rand_a, preserving the variant bits
      (let [b8   (bit-and (aget ba 8) 0xFF)
            b8'  (bit-and b8 0x3F)]
        (aset-byte ba 8 (unchecked-byte (bit-or 0x80 (if (< b8' 0x3F) (inc b8') 0x00))))
        (if (< b8' 0x3F)
          true
          ;; rand_a is full too: exhaust bytes 7 and the low nibble of byte 6
          (let [b7  (bit-and (aget ba 7) 0xFF)
                b6  (bit-and (aget ba 6) 0x0F)]
            (aset-byte ba 7 (unchecked-byte (if (< b7 0xFF) (inc b7) 0x00)))
            (if (< b7 0xFF)
              true
              (let [b6' (inc b6)]
                (aset-byte ba 6 (unchecked-byte (bit-or 0x70 (if (< b6' 0x0F) b6' 0x00))))
                (< b6' 0x0F)))))))))

;; ──────────────────────────────────────────────
;; UUID v7 Public API
;; ──────────────────────────────────────────────

(defn valid?
  "Return true if s is a valid UUID v7 string (36-char hex-hyphenated,
  version 7, variant 10xx).

    (valid? \"019ebd32-2280-7168-b09b-2240203908ea\")
    ;; => true

    (valid? \"550e8400-e29b-41d4-a716-446655440000\")
    ;; => false"
  {:added "0.3.0"}
  [^String s]
  (boolean (re-matches uuid7-re (.toLowerCase (str s)))))

(defn gen
  "Generate a new UUID v7 string.

  With no arguments, stamps the current system time. Pass an explicit
  millisecond Unix epoch to generate a UUID for a known instant.

  Returns a 36-character hex-hyphenated RFC 9562 UUID string.

    (gen)
    ;; => \"019ebd32-2666-7168-b09b-2240203908ea\"

    (gen 1781290640000)
    ;; => \"019ebd32-2280-7168-b09b-2240203908ea\""
  {:added "0.3.0"}
  ([] (gen (System/currentTimeMillis)))
  ([^long millis]
   (let [ba (random-payload-bytes)]
     (System/arraycopy (timestamp->bytes millis) 0 ba 0 6)
     (bytes->hex-str ba))))

(defn timestamp
  "Extract the millisecond Unix timestamp embedded in a UUID v7 string.

  Returns nil when `s` is not a valid UUID v7.

    (timestamp \"019ebd32-2280-7168-b09b-2240203908ea\")
    ;; => 1781290640000"
  {:added "0.3.0"}
  [^String s]
  (when (valid? s)
    (parse-hex (subs (strip-hyphens (.toLowerCase (str s))) 0 12))))

(defn decode
  "Decode a UUID v7 string into a 16-byte byte array.
  Returns nil if s is not a valid UUID v7.

    (decode \"019ebd32-2280-7168-b09b-2240203908ea\")
    ;; => #object[\"[B\" 0x...]"
  {:added "0.3.0"}
  [^String s]
  (when (valid? s)
    (hex->bytes (strip-hyphens (.toLowerCase (str s))))))

(defn encode
  "Encode a 16-byte byte array into a UUID v7 string.
  Throws IllegalArgumentException when the array is not exactly 16 bytes.

    (encode (byte-array 16 (range 16)))
    ;; => \"00010203-0405-0607-0809-0a0b0c0d0e0f\""
  {:added "0.3.0"}
  [^bytes byte-arr]
  (let [n (count byte-arr)]
    (when-not (= n 16)
      (throw (IllegalArgumentException.
               (str "UUID byte array must be exactly 16 bytes, got " n)))))
  (bytes->hex-str byte-arr))

(defn monotonic
  "Generate monotonically increasing UUID v7s using an explicit state atom.

  Usage:
    (def gen (atom nil))
    (identika.uuid7/monotonic gen)

  The atom holds the last generated UUID v7 string. When several calls land in
  the same millisecond the random payload (rand_a ‖ rand_b) is incremented
  instead of re-rolled, mirroring the random bit-increment counter method in
  RFC 9562 §6.2.1. If that payload is exhausted the timestamp advances by one
  millisecond. When the wall clock moves backwards, generation continues from
  the last timestamp so the sequence never regresses.

  Each call resets `generator-atom` to the value it returns."
  {:added "0.3.0"}
  [generator-atom]
  (let [prev    @generator-atom
        prev-ms (when (and prev (valid? prev)) (timestamp prev))
        millis  (if prev-ms
                  (max (System/currentTimeMillis) prev-ms)
                  (System/currentTimeMillis))]
    (if (= millis prev-ms)
      ;; Same millisecond: increment the previous random payload
      (let [ba   (hex->bytes (strip-hyphens prev))
            next (if (increment-payload ba)
                   (bytes->hex-str ba)
                   (gen (inc millis)))]
        (reset! generator-atom next)
        next)
      ;; New millisecond (or first call): fresh random payload
      (let [next (gen millis)]
        (reset! generator-atom next)
        next))))
