# Identika

**A lightweight, zero-dependency Clojure toolkit for generating and parsing unique identifiers (ULID, UUID v4, UUIDv7, and more).**

---

Identika provides a collection of modern unique identifier strategies, each in its own self-contained namespace. Designed for database primary keys, distributed tracing, log collation, and client-safe obfuscation — using idiomatic Clojure without pulling in heavy transitive dependencies.

## Key Features

- **Zero Transitive Dependencies** — Built using pure Clojure (`org.clojure/clojure`) and standard JDK classes (`java.security.SecureRandom`, etc.).
- **Self-Contained Namespaces** — Each strategy is independent. Import only what you need.
- **Thread-Safe** — All entropy sources use `SecureRandom` and are shared across calls.
- **Pluggable** — Every strategy ships as its own namespace with no shared machinery, so future formats (NanoID, KSUID, CUID2) add no weight to the ones you don't use.

---

## Supported & Planned Identifier Formats

| Format | Sortable? | Length / Representation | Key Advantages & Best Use Case | Status |
| :--- | :---: | :--- | :--- | :--- |
| **UUID v4** | **No** | 36 chars (hex-hyphens) / 16 bytes | RFC 4122 random UUID. Universal standard, widely supported. | ✅ Complete |
| **ULID** | **Yes** | 26 chars (Crockford Base32) / 16 bytes | Millisecond-precision sorting, URL-safe, case-insensitive. Excellent for DB keys. | ✅ Complete |
| **UUIDv7** | **Yes** | 36 chars (Hex-Hyphens) / 16 bytes | RFC 9562 time-ordered UUID. Seamless drop-in for traditional UUIDs. | ✅ Complete |
| **KSUID** | **Yes** | 27 chars (Base62) / 20 bytes | 32-bit second-precision timestamp + 128-bit random payload. | ⏳ Planned |
| **NanoID** | **No** | Customizable (default 21 chars) | Compact, highly secure, custom alphabets. Great for user-facing short IDs. | ⏳ Planned |
| **CUID2** | **No** | Customizable (default 24 chars) | Secure, collision-resistant, horizontally-scalable IDs. | ⏳ Planned |
| **HashID** | **No** | Variable based on input integer | Reversible obfuscation for auto-incrementing IDs. | ⏳ Planned |
| **FlakeID**| **Yes** | 64-bit Long / Hex | Snowflake-style distributed ID (64-bit integer space). | ⏳ Planned |

---

## Installation

Add Identika to your `deps.edn` dependencies:

```clojure
org.clojars.rodriguesgot/identika {:mvn/version "0.2.0"}
```

---

## Usage

### Quick Start

