(ns identika.core
  "Core namespace for Identika — a unified unique identifier toolkit.

  Provides protocols, generator records, and a factory for creating and working
  with various ID strategies (UUID, ULID, NanoID, etc.).

  ## Quick Start

    (require '[identika.core :as identika])

    ;; Generate a UUID v4 (default strategy)
    (identika/generate)
    ;; => \"550e8400-e29b-41d4-a716-446655440000\"

    ;; Use ULID strategy
    (identika/generate :ulid)
    ;; => \"01ARZ3NDEKTSV4RRFFQ69G5FAV\"

    ;; Explicit generator record
    (def gen (identika/generator :ulid))
    (identika/generate gen)

    ;; Time-sortable operations (ULID only)
    (identika/get-timestamp :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")
    ;; => 1781290640998"
  (:require [identika.ulid :as ulid]
            [identika.uuid :as uuid])
  (:import [java.math BigInteger]))

;; ──────────────────────────────────────────────
;; Protocols
;; ──────────────────────────────────────────────

(defprotocol IdGenerator
  "Protocol for unique identifier generation and parsing.

  Every ID strategy (ULID, UUID, NanoID, etc.) implements this protocol,
  providing a consistent API for generating and converting identifiers."
  (generate
    [this opts]
    "Generate a new identifier string.

    opts is a (possibly nil) map with strategy-specific keys:
    - :timestamp — millisecond epoch for time-based IDs (ULID, KSUID)
    - :size      — length for variable-length IDs (NanoID)
    - :alphabet  — custom alphabet (NanoID)")
  (valid?
    [this id-str]
    "Returns true if id-str is a valid identifier for this strategy.")
  (to-bytes
    [this id-str]
    "Decode an identifier string into its BigInteger representation.")
  (bytes->id
    [this byte-arr]
    "Encode a 16-byte array back into an identifier string."))

(defprotocol TimeSortable
  "Mixin protocol for ID types that embed timestamps.

  Implemented by ULID, KSUID, UUIDv7, FlakeID — any ID whose string
  representation encodes a creation timestamp."
  (timestamp
    [this id-str]
    "Extract the millisecond epoch timestamp from an identifier string."))

(defprotocol MonotonicId
  "Mixin protocol for ID types that support monotonic (incrementing) ordering.

  Useful for database primary keys where strict sort order within the same
  timestamp is required."
  (next-id
    [this id-str]
    "Return the next identifier in lexicographic sort order after id-str.")
  (monotonic-gen
    [this state-atom]
    "Generate monotonically increasing identifiers using an explicit state atom."))

;; ──────────────────────────────────────────────
;; Capture protocol dispatch fns before overriding
;; ──────────────────────────────────────────────

(def ^:private ^:no-doc -prot-generate generate)
(def ^:private ^:no-doc -prot-valid? valid?)
(def ^:private ^:no-doc -prot-to-bytes to-bytes)
(def ^:private ^:no-doc -prot-bytes->id bytes->id)
(def ^:private ^:no-doc -prot-timestamp timestamp)
(def ^:private ^:no-doc -prot-next-id next-id)
(def ^:private ^:no-doc -prot-monotonic-gen monotonic-gen)

;; ──────────────────────────────────────────────
;; UUID v4 Generator (delegates to identika.uuid)
;; ──────────────────────────────────────────────

(defrecord UUIDGenerator []
  IdGenerator
  (generate [this opts] (uuid/gen))
  (valid? [this id-str] (uuid/valid? id-str))
  (to-bytes [this id-str] (uuid/to-bytes id-str))
  (bytes->id [this byte-arr] (uuid/bytes->id byte-arr)))

;; ──────────────────────────────────────────────
;; ULID Generator
;; ──────────────────────────────────────────────

(defrecord ULIDGenerator []
  IdGenerator
  (generate [this opts]
    (ulid/gen (if opts
                (:timestamp opts (System/currentTimeMillis))
                (System/currentTimeMillis))))
  (valid? [this id-str]
    (ulid/valid? id-str))
  (to-bytes [this id-str]
    (ulid/to-bytes id-str))
  (bytes->id [this byte-arr]
    (ulid/bytes->ulid byte-arr))

  TimeSortable
  (timestamp [this id-str]
    (ulid/timestamp id-str))

  MonotonicId
  (next-id [this id-str]
    (ulid/next-ulid id-str))
  (monotonic-gen [this state-atom]
    (ulid/monotonic state-atom)))

;; ──────────────────────────────────────────────
;; Factory
;; ──────────────────────────────────────────────

(defn generator
  "Create an ID generator for the specified strategy.

  Supported strategies:
  - :uuid  — RFC 4122 UUID v4 (random) — DEFAULT
  - :ulid  — Universally Unique Lexicographically Sortable Identifier

  Returns a record implementing IdGenerator (and optionally TimeSortable,
  MonotonicId).

    (generator)        ;; => UUID generator (default)
    (generator :ulid)  ;; => ULID generator"
  ([]
   (generator :uuid))
  ([strategy]
   (case strategy
     :uuid (->UUIDGenerator)
     :ulid (->ULIDGenerator)
     (throw (IllegalArgumentException.
              (str "Unknown ID generator strategy: " strategy
                   ". Supported: :uuid, :ulid"))))))

;; ──────────────────────────────────────────────
;; Internal helpers
;; ──────────────────────────────────────────────

(defn- resolve-generator
  "If x is a keyword, create the corresponding generator via `generator`.
  Otherwise, assume it's already an IdGenerator instance."
  [x]
  (if (keyword? x)
    (generator x)
    x))

;; ──────────────────────────────────────────────
;; Convenience Functions
;; ──────────────────────────────────────────────
;;
;; These call the captured protocol dispatch fns (-prot-*) to avoid
;; circular-name conflicts with the protocol methods themselves.

(defn generate
  "Generate a new identifier.

  Accepts a strategy keyword, an IdGenerator record, or no argument (defaults
  to :uuid).

    (generate)              ;; UUID (default)
    (generate :ulid)        ;; ULID
    (generate gen)          ;; from existing generator record
    (generate :ulid {:timestamp 1781290640998})"
  ([]
   (-prot-generate (generator) nil))
  ([strategy-or-gen]
   (-prot-generate (resolve-generator strategy-or-gen) nil))
  ([strategy-or-gen opts]
   (-prot-generate (resolve-generator strategy-or-gen) opts)))

(defn valid?
  "Validate an identifier string against a strategy.

  With one argument, uses the default (:uuid) strategy.
  With two arguments, first arg is a strategy keyword or IdGenerator record.

    (valid? \"550e8400-e29b-41d4-a716-446655440000\")
    (valid? :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")"
  ([id-str]
   (-prot-valid? (generator) id-str))
  ([strategy-or-gen id-str]
   (-prot-valid? (resolve-generator strategy-or-gen) id-str)))

(defn to-bytes
  "Decode an identifier string into its BigInteger representation.

  With one argument, uses the default (:uuid) strategy.

    (to-bytes \"550e8400-e29b-41d4-a716-446655440000\")
    (to-bytes :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")"
  ([id-str]
   (-prot-to-bytes (generator) id-str))
  ([strategy-or-gen id-str]
   (-prot-to-bytes (resolve-generator strategy-or-gen) id-str)))

(defn bytes->id
  "Encode a byte array into an identifier string.

  With one argument, uses the default (:uuid) strategy.

    (bytes->id byte-arr)
    (bytes->id :ulid byte-arr)"
  ([byte-arr]
   (-prot-bytes->id (generator) byte-arr))
  ([strategy-or-gen byte-arr]
   (-prot-bytes->id (resolve-generator strategy-or-gen) byte-arr)))

(defn get-timestamp
  "Extract the millisecond epoch timestamp from a time-sortable identifier.

  Returns nil for non-time-sortable types (e.g., UUID v4, NanoID).

    (get-timestamp :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")"
  [strategy-or-gen id-str]
  (let [gen (resolve-generator strategy-or-gen)]
    (when (satisfies? TimeSortable gen)
      (-prot-timestamp gen id-str))))

(defn next-id
  "Return the next identifier in lexicographic sort order.

  Returns nil for non-monotonic ID types (e.g., UUID v4).

    (next-id :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")"
  [strategy-or-gen id-str]
  (let [gen (resolve-generator strategy-or-gen)]
    (when (satisfies? MonotonicId gen)
      (-prot-next-id gen id-str))))

(defn monotonic-gen
  "Generate monotonically increasing identifiers using an explicit state atom.

  Throws for non-monotonic ID types (e.g., UUID v4).

    (def gen (atom nil))
    (monotonic-gen :ulid gen)"
  [strategy-or-gen state-atom]
  (let [gen (resolve-generator strategy-or-gen)]
    (if (satisfies? MonotonicId gen)
      (-prot-monotonic-gen gen state-atom)
      (throw (IllegalArgumentException.
               (str "Strategy does not support monotonic generation: "
                    (if (keyword? strategy-or-gen)
                      strategy-or-gen
                      (type gen))))))))
