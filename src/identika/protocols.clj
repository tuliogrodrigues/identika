(ns identika.protocols)

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
  (decode
    [this id-str]
    "Decode an identifier string into its byte array representation (16 bytes for UUID/ULID).")
  (encode
    [this byte-arr]
    "Encode a byte array back into an identifier string."))

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