```clojure
(require '[identika.uuid :as uuid]
         '[identika.uuid7 :as uuid7]
         '[identika.ulid :as ulid])

;; Generate a UUID v4
(uuid/gen)
;; => "550e8400-e29b-41d4-a716-446655440000"

;; Generate a time-ordered UUIDv7
(uuid7/gen)
;; => "0190f0e2-3b9a-7c4d-9e5f-8a1b2c3d4e5f"

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

### UUIDv7 (RFC 9562)

Time-ordered UUIDs with the same 36-character hex-hyphenated shape as UUID v4, so they drop straight into existing UUID columns, indexes, and clients. 128 bits laid out as:

- **48 bits** of big-endian millisecond Unix timestamp (bytes 0-5)
- **12 bits** of randomness (`rand_a`, bytes 6-7), with version 7 (`0111`) in byte 6
- **62 bits** of randomness (`rand_b`, bytes 8-15), with variant `10` in byte 8

Because the timestamp occupies the most significant bits, UUIDv7 values sort chronologically as plain strings.

#### Generation

```clojure
(require '[identika.uuid7 :as uuid7])

;; Generate using current system time
(uuid7/gen)
;; => "019ebd32-2666-7168-b09b-2240203908ea"

;; Generate with a specific timestamp (millisecond epoch)
(uuid7/gen 1781290640000)
;; => "019ebd32-2280-7168-b09b-2240203908ea"

;; Timestamps must fit in 48 bits, otherwise gen throws
(uuid7/gen 281474976710656)
;; => throws IllegalArgumentException
```

#### Validation

```clojure
(uuid7/valid? "019ebd32-2280-7168-b09b-2240203908ea")
;; => true

;; UUID v4 and ULID strings are rejected — each namespace validates its own format
(uuid7/valid? "550e8400-e29b-41d4-a716-446655440000")
;; => false
```

#### Timestamp Extraction

```clojure
;; Extract the millisecond timestamp from the leading 48 bits
(uuid7/timestamp "019ebd32-2280-7168-b09b-2240203908ea")
;; => 1781290640000

;; The canonical example from RFC 9562 §5.7
(uuid7/timestamp "017f22e2-79b0-7cc3-98c4-dc0c0c07398f")
;; => 1645557742000

;; Returns nil for anything that is not a UUIDv7
(uuid7/timestamp "not-a-uuid")
;; => nil
```

#### Encode / Decode (String ↔ byte[])

```clojure
;; Decode a UUIDv7 string into a 16-byte array
(uuid7/decode "019ebd32-2280-7168-b09b-2240203908ea")
;; => #object["[B" ...]

;; Encode a 16-byte array back into a UUIDv7 string
(uuid7/encode (byte-array 16 (range 16)))
;; => "00010203-0405-0607-0809-0a0b0c0d0e0f"
```

#### Monotonic

Plain `gen` is time-ordered but **not** monotonic — two UUIDs generated in the same millisecond can sort in either order, because their random payloads are unrelated. `monotonic` closes that gap by incrementing the 74-bit random payload instead of re-rolling it, which is the bit-increment counter method from [RFC 9562 §6.2.1](https://www.rfc-editor.org/rfc/rfc9562.html#name-fixed-length-dedicated-counte):

```clojure
;; The state atom holds the last UUIDv7 handed out
(def state (atom nil))

(uuid7/monotonic state)
;; => "01a101c5-59d2-7f20-b733-56ea3b3d2bb6"

;; Same millisecond: the payload is incremented, so ordering is guaranteed
(uuid7/monotonic state)
;; => "01a101c5-59d2-7f20-b733-56ea3b3d2bb7"
```

Details worth knowing:

- The counter runs through `rand_b` first, carries into `rand_a`, and never disturbs the version nibble or variant bits — every value returned is still a valid UUIDv7.
- If all 74 payload bits are exhausted, the timestamp advances by one millisecond. That needs 2^74 IDs inside a single millisecond, so in practice it never fires.
- If the wall clock jumps backwards, generation continues from the last timestamp rather than regressing.

Use `monotonic` (via a dedicated atom per generator) when you need a strict insertion order — index locality in B-tree primary keys, for instance. Use plain `gen` when you want independence between calls and are happy with millisecond-level ordering only.

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

### `identika.uuid7`

| Function | Description |
| :--- | :--- |
| `(gen)` / `(gen millis)` | Generate a UUIDv7 string at the current time, or at an explicit millisecond epoch; throws if `millis` does not fit in 48 bits |
| `(valid? s)` | Returns `true` if `s` is a valid RFC 9562 UUIDv7 (version 7, variant `10xx`); accepts uppercase hex |
| `(timestamp s)` | Extract the millisecond Unix timestamp from the leading 48 bits, or `nil` if invalid |
| `(decode s)` | Decode UUIDv7 string → 16-byte array; `nil` if invalid |
| `(encode byte-arr)` | Encode 16-byte array → UUIDv7 string; throws if not 16 bytes |
| `(monotonic state-atom)` | Monotonically increasing UUIDv7s via state atom, incrementing the random payload within a millisecond |

---

## Building & Deploying

### Build the JAR

```bash
clojure -T:build jar
```

Produces `target/identika-<version>.jar`. Version defaults to `0.2.0` and can be overridden with the `PROJECT_VERSION` environment variable:

```bash
PROJECT_VERSION=1.0.0 clojure -T:build jar
```

### Install Locally

```bash
clojure -T:build install
```

This installs the jar to your local `~/.m2/repository` so other projects on your machine can depend on it.

### Deploy to Clojars

Deploys use [`slipset/deps-deploy`](https://github.com/slipset/deps-deploy) directly from the build — no Maven required.

1. Create a [deploy token](https://clojars.org/tokens) on Clojars (account passwords are no longer accepted for deploys; copy the token value when it is shown — it is displayed only once).
2. Export your Clojars username and the token, then run:

```bash
CLOJARS_USERNAME=<clojars-username> \
CLOJARS_PASSWORD=<deploy-token> \
clojure -T:build deploy
```

This builds the jar and publishes it to `https://clojars.org/repo` under `org.clojars.rodriguesgot/identika`.

> **Note:** the `org.clojars.<username>` group is verified automatically for your account, so no additional group setup is needed.

### Release via GitHub Actions

Pushing a tag that starts with `v` runs the [release workflow](.github/workflows/release.yml), which lints, tests, and deploys to Clojars using the version encoded in the tag:

```bash
git tag v0.2.0
git push origin v0.2.0
```

The workflow requires these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| :--- | :--- |
| `CLOJARS_USERNAME` | Your Clojars username |
| `CLOJARS_PASSWORD` | A Clojars [deploy token](https://clojars.org/tokens) |

### Deploy to Maven Central

Requires GPG signing and a Sonatype account. After building:

```bash
mvn deploy:deploy-file \
  -Dfile=target/identika-<version>.jar \
  -DpomFile=target/classes/META-INF/maven/org.clojars.rodriguesgot/identika/pom.xml \
  -DrepositoryId=sonatype \
  -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ \
  -Dgpg.sign=true
```

### Clean Build Artifacts

```bash
clojure -T:build clean
```

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
- [x] **UUIDv7** — Time-ordered UUIDs (RFC 9562): generation, validation, timestamp extraction, encode/decode, monotonic generation
- [ ] **KSUID** — K-Sortable Unique Identifier
- [ ] **NanoID** — Compact, URL-safe, customizable-length IDs
- [ ] **HashID** — Reversible, salt-based ID obfuscation
- [ ] **CUID2** — Secure, collision-resistant IDs for horizontal scaling
- [ ] **FlakeID** — Distributed, time-sorted, snowflake-style IDs

---

## License

Distributed under the MIT License.
