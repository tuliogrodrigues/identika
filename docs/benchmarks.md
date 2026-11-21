## Benchmarks

Identika ships a small, dependency-free benchmark harness that compares the
four implemented formats: **UUID v4**, **UUIDv7**, **ULID**, and **KSUID**.

The goal is to compare the implementations against each other on the same JVM,
not to produce JMH-grade absolute numbers.

### Running

```bash
clojure -M:bench
```

Two environment variables tune the run:

| Variable | Default | Meaning |
| :--- | :--- | :--- |
| `BENCH_ROUNDS` | `7` | Measured rounds per operation |
| `BENCH_TARGET_MS` | `50` | Target duration of one measured round, in milliseconds |

For example:

```bash
BENCH_ROUNDS=9 BENCH_TARGET_MS=100 clojure -M:bench
```

### Methodology

For every format and operation, the harness:

1. Warms up for roughly 200 ms to load classes and let the JIT settle.
2. Calibrates an iteration count so that one batch runs for roughly
   `BENCH_TARGET_MS`.
3. Runs three more warmup batches at the calibrated size.
4. Measures `BENCH_ROUNDS` batches and reports the median throughput
   (ops/sec) and median latency (ns/op).

Each benchmarked closure writes its result into a `volatile`, which prevents
the JIT from eliminating the call as dead code. Fixtures such as a pre-generated
ID and its decoded byte array are created once, outside the measured loop.

The benchmark covers:

| Operation | UUID v4 | UUIDv7 | ULID | KSUID |
| :--- | :---: | :---: | :---: | :---: |
| `gen` (current time) | ✅ | ✅ | ✅ | ✅ |
| `gen` (fixed timestamp) | — | ✅ | ✅ | ✅ |
| `valid?` | ✅ | ✅ | ✅ | ✅ |
| `timestamp` | — | ✅ | ✅ | ✅ |
| `decode` | ✅ | ✅ | ✅ | ✅ |
| `encode` | ✅ | ✅ | ✅ | ✅ |
| `decode + encode` | ✅ | ✅ | ✅ | ✅ |
| `monotonic` | — | ✅ | ✅ | ✅ |
| `next` | — | — | ✅ | ✅ |

> `gen` with a fixed timestamp uses milliseconds for UUIDv7 and ULID, and
> seconds for KSUID, matching each format's native timestamp unit.

### Sample results

Captured on:

- **CPU:** Intel Core i7-9750H @ 2.60 GHz
- **OS:** Linux 6.12
- **JVM:** OpenJDK 64-Bit Server VM 25.0.1
- **Settings:** 7 rounds, 50 ms target per round

#### Throughput (median ops/sec; higher is better)

| Operation | UUID v4 | UUIDv7 | ULID | KSUID |
| :--- | ---: | ---: | ---: | ---: |
| `gen` (current time) | 239,134 | 244,773 | 52,502 | 150,128 |
| `gen` (fixed timestamp) | — | 248,440 | 53,120 | 164,584 |
| `valid?` | 2,834,647 | 2,861,604 | 388,054 | 422,614 |
| `timestamp` | — | 1,689,190 | 257,836 | 259,461 |
| `decode` | 231,727 | 941,407 | 214,854 | 252,327 |
| `encode` | 434,334 | 440,027 | 195,249 | 169,297 |
| `decode + encode` | 138,186 | 287,531 | 100,926 | 101,576 |
| `monotonic` | — | 246,512 | 65,649 | 63,028 |
| `next` | — | — | 97,967 | 102,097 |

#### Latency (median ns/op; lower is better)

| Operation | UUID v4 | UUIDv7 | ULID | KSUID |
| :--- | ---: | ---: | ---: | ---: |
| `gen` (current time) | 4,182 | 4,085 | 19,047 | 6,661 |
| `gen` (fixed timestamp) | — | 4,025 | 18,825 | 6,076 |
| `valid?` | 353 | 349 | 2,577 | 2,366 |
| `timestamp` | — | 592 | 3,878 | 3,854 |
| `decode` | 4,315 | 1,062 | 4,654 | 3,963 |
| `encode` | 2,302 | 2,273 | 5,122 | 5,907 |
| `decode + encode` | 7,237 | 3,478 | 9,908 | 9,845 |
| `monotonic` | — | 4,057 | 15,233 | 15,866 |
| `next` | — | — | 10,208 | 9,795 |

### Reading the results

- **Generation** is dominated by `SecureRandom` and the encoding step. UUID v4
  and UUIDv7 are close because both draw 16 random bytes and format hex; ULID
  pays for per-byte random draws plus Crockford Base32; KSUID pays for Base62
  division over a 160-bit integer.
- **Validation** is fastest for the UUID formats because it is a single regex
  match. ULID and KSUID walk the string and decode the value.
- **UUIDv7 decode** is faster than the other decoders because it is a direct
  hex parse, while ULID and KSUID go through `BigInteger`.
- These numbers move with JVM version, CPU, and load. Run the benchmark on a
  quiet machine and compare ratios between formats rather than treating any
  single number as a fixed cost.
