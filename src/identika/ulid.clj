(ns identika.ulid
  "ULID generation, parsing, and utilities.

  Implements the ULID spec:
  - 26-char Crockford Base32 encoded string
  - 48-bit timestamp (millisecond Unix epoch) + 80-bit random component
  - Lexicographically sortable
  - Monotonic generation within same millisecond"
  (:require [identika.protocols :as pct]
            [identika.protocols :as ptc])
  (:import [java.security SecureRandom]))

(defonce ^:private randomizer (SecureRandom.))

(defonce ^:private ^chars encoding-chars
  (char-array "0123456789ABCDEFGHJKMNPQRSTVWXYZ"))

(defonce ^:private encoding-map
  (zipmap encoding-chars (range 32)))

#_(def ^:private decoding-map
  (let [m (java.util.HashMap.)
        s encoding-chars]
    (doseq [i (range (count s))]
      (.put m (long (nth s i)) (byte i))
      (.put m (long (Character/toLowerCase ^char (nth s i))) (byte i)))
    m))

(defn- ulid-timestamp->bytes [timestamp]
    (for [shift (range 40 -1 -8)]
      (-> (bit-shift-right timestamp shift)
          (bit-and 0xFF))))

(defn- ulid-entropy->bytes []
  (repeatedly 10 #(bit-and (.nextInt randomizer) 0xFF)))

(defn- crockford-millis [ulid]
  (when ulid
    (reduce #(+ (bit-shift-left %1 5)
                (get encoding-map %2))
            0
            (take 10 ulid))))

(defn- bit-masking [^BigInteger acc el]
  (.or (.shiftLeft acc 8)
       (biginteger el)))

(defn- ulid-bytes
  [timestamp]
  (reduce bit-masking BigInteger/ZERO
          (concat (ulid-timestamp->bytes timestamp)
                  (ulid-entropy->bytes))))

(defn- bigint->crockford
  "Encode a BigInteger into a 26-character Crockford Base32 ULID string."
  [^java.math.BigInteger v]
  (let [sb (StringBuilder. 26)]
    (loop [n 25]
      (if (neg? n)
        (.toString sb)
        (let [idx (.intValue (.and (.shiftRight v (* n 5))
                                    (BigInteger/valueOf 0x1F)))]
          (.append sb (get encoding-chars idx))
          (recur (dec n)))))))

(defn- ulid-decode
  "Decode a 26-character Crockford Base32 ULID string into its BigInteger representation."
  [ulid]
  (reduce #(.or (.shiftLeft ^java.math.BigInteger %1 5)
                (biginteger (get encoding-map %2)))
          BigInteger/ZERO
          ulid))

(defn- bigint->bytes-16
  "Convert a BigInteger (0 to 2^128 - 1) to a 16-byte big-endian byte array.
  java.math.BigInteger/toByteArray may return fewer than 16 bytes (sign-trimmed)
  or 17 bytes (sign bit), so this normalises to exactly 16."
  [^java.math.BigInteger bi]
  (let [ba (.toByteArray bi)
        n (count ba)]
    (cond
      (= n 16) ba
      (= n 17) (java.util.Arrays/copyOfRange ba 1 17)
      :else    (let [result (byte-array 16)]
                 (System/arraycopy ba 0 result (- 16 n) n)
                 result))))

;; ──────────────────────────────────────────────
;; ULID Interface
;; ──────────────────────────────────────────────



(defn valid?
  "Return true if is a valid ULID string (26-char Crockford Base32)."
  {:added "0.0.1"}
  [ulid]
  (cond
    (not= (count ulid) 26) (do (println "ULID invalid length") false)
    (not-every? (apply hash-set encoding-chars) ulid) (do (println "ULID contains invalid characters") false)
    :else true))

(defn timestamp
  "Extract the timestamp from a ULID string as a java.time.Instant."
  {:added "0.0.1"}
  [ulid]
  (when (valid? ulid)
    (crockford-millis ulid)))

(defn decode
  "Decode a ULID string into a 16-byte byte array."
  {:added "0.1.0"}
  [ulid]
  (when (valid? ulid)
    (bigint->bytes-16 (ulid-decode ulid))))

(defn encode
  "Encode a 16-byte byte array into a ULID string."
  {:added "0.1.0"}
  [byte-arr]
  (let [n (count byte-arr)]
    (when-not (= n 16)
      (throw (IllegalArgumentException.
               (str "ULID byte array must be exactly 16 bytes, got " n)))))
  (bigint->crockford (BigInteger. 1 byte-arr)))

(defn gen
  "Generate a new ULID string.
  Returns a 26-character Crockford Base32 encoded ULID."
  {:added "0.0.1"}
  ([] (gen (System/currentTimeMillis)))
  ([^long timestamp ]
   (bigint->crockford (ulid-bytes timestamp))))

(defn next-ulid
  "Return the next ULID in lexicographic sort order after `ulid-str`.
  Increments the random component, carrying into the timestamp if overflow.
  Returns nil if `ulid-str` is not a valid ULID."
  {:added "1.0"}
  [ulid-str]
  (when (valid? ulid-str)
    (bigint->crockford (.add (ulid-decode ulid-str) BigInteger/ONE))))

(defn monotonic
  "Generate a monotonically increasing ULID using an explicit state atom.

  Usage:
    (def gen (atom nil))
    (identika.ulid/monotonic gen)  ;; returns ULID with guaranteed increasing order

  The atom holds the last generated ULID string. If the next call falls within
  the same millisecond, the random component is incremented. If the timestamp
  advances, a fresh ULID is generated."
  {:added "1.0"}
  [generator-atom]
  (let [ts (System/currentTimeMillis)
        prev @generator-atom]
    (if (and prev (= ts (timestamp prev)))
      ;; Same millisecond: increment the previous ULID
      (let [next (next-ulid prev)]
        (reset! generator-atom next)
        next)
      ;; Different millisecond or first call: generate fresh
      (let [ulid (gen ts)]
        (reset! generator-atom ulid)
        ulid))))


(defrecord ULIDGenerator []
  ptc/IdGenerator
  (generate [this opts]
    (gen (if opts
                (:timestamp opts (System/currentTimeMillis))
                (System/currentTimeMillis))))
  (valid? [this id-str]
    (valid? id-str))
  (decode [this id-str]
    (decode id-str))
  (encode [this byte-arr]
    (encode byte-arr))

  ptc/TimeSortable
  (timestamp [this id-str]
    (timestamp id-str))

  ptc/MonotonicId
  (next-id [this id-str]
    (next-ulid id-str))
  (monotonic-gen [this state-atom]
    (monotonic state-atom)))
