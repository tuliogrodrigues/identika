(ns identika.ksuid
  "KSUID generation, parsing, and validation.

  Implements the Segment KSUID spec:
  - 27-char Base62 encoded string
  - 32-bit timestamp (seconds since the KSUID epoch, 2014-05-13T16:53:20Z)
    followed by a 128-bit random payload
  - Lexicographically sortable by creation time
  - Pure Clojure, zero dependencies beyond java.security.SecureRandom"
  (:import [java.security SecureRandom]
           [java.math BigInteger]))

(defonce ^:private ^SecureRandom ksuid-rng (SecureRandom.))

(def ^:private epoch
  "Unix timestamp (seconds) of the KSUID epoch: 2014-05-13T16:53:20Z."
  1400000000)

(def ^:private max-timestamp
  "Largest Unix timestamp (seconds) representable in the 32-bit timestamp field."
  (+ epoch 0xFFFFFFFF))

(def ^:private base62-chars
  (char-array "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"))

(def ^:private encoding-map
  (zipmap base62-chars (range 62)))

(def ^:private base62-base
  (BigInteger/valueOf 62))

(def ^:private max-bits
  "KSUIDs are 20 bytes, i.e. 160 bits, wide."
  160)

;; ──────────────────────────────────────────────
;; Byte / Base62 plumbing
;; ──────────────────────────────────────────────

(defn- timestamp->bytes
  "Split a Unix timestamp in seconds into the 4 big-endian bytes of the
  KSUID timestamp field, which stores seconds since the KSUID epoch.

  Throws IllegalArgumentException when the value is outside the representable
  range."
  ^bytes [^long unix-seconds]
  (when-not (<= epoch unix-seconds max-timestamp)
    (throw (IllegalArgumentException.
             (str "Timestamp must be between " epoch " and " max-timestamp
                  " (Unix seconds), got " unix-seconds))))
  (let [stored (- unix-seconds epoch)
        ba     (byte-array 4)]
    (dotimes [i 4]
      (aset-byte ba i (unchecked-byte
                       (bit-and (bit-shift-right stored (* 8 (- 3 i))) 0xFF))))
    ba))

(defn- random-payload-bytes
  "Generate the 16-byte (128-bit) random payload of a KSUID."
  ^bytes []
  (let [ba (byte-array 16)]
    (.nextBytes ^SecureRandom ksuid-rng ba)
    ba))

(defn- bigint->bytes-20
  "Convert a BigInteger (0 to 2^160 - 1) to a 20-byte big-endian byte array.
  java.math.BigInteger/toByteArray may return fewer than 20 bytes (sign-trimmed)
  or 21 bytes (sign bit), so this normalises to exactly 20."
  ^bytes [^BigInteger bi]
  (let [ba (.toByteArray bi)
        n  (count ba)]
    (cond
      (= n 20) ba
      (= n 21) (java.util.Arrays/copyOfRange ba 1 21)
      :else    (let [result (byte-array 20)]
                 (System/arraycopy ba 0 result (- 20 n) n)
                 result))))

(defn- bigint->base62
  "Encode a BigInteger into a 27-character, left-zero-padded Base62 string."
  ^String [^BigInteger v]
  (let [buf (char-array 27 \0)]
    (loop [^BigInteger n v
           i 26]
      (if (or (neg? i) (zero? (.signum n)))
        (String. buf)
        (let [r (.remainder n base62-base)]
          (aset-char buf i (get base62-chars (.intValue r)))
          (recur (.divide n base62-base) (dec i)))))))

(defn- base62->bigint
  "Decode a Base62 string into a BigInteger."
  ^BigInteger [^String s]
  (reduce (fn [^BigInteger acc c]
            (.add (.multiply acc base62-base)
                  (BigInteger/valueOf (long (get encoding-map c)))))
          BigInteger/ZERO
          s))

