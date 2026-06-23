# Identika

**A lightweight, zero-dependency Clojure toolkit for generating and parsing unique identifiers (ULID, UUIDv7, NanoID, KSUID, and more).**

---

Identika provides a comprehensive collection of modern unique identifier strategies under a single, unified namespace. It is designed from the ground up to support various application needs—such as database primary keys, distributed tracing, log collation, and client-safe obfuscation—using idiomatic Clojure without pulling in heavy transitive dependencies.

## Key Features

- **Zero Transitive Dependencies** — Built using pure Clojure (`org.clojure/clojure`) and standard JDK classes (`java.security.SecureRandom`, etc.) to keep your dependency tree clean.
- **Consistent, Idiomatic API** — Every identifier strategy follows uniform patterns and Clojure conventions.
- **High Performance & Safety** — Implemented with thread-safe secure entropy providers and optimized string/byte manipulations.
- **Unified Toolkit** — Avoid pulling in different libraries for ULIDs, NanoIDs, KSUIDs, and HashIDs. Identika covers them all.

---

## Supported & Planned Identifier Formats

| Format | Sortable? | Length / Representation | Key Advantages & Best Use Case | Status |
| :--- | :---: | :--- | :--- | :--- |
| **ULID** | **Yes** | 26 chars (Base32) / 16 bytes | Millisecond-precision sorting, Crockford Base32 (URL-safe, case-insensitive). Excellent for DB keys. | 🚧 Partial |
| **UUIDv7** | **Yes** | 36 chars (Hex-Hyphens) / 16 bytes | RFC 9562 time-ordered UUID. Seamless drop-in replacement for traditional UUIDs. | ⏳ Planned |
| **KSUID** | **Yes** | 27 chars (Base62) / 20 bytes | 32-bit second-precision timestamp + 128-bit random payload. Extra entropy, no special chars. | ⏳ Planned |
| **NanoID** | **No** | Customizable (default 21 chars) | Extremely compact, highly secure, custom alphabets. Best for user-facing short links/IDs. | ⏳ Planned |
| **CUID2** | **No** | Customizable (default 24 chars) | Secure, collision-resistant, horizontally-scalable IDs. Great for distributed web systems. | ⏳ Planned |
| **HashID** | **No** | Variable based on input integer | Reversible obfuscation. Encodes auto-incrementing database IDs into short, unique salts. | ⏳ Planned |
| **FlakeID**| **Yes** | 64-bit Long / Hex | Snowflake-style distributed ID. Highly efficient for massive scales (64-bit integer space). | ⏳ Planned |

---

## Installation

Add Identika to your `deps.edn` dependencies:

```clojure
com.identika/identika {:mvn/version "0.0.1"}
```

---

## Usage

Currently, **ULID** is partially implemented, and more formats are under active development.

### ULID (Universally Unique Lexicographically Sortable Identifier)

ULIDs are 128-bit compatibility-compatible identifiers consisting of:
- A **48-bit timestamp** (millisecond epoch)
- An **80-bit random component** (generated using `SecureRandom`)

Encoded using Crockford's Base32 (excluding I, L, O, and U to avoid visual confusion).

#### Basic Generation

```clojure
(require '[identika.core :as identika])

;; Generate a standard ULID for the current time
(identika/ulid)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"

;; Generate a ULID for a specific unix timestamp (millisecond epoch)
(identika/ulid 1781290640998)
;; => "01ARZ3NDEKTSV4RRFFQ69G5FAV"
```

#### Validation & Parsing

```clojure
;; Check if a string is a valid Crockford Base32 ULID
(identika/valid-ulid? "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => {:success "01ARZ3NDEKTSV4RRFFQ69G5FAV"}

(identika/valid-ulid? "invalid-ulid!")
;; => {:error "ULID invalid length"}

;; Extract the millisecond timestamp as a long epoch value
(identika/ulid->time "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => 1781290640998

;; Decode a ULID string into its BigInteger representation/bytes
(identika/ulid->bytes "01ARZ3NDEKTSV4RRFFQ69G5FAV")
;; => 2362481014167527627471804791550269387
```

#### ULID API Status

| Function | Status | Description |
| :--- | :--- | :--- |
| `(ulid)` | ✓ Implemented | Generates a ULID string using the current system timestamp |
| `(ulid timestamp)` | ✓ Implemented | Generates a ULID string with a specific Unix epoch timestamp (ms) |
| `(valid-ulid? s)` | ✓ Implemented | Validates a ULID string. Returns `{:success s}` or `{:error msg}` |
| `(ulid->time ulid)` | ✓ Implemented | Extracts the millisecond epoch timestamp from a ULID |
| `(ulid->bytes ulid)` | ✓ Implemented | Decodes a ULID string into its raw representation |
| `(bytes->ulid byte-arr)` | 🚧 In Progress | Encodes a 16-byte array back into a ULID string |
| `(ulid-succ ulid)` | 🚧 In Progress | Returns the next lexicographical ULID (incrementing entropy) |
| `(monotonic-ulid state)` | 🚧 In Progress | Generates monotonically increasing ULIDs via state atom |

---

## Roadmap

- [x] **ULID** — Core implementation (generation, validation, timestamp extraction, byte decoding)
- [ ] **ULID** — bytes→ULID encoding, successor, monotonic generation
- [ ] **NanoID** — compact, URL-safe, customizable-length IDs
- [ ] **KSUID** — K-Sortable Unique Identifier (timestamp + payload)
- [ ] **HashID** — obfuscated, reversible, salt-based IDs
- [ ] **CUID2** — secure, collision-resistant IDs for horizontal scaling
- [ ] **UUIDv7** — time-ordered UUIDs (RFC 9562)
- [ ] **FlakeID** — distributed, time-sorted, snowflake-style IDs

---

## Development & Testing

### Running Tests

Identika uses [Kaocha](https://github.com/lambdaisland/kaocha) for testing:

```bash
# Run unit tests
clojure -M:test/unit
```

### REPL Workflow

To start a REPL integrated with CIDER:

```bash
clojure -M:repl
```

---

## License

Distributed under the MIT License.

