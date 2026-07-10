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
  (:require [identika.protocols :as proto]
            [identika.ulid :as ulid]
            [identika.uuid :as uuid]))

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
     :uuid (uuid/->UUIDGenerator)
     :ulid (ulid/->ULIDGenerator)
     (throw (IllegalArgumentException.
              (str "Unknown ID generator strategy: " strategy
                   ". Supported: :uuid, :ulid"))))))

(defn generate
  ([strategy]
   (generate strategy nil))
  ([strategy opts]
   (proto/generate (generator strategy) opts)))

(defn valid? [strategy id]
  (proto/valid? (generator strategy) id))

(defn encode [strategy byte-arr]
  (proto/encode (generator strategy) byte-arr))

(defn decode [strategy id]
  (proto/decode (generator strategy) id))

(defn get-timestamp [strategy id]
  (let [gen (generator strategy)]
    (when (satisfies? proto/TimeSortable gen)
      (proto/timestamp gen id))))

(defn next-id [strategy id]
  (let [gen (generator strategy)]
    (when (satisfies? proto/MonotonicId gen)
      (proto/next-id gen id))))

(defn monotonic-gen [strategy state]
  (let [gen (generator strategy)]
    (when (satisfies? proto/MonotonicId gen)
      (proto/monotonic-gen gen state))))