(defn- valid-base62?
  "Return true when s is a canonical 27-char KSUID string: every character is
  in the Base62 alphabet and the decoded value fits in 160 bits."
  [^String s]
  (and (= 27 (count s))
       (every? #(contains? encoding-map %) s)
       (<= (.bitLength (base62->bigint s)) max-bits)))

;; ──────────────────────────────────────────────
;; KSUID Public API
;; ──────────────────────────────────────────────

(defn gen
  "Generate a new KSUID string.

  With no arguments, stamps the current system time. Pass an explicit Unix
  timestamp in *seconds* to generate a KSUID for a known instant. The value
  must fall within the KSUID epoch (1400000000) and the 32-bit timestamp
  limit (5694967295); anything outside that range throws.

  Returns a 27-character Base62 KSUID string.

    (gen)
    ;; => \"0ujtsYcgvSTl8PAuAdqWYSMnLOv\"

    (gen 1507608047)
    ;; => \"0ujtsXownSh4sCYyDHoVXYMCidr\""
  {:added "0.3.0"}
  ([] (gen (quot (System/currentTimeMillis) 1000)))
  ([^long unix-seconds]
   (let [ba (byte-array 20)]
     (System/arraycopy (timestamp->bytes unix-seconds) 0 ba 0 4)
     (System/arraycopy (random-payload-bytes) 0 ba 4 16)
     (bigint->base62 (BigInteger. 1 ba)))))

(defn valid?
  "Return true if s is a valid KSUID string: exactly 27 Base62 characters
  whose value fits in 160 bits.

    (valid? \"0ujtsYcgvSTl8PAuAdqWYSMnLOv\")
    ;; => true

    (valid? \"not-a-ksuid\")
    ;; => false"
  {:added "0.3.0"}
  [ksuid-str]
  (valid-base62? (str ksuid-str)))

(defn timestamp
  "Extract the Unix timestamp (seconds) embedded in a KSUID string.
  Returns nil when s is not a valid KSUID.

  The KSUID epoch is 2014-05-13T16:53:20Z, so the smallest valid KSUID
  reports 1400000000 rather than 0.

    (timestamp \"0ujtsYcgvSTl8PAuAdqWYSMnLOv\")
    ;; => 1507608047"
  {:added "0.3.0"}
  [ksuid-str]
  (let [s (str ksuid-str)]
    (when (valid-base62? s)
      (+ epoch (.longValue (.shiftRight (base62->bigint s) 128))))))

(defn decode
  "Decode a KSUID string into a 20-byte byte array.
  Returns nil if s is not a valid KSUID.

    (decode \"0ujtsYcgvSTl8PAuAdqWYSMnLOv\")
    ;; => #object[\"[B\" 0x...]"
  {:added "0.3.0"}
  [ksuid-str]
  (let [s (str ksuid-str)]
    (when (valid-base62? s)
      (bigint->bytes-20 (base62->bigint s)))))

(defn encode
  "Encode a 20-byte byte array into a KSUID string.
  Throws IllegalArgumentException when the array is not exactly 20 bytes.

    (encode (byte-array 20 (range 20)))
    ;; => \"0029sS3yqsuDyR1vGFhTuuRAq1b\""
  {:added "0.3.0"}
  [^bytes byte-arr]
  (let [n (count byte-arr)]
    (when-not (= n 20)
      (throw (IllegalArgumentException.
               (str "KSUID byte array must be exactly 20 bytes, got " n)))))
  (bigint->base62 (BigInteger. 1 byte-arr)))

(defn next-ksuid
  "Return the next KSUID in lexicographic sort order after `ksuid-str` by
  incrementing its 160-bit value.

  Returns nil if `ksuid-str` is not a valid KSUID or is already the maximum
  possible KSUID."
  {:added "0.3.0"}
  [ksuid-str]
  (let [s (str ksuid-str)]
    (when (valid-base62? s)
      (let [n (.add (base62->bigint s) BigInteger/ONE)]
        (when (<= (.bitLength n) max-bits)
          (bigint->base62 n))))))

(defn monotonic
  "Generate monotonically increasing KSUIDs using an explicit state atom.

  Usage:
    (def gen (atom nil))
    (identika.ksuid/monotonic gen)

  The atom holds the last generated KSUID string. When several calls land in
  the same second the previous KSUID is incremented instead of re-rolled, so
  ordering is guaranteed. If the wall clock moves backwards, generation
  continues from the last timestamp rather than regressing.

  Each call resets `generator-atom` to the value it returns."
  {:added "0.3.0"}
  [generator-atom]
  (let [prev    @generator-atom
        prev-ts (when (and prev (valid? prev)) (timestamp prev))
        now     (quot (System/currentTimeMillis) 1000)
        ts      (if prev-ts (max now prev-ts) now)]
    (if (= ts prev-ts)
      ;; Same second: increment the previous KSUID
      (let [next (next-ksuid prev)]
        (if next
          (do (reset! generator-atom next)
              next)
          ;; Absolute maximum reached: advance one second
          (let [next (gen (inc ts))]
            (reset! generator-atom next)
            next)))
      ;; New second (or first call): fresh random payload
      (let [next (gen ts)]
        (reset! generator-atom next)
        next))))
