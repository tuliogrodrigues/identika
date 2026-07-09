(ns identika.core
  "Core namespace for Identika — a unified unique identifier toolkit.

  Provides a multimethod-based API for generating and working with various
  ID strategies (UUID, ULID, NanoID, etc.).

  ## Quick Start

    (require '[identika.core :as identika])

    ;; Generate a UUID v4 (default strategy)
    (identika/generate)
    ;; => \"550e8400-e29b-41d4-a716-446655440000\"

    ;; Use ULID strategy
    (identika/generate :ulid)
    ;; => \"01ARZ3NDEKTSV4RRFFQ69G5FAV\"

    ;; Time-sortable operations (ULID only)
    (identika/get-timestamp :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")
    ;; => 1781290640998"
  (:require [identika.ulid :as ulid]
            [identika.uuid :as uuid]))

;; ──────────────────────────────────────────────
;; generate
;; ──────────────────────────────────────────────

(defmulti ^:private ->generate
  "Multimethod dispatching on strategy keyword for ID generation."
  (fn [strategy & _] strategy))

(defmethod ^:private ->generate :ulid [_ & [opts]]
  (if opts
    (ulid/gen (:timestamp opts (System/currentTimeMillis)))
    (ulid/gen)))

(defmethod ^:private ->generate :uuid [_ & _]
  (uuid/gen))

(defmethod ^:private ->generate :default [strategy & _]
  (throw (IllegalArgumentException.
           (str "Unknown ID generator strategy: " strategy
                ". Supported: :uuid, :ulid"))))

(defn generate
  "Generate a new identifier string.

  (generate)        => UUID v4 (default)
  (generate :ulid)  => ULID with current timestamp
  (generate :ulid {:timestamp 1781290640998})  => ULID with specific timestamp"
  ([]
   (->generate :uuid))
  ([strategy]
   (->generate strategy))
  ([strategy opts]
   (->generate strategy opts)))

;; ──────────────────────────────────────────────
;; valid?
;; ──────────────────────────────────────────────

(defmulti valid?
  "Return true if id is a valid identifier for the given strategy.

    (valid? :uuid \"550e8400-e29b-41d4-a716-446655440000\")  ;; => true
    (valid? :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")            ;; => true
    (valid? :ulid \"not-a-ulid\")                             ;; => false"
  (fn [strategy _] strategy))

(defmethod valid? :ulid [_ s]
  (ulid/valid? s))

(defmethod valid? :uuid [_ s]
  (uuid/valid? s))

(defmethod valid? :default [strategy _]
  (throw (IllegalArgumentException.
           (str "Unknown ID generator strategy: " strategy
                ". Supported: :uuid, :ulid"))))

;; ──────────────────────────────────────────────
;; encode / decode
;; ──────────────────────────────────────────────

(defmulti encode
  "Encode a 16-byte byte array into an identifier string for the given strategy.

    (encode :ulid (byte-array 16 ...))  ;; => \"01ARZ3NDEKTSV4RRFFQ69G5FAV\"
    (encode :uuid (byte-array 16 ...))  ;; => \"550e8400-e29b-41d4-a716-446655440000\""
  (fn [strategy _] strategy))

(defmethod encode :ulid [_ ba]
  (ulid/encode ba))

(defmethod encode :uuid [_ ba]
  (uuid/encode ba))

(defmethod encode :default [strategy _]
  (throw (IllegalArgumentException.
           (str "Unknown ID generator strategy: " strategy
                ". Supported: :uuid, :ulid"))))

(defmulti decode
  "Decode an identifier string into a 16-byte byte array.

    (decode :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")  ;; => byte[]
    (decode :uuid \"550e8400-e29b-41d4-a716-446655440000\")  ;; => byte[]"
  (fn [strategy _] strategy))

(defmethod decode :ulid [_ s]
  (ulid/decode s))

(defmethod decode :uuid [_ s]
  (uuid/decode s))

(defmethod decode :default [strategy _]
  (throw (IllegalArgumentException.
           (str "Unknown ID generator strategy: " strategy
                ". Supported: :uuid, :ulid"))))

;; ──────────────────────────────────────────────
;; Optional: get-timestamp  (ULID only)
;; ──────────────────────────────────────────────

(defmulti get-timestamp
  "Extract the millisecond epoch timestamp from an identifier.
  Returns nil for strategies that don't embed timestamps.

    (get-timestamp :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")  ;; => 1781290640998
    (get-timestamp :uuid \"550e8400-...\")                  ;; => nil"
  (fn [strategy _] strategy))

(defmethod get-timestamp :ulid [_ s]
  (ulid/timestamp s))

(defmethod get-timestamp :uuid [_ _]
  nil)

(defmethod get-timestamp :default [strategy _]
  (throw (IllegalArgumentException.
           (str "Unknown ID generator strategy: " strategy
                ". Supported: :uuid, :ulid"))))

;; ──────────────────────────────────────────────
;; Optional: next-id  (ULID only)
;; ──────────────────────────────────────────────

(defmulti next-id
  "Return the next identifier in lexicographic sort order.
  Returns nil for strategies that don't support monotonic ordering.

    (next-id :ulid \"01ARZ3NDEKTSV4RRFFQ69G5FAV\")  ;; => \"01ARZ3NDEKTSV4RRFFQ69G5FAW\"
    (next-id :uuid \"550e8400-...\")                  ;; => nil"
  (fn [strategy _] strategy))

(defmethod next-id :ulid [_ s]
  (ulid/next-ulid s))

(defmethod next-id :uuid [_ _]
  nil)

(defmethod next-id :default [strategy _]
  (throw (IllegalArgumentException.
           (str "Unknown ID generator strategy: " strategy
                ". Supported: :uuid, :ulid"))))

;; ──────────────────────────────────────────────
;; Optional: monotonic-gen  (ULID only)
;; ──────────────────────────────────────────────

(defmulti monotonic-gen
  "Generate monotonically increasing identifiers using a state atom.
  Returns nil for strategies that don't support monotonic ordering.

    (monotonic-gen :ulid (atom nil))  ;; => \"01ARZ3NDEKTSV4RRFFQ69G5FAV\""
  (fn [strategy _] strategy))

(defmethod monotonic-gen :ulid [_ state]
  (ulid/monotonic state))

(defmethod monotonic-gen :uuid [_ _]
  nil)

(defmethod monotonic-gen :default [strategy _]
  (throw (IllegalArgumentException.
           (str "Unknown ID generator strategy: " strategy
                ". Supported: :uuid, :ulid"))))
