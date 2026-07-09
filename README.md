# Identika

**A lightweight, zero-dependency Clojure toolkit for generating and parsing unique identifiers (ULID, UUID v4, and more).**

---

Identika provides a collection of modern unique identifier strategies, each in its own self-contained namespace. Designed for database primary keys, distributed tracing, log collation, and client-safe obfuscation — using idiomatic Clojure without pulling in heavy transitive dependencies.

## Key Features

- **Zero Transitive Dependencies** — Built using pure Clojure (`org.clojure/clojure`) and standard JDK classes (`java.security.SecureRandom`, etc.).
- **Self-Contained Namespaces** — Each strategy is independent. Import only what you need.
- **Thread-Safe** — All entropy sources use `SecureRandom` and are shared across calls.
- **Pluggable** — Future strategies (NanoID, KSUID, UUIDv7) ship as their own namespaces with no shared machinery.

---

## Supported & Planned Identifier Formats

| Format | Sortable? | Length / Representation | Key Advantages & Best Use Case | Status |
| :--- | :---: | :--- | :--- | :--- |
| **UUID v4** | **No** | 36 chars (hex-hyphens) / 16 bytes | RFC 4122 random UUID. Universal standard, widely supported. | ✅ Complete |
| **ULID** | **Yes** | 26 chars (Crockford Base32) / 16 bytes | Millisecond-precision sorting, URL-safe, case-insensitive. Excellent for DB keys. | ✅ Complete |
| **UUIDv7** | **Yes** | 36 chars (Hex-Hyphens) / 16 bytes | RFC 9562 time-ordered UUID. Seamless drop-in for traditional UUIDs. | ⏳ Planned |
| **KSUID** | **Yes** | 27 chars (Base62) / 20 bytes | 32-bit second-precision timestamp + 128-bit random payload. | ⏳ Planned |
| **NanoID** | **No** | Customizable (default 21 chars) | Compact, highly secure, custom alphabets. Great for user-facing short IDs. | ⏳ Planned |
| **CUID2** | **No** | Customizable (default 24 chars) | Secure, collision-resistant, horizontally-scalable IDs. | ⏳ Planned |
| **HashID** | **No** | Variable based on input integer | Reversible obfuscation for auto-incrementing IDs. | ⏳ Planned |
| **FlakeID**| **Yes** | 64-bit Long / Hex | Snowflake-style distributed ID (64-bit integer space). | ⏳ Planned |

---

## Installation

Add Identika to your `deps.edn` dependencies:

```clojure
com.identika/identika {:mvn/version "0.1.0"}
```

---

## Usage

### Quick Start

```clojure
(require '[identika.uuid :as uuid]
         '[identika.ulid :as ulid])

;; Generate a UUID v4
(uuid/gen)
;; => "550e8400-e29b-41d4-a716-446655440000"

;; Generate a ULID
(ulid/gen)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"
```

---

### UUID v4 (RFC 4122)

Generates 36-character hex-hyphenated strings with proper version (0100) and variant (10xx) bits.

```clojure
(require '[identika.uuid :as uuid])

;; Generate
(uuid/gen)
;; => "550e8400-e29b-41d4-a716-446655440000"

;; Validate
(uuid/valid? "550e8400-e29b-41d4-a716-446655440000")
;; => true

(uuid/valid? "not-a-uuid")
;; => false

;; Decode a UUID string into a 16-byte array
(uuid/decode "550e8400-e29b-41d4-a716-446655440000")
;; => #object["[B" ...]

;; Encode a 16-byte array back into a UUID string
(uuid/encode (byte-array 16 (range 16)))
;; => "00010203-0405-0607-0809-0a0b0c0d0e0f"
```

UUID v4 is **not** time-sortable and does **not** support monotonic operations. The namespace only includes `gen`, `valid?`, `decode`, and `encode`.

---

### ULID (Universally Unique Lexicographically Sortable Identifier)

ULIDs are 128-bit identifiers consisting of:
- A **48-bit timestamp** (millisecond Unix epoch)
- An **80-bit random component** (generated using `SecureRandom`)
- Encoded using **Crockford's Base32** (excluding I, L, O, U to avoid visual confusion)

#### Generation

```clojure
(require '[identika.ulid :as ulid])

;; Generate using current system time
(ulid/gen)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"

;; Generate with a specific timestamp (millisecond epoch)
(ulid/gen 1781290640998)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"
```

#### Validation

```clojure
;; Validate a ULID string
(ulid/valid? "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => true

(ulid/valid? "invalid-ulid!")
;; => false
```

#### Timestamp Extraction

```clojure
;; Extract the millisecond timestamp
(ulid/timestamp "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => 1781290640998

;; Returns nil for invalid ULIDs
(ulid/timestamp "not-a-ulid")
;; => nil
```

#### Encode / Decode (String ↔ byte[])

`decode` and `encode` are inverses — `encode ∘ decode = id`:

```clojure
;; Decode a ULID string into a 16-byte array
(ulid/decode "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => #object["[B" ...]

;; Encode a 16-byte array back into a ULID string
(ulid/encode (ulid/decode "01ARZ3NDEKTSV4RRFFQ69G5FAV"))
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"
```

#### Next & Monotonic

```clojure
;; Get the next lexicographical ULID (increments random component)
(ulid/next-ulid "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAW"

;; Monotonic generation via state atom
(def ulid-state (atom nil))
(ulid/monotonic ulid-state)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"

;; Next call within same millisecond increments instead of re-rolling entropy
(ulid/monotonic ulid-state)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAW"
```

---

## API Reference

### `identika.ulid`

| Function | Description |
| :--- | :--- |
| `(gen)` / `(gen timestamp)` | Generate a ULID string |
| `(valid? s)` | Returns `true` if `s` is a valid 26-char Crockford Base32 ULID |
| `(timestamp s)` | Extract millisecond timestamp, or `nil` |
| `(decode s)` | Decode ULID string → 16-byte array; `nil` if invalid |
| `(encode byte-arr)` | Encode 16-byte array → ULID string; throws if not 16 bytes |
| `(next-ulid s)` | Next lexicographic ULID; `nil` if invalid |
| `(monotonic state-atom)` | Monotonically increasing ULIDs via state atom |

### `identika.uuid`

| Function | Description |
| :--- | :--- |
| `(gen)` | Generate a UUID v4 string |
| `(valid? s)` | Returns `true` if `s` is a valid RFC 4122 UUID v4 |
| `(decode s)` | Decode UUID string → 16-byte array; `nil` if invalid |
| `(encode byte-arr)` | Encode 16-byte array → UUID string; throws if not 16 bytes |

---

## Development & Testing

### Running Tests

Identika uses [Kaocha](https://github.com/lambdaisland/kaocha) for testing:

```bash
clojure -M:test/unit
```

### REPL Workflow

```bash
clojure -M:repl
```

---

## Roadmap

- [x] **UUID v4** — Generation, validation, encode/decode round-trip
- [x] **ULID** — Generation, validation, timestamp extraction, encode/decode, `next-ulid`, monotonic generation
- [ ] **UUIDv7** — Time-ordered UUIDs (RFC 9562)
- [ ] **KSUID** — K-Sortable Unique Identifier
- [ ] **NanoID** — Compact, URL-safe, customizable-length IDs
- [ ] **HashID** — Reversible, salt-based ID obfuscation
- [ ] **CUID2** — Secure, collision-resistant IDs for horizontal scaling
- [ ] **FlakeID** — Distributed, time-sorted, snowflake-style IDs

---

## License

Distributed under the MIT License.
