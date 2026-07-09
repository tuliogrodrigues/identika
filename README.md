# Identika

**A lightweight, zero-dependency Clojure toolkit for generating and parsing unique identifiers (ULID, UUID v4, and more).**

---

Identika provides a collection of modern unique identifier strategies under a single, unified namespace. Designed for database primary keys, distributed tracing, log collation, and client-safe obfuscation — using idiomatic Clojure without pulling in heavy transitive dependencies.

## Key Features

- **Zero Transitive Dependencies** — Built using pure Clojure (`org.clojure/clojure`) and standard JDK classes (`java.security.SecureRandom`, etc.).
- **Consistent, Idiomatic API** — Every strategy follows the same patterns via Clojure protocols.
- **Thread-Safe** — All entropy sources use `SecureRandom` and are shared across calls.
- **Unified Toolkit** — Single namespace for ULID, UUID v4, and future strategies (NanoID, KSUID, etc.).

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
(require '[identika.core :as identika])

;; Generate a UUID v4 (default strategy)
(identika/generate)
;; => "550e8400-e29b-41d4-a716-446655440000"

;; Generate a ULID
(identika/generate :ulid)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"
```

### Polymorphic API via Multimethods

The `identika.core` namespace uses Clojure multimethods dispatched on strategy keywords (`:uuid`, `:ulid`). Every function accepts a strategy keyword as its first argument to select the format.

---

### UUID v4 (RFC 4122)

UUID v4 is the **default strategy**. Generates 36-character hex-hyphenated strings with proper version (0100) and variant (10xx) bits.

```clojure
(require '[identika.core :as identika])

;; Generate
(identika/generate :uuid)
;; => "550e8400-e29b-41d4-a716-446655440000"

;; Validate
(identika/valid? :uuid "550e8400-e29b-41d4-a716-446655440000")
;; => true

(identika/valid? :uuid "not-a-uuid")
;; => false

;; Decode a UUID string into a 16-byte array
(identika/decode :uuid "550e8400-e29b-41d4-a716-446655440000")
;; => #object["[B" ...]

;; Encode a 16-byte array back into a UUID string
(identika/encode :uuid (byte-array 16 (range 16)))
;; => "00010203-0405-0607-0809-0a0b0c0d0e0f"
```

UUID v4 is **not** time-sortable and does **not** support monotonic operations:

```clojure
(identika/get-timestamp :uuid (identika/generate :uuid))
;; => nil  (UUID v4 has no embedded timestamp)

(identika/next-id :uuid (identika/generate :uuid))
;; => nil

(identika/monotonic-gen :uuid (atom nil))
;; => nil
```

---

### ULID (Universally Unique Lexicographically Sortable Identifier)

ULIDs are 128-bit identifiers consisting of:
- A **48-bit timestamp** (millisecond Unix epoch)
- An **80-bit random component** (generated using `SecureRandom`)
- Encoded using **Crockford's Base32** (excluding I, L, O, U to avoid visual confusion)

#### Generation

```clojure
(require '[identika.core :as identika])

;; Generate using current system time
(identika/generate :ulid)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"

;; Generate with a specific timestamp (millisecond epoch)
(identika/generate :ulid {:timestamp 1781290640998})
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"
```

#### Validation

```clojure
;; Validate a ULID string
(identika/valid? :ulid "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => true

(identika/valid? :ulid "invalid-ulid!")
;; => false
```

#### Timestamp Extraction

```clojure
;; Extract the millisecond timestamp
(identika/get-timestamp :ulid "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => 1781290640998

;; Non-time-sortable strategies return nil
(identika/get-timestamp :uuid (identika/generate :uuid))
;; => nil
```

#### Encode / Decode (String ↔ byte[])

`decode` and `encode` are inverses — `encode ∘ decode = id`:

```clojure
;; Decode a ULID string into a 16-byte array
(identika/decode :ulid "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => #object["[B" ...]

;; Encode a 16-byte array back into a ULID string
(identika/encode :ulid (identika/decode :ulid "01ARZ3NDEKTSV4RRFFQ69G5FAV"))
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"
```

#### Next & Monotonic

```clojure
;; Get the next lexicographical ULID (increments random component)
(identika/next-id :ulid "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAW"

;; Monotonic generation via state atom
(def ulid-state (atom nil))
(identika/monotonic-gen :ulid ulid-state)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"

;; Next call within same millisecond increments instead of re-rolling entropy
(identika/monotonic-gen :ulid ulid-state)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAW"
```

#### Raw Namespace

You can also use the `identika.ulid` namespace directly:

```clojure
(require '[identika.ulid :as ulid])

(ulid/gen)                        ;; Generate ULID string
(ulid/valid? "01ARZ3ND...")       ;; Validate
(ulid/timestamp "01ARZ3ND...")    ;; Extract timestamp
(ulid/decode "01ARZ3ND...")       ;; String → 16-byte array
(ulid/encode byte-arr)            ;; 16-byte array → string
(ulid/next-ulid "01ARZ3ND...")    ;; Next lexicographic ULID
(ulid/monotonic (atom nil))       ;; Monotonic generation
```

---

## API Reference

### `identika.core` (public API)

All functions are multimethods dispatched on a **strategy keyword** (`:uuid`, `:ulid`).

| Function | Description |
| :--- | :--- |
| `(generate)` / `(generate strategy opts?)` | Generate an ID string (default: `:uuid`). `opts` may include `:timestamp` |
| `(valid? strategy id-str)` | Check if `id-str` is valid for the given strategy |
| `(encode strategy byte-arr)` | Encode a 16-byte array into an ID string |
| `(decode strategy id-str)` | Decode an ID string into a 16-byte byte array |
| `(get-timestamp strategy id-str)` | Extract millisecond timestamp (ULID only; returns `nil` for UUID) |
| `(next-id strategy id-str)` | Return the next ID in sort order (ULID only; returns `nil` for UUID) |
| `(monotonic-gen strategy state-atom)` | Generate monotonically increasing IDs (ULID only; returns `nil` for UUID) |

### `identika.ulid` (direct ULID namespace)

| Function | Description |
| :--- | :--- |
| `(gen)` / `(gen timestamp)` | Generate a ULID string |
| `(valid? s)` | Returns `true` if `s` is a valid 26-char Crockford Base32 ULID |
| `(timestamp s)` | Extract millisecond timestamp, or `nil` |
| `(decode s)` | Decode ULID string → 16-byte array; `nil` if invalid |
| `(encode byte-arr)` | Encode 16-byte array → ULID string; throws if not 16 bytes |
| `(next-ulid s)` | Next lexicographic ULID; `nil` if invalid |
| `(monotonic state-atom)` | Monotonically increasing ULIDs via state atom |

### `identika.uuid` (direct UUID namespace)

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
