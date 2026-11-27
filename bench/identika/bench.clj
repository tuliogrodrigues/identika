(ns identika.bench
  "Lightweight, dependency-free microbenchmarks for Identika's identifier
  formats.

  Run with:

    clojure -M:bench

  Environment variables:

    BENCH_ROUNDS     measured rounds per operation (default 7)
    BENCH_TARGET_MS  target duration of one measured round, in ms (default 50)

  The harness calibrates each operation to run for roughly BENCH_TARGET_MS,
  warms it up, then reports the median throughput and latency over
  BENCH_ROUNDS rounds. It is intentionally simple: use the numbers to compare
  the implementations against each other, not as absolute JMH-grade results."
  (:require [identika.ksuid :as ksuid]
            [identika.ulid :as ulid]
            [identika.uuid :as uuid]
            [identika.uuid7 :as uuid7])
  (:import [java.util Locale]))

(defn- env-long
  "Read a positive long from an environment variable, falling back to default."
  ^long [name ^long default]
  (let [value (Long/parseLong (or (System/getenv name) (str default)))]
    (when-not (pos? value)
      (throw (IllegalArgumentException.
               (str name " must be positive, got " value))))
    value))

(def ^:private rounds
  (env-long "BENCH_ROUNDS" 7))

(def ^:private target-ms
  (env-long "BENCH_TARGET_MS" 50))

;; ──────────────────────────────────────────────
;; Measurement harness
;; ──────────────────────────────────────────────

(defn- run-batch
  "Run f n times and return the elapsed nanoseconds. Writing the result into a
  volatile keeps the JIT from eliminating the calls as dead code."
  ^long [f ^long n]
  (let [sink  (volatile! nil)
        start (System/nanoTime)]
    (dotimes [_ n]
      (vreset! sink (f)))
    (- (System/nanoTime) start)))

(defn- warmup
  "Run f for roughly 200 ms so that classes load and the JIT settles before
  calibration. The clock is only read between batches, not per call."
  [f]
  (let [deadline (+ (System/nanoTime) (* 200 1000000))]
    (loop []
      (run-batch f 1000)
      (when (< (System/nanoTime) deadline)
        (recur)))))

(defn- calibrate
  "Find an iteration count whose batch runs for at least target-ns. Starts at
  16 so that even a very fast operation gets a batch large enough to time
  meaningfully."
  ^long [f ^long target-ns]
  (loop [n 16]
    (if (>= (run-batch f n) target-ns)
      n
      (recur (* n 2)))))

(defn- median [xs]
  (let [sorted (vec (sort xs))
        n      (count sorted)]
    (when (pos? n)
      (if (odd? n)
        (nth sorted (quot n 2))
        (/ (+ (nth sorted (dec (quot n 2)))
              (nth sorted (quot n 2)))
           2.0)))))

(defn- benchmark
  "Calibrate, warm up, and measure f. Returns median ops/sec and ns/op."
  [f]
  (warmup f)
  (let [target-ns (* target-ms 1000000)
        n         (calibrate f target-ns)]
    ;; Warm up again with the calibrated batch size.
    (dotimes [_ 3]
      (run-batch f n))
    (let [samples (vec (repeatedly rounds #(run-batch f n)))
          ops-sec (mapv (fn [elapsed]
                          (/ (* (double n) 1e9) (double elapsed)))
                        samples)
          ns-op   (mapv (fn [elapsed]
                          (/ (double elapsed) (double n)))
                        samples)]
      {:iterations n
       :ops-sec    (median ops-sec)
       :ns-op      (median ns-op)})))

;; ──────────────────────────────────────────────
;; Benchmark suite
;; ──────────────────────────────────────────────

(def ^:private operations
  [[:gen        "gen (current time)"]
   [:gen-fixed  "gen (fixed timestamp)"]
   [:valid?     "valid?"]
   [:timestamp  "timestamp"]
   [:decode     "decode"]
   [:encode     "encode"]
   [:roundtrip  "decode + encode"]
   [:monotonic  "monotonic"]
   [:next       "next"]])

(defn- make-specs
  "Build the per-format benchmark closures. Fixtures are generated once so
  that each measured operation only exercises the function under test."
  []
  (let [uuid-id  (uuid/gen)
        uuid-ba  (uuid/decode uuid-id)

        uuid7-id (uuid7/gen)
        uuid7-ba (uuid7/decode uuid7-id)

        ulid-id  (ulid/gen)
        ulid-ba  (ulid/decode ulid-id)

        ksuid-id  (ksuid/gen)
        ksuid-ba  (ksuid/decode ksuid-id)

        uuid7-state (atom nil)
        ulid-state  (atom nil)
        ksuid-state (atom nil)

        ;; Each format's native timestamp unit.
        fixed-ms 1781290640000
        fixed-s  1781290640]
    [{:format "UUID v4"
      :ops    {:gen       #(uuid/gen)
               :valid?    #(uuid/valid? uuid-id)
               :decode    #(uuid/decode uuid-id)
               :encode    #(uuid/encode uuid-ba)
               :roundtrip #(uuid/encode (uuid/decode uuid-id))}}

     {:format "UUIDv7"
      :ops    {:gen       #(uuid7/gen)
               :gen-fixed #(uuid7/gen fixed-ms)
               :valid?    #(uuid7/valid? uuid7-id)
               :timestamp #(uuid7/timestamp uuid7-id)
               :decode    #(uuid7/decode uuid7-id)
               :encode    #(uuid7/encode uuid7-ba)
               :roundtrip #(uuid7/encode (uuid7/decode uuid7-id))
               :monotonic #(uuid7/monotonic uuid7-state)}}

     {:format "ULID"
      :ops    {:gen       #(ulid/gen)
               :gen-fixed #(ulid/gen fixed-ms)
               :valid?    #(ulid/valid? ulid-id)
               :timestamp #(ulid/timestamp ulid-id)
               :decode    #(ulid/decode ulid-id)
               :encode    #(ulid/encode ulid-ba)
               :roundtrip #(ulid/encode (ulid/decode ulid-id))
               :monotonic #(ulid/monotonic ulid-state)
               :next      #(ulid/next-ulid ulid-id)}}

     {:format "KSUID"
      :ops    {:gen       #(ksuid/gen)
               :gen-fixed #(ksuid/gen fixed-s)
               :valid?    #(ksuid/valid? ksuid-id)
               :timestamp #(ksuid/timestamp ksuid-id)
               :decode    #(ksuid/decode ksuid-id)
               :encode    #(ksuid/encode ksuid-ba)
               :roundtrip #(ksuid/encode (ksuid/decode ksuid-id))
               :monotonic #(ksuid/monotonic ksuid-state)
               :next      #(ksuid/next-ksuid ksuid-id)}}]))

(defn- run-suite [specs]
  (reduce
   (fn [results {:keys [format ops]}]
     (binding [*out* *err*]
       (println (str "Benchmarking " format "...")))
     (reduce
      (fn [results [op _]]
        (if-let [f (get ops op)]
          (update results op assoc format (benchmark f))
          results))
      results
      operations))
   {}
   specs))

;; ──────────────────────────────────────────────
;; Reporting
;; ──────────────────────────────────────────────

(defn- fmt-number [x]
  (if (nil? x)
    "-"
    (String/format Locale/US "%,.0f" (to-array [(double x)]))))

(defn- print-table [title results specs value-key]
  (let [formats  (mapv :format specs)
        op-width 26
        col-width 20
        width    (+ op-width (* col-width (count formats)))]
    (println title)
    (println)
    (print (format (str "%-" op-width "s") "Operation"))
    (doseq [f formats]
      (print (format (str "%" col-width "s") f)))
    (println)
    (println (apply str (repeat width "-")))
    (doseq [[op label] operations]
      (print (format (str "%-" op-width "s") label))
      (doseq [f formats]
        (let [result (get-in results [op f])]
          (print (format (str "%" col-width "s")
                         (if result
                           (fmt-number (get result value-key))
                           "-")))))
      (println))
    (println)))

(defn- print-report [results specs]
  (println)
  (println (format "Identika benchmark — %d rounds, %d ms target per round"
                   rounds target-ms))
  (println (format "JVM: %s %s"
                   (System/getProperty "java.vm.name")
                   (System/getProperty "java.version")))
  (println)

  (print-table "Throughput (median ops/sec; higher is better)"
               results specs :ops-sec)

  (print-table "Latency (median ns/op; lower is better)"
               results specs :ns-op)

  (println "Notes:")
  (println "  • Each operation is calibrated to run for about"
           (str target-ms " ms per round, then measured over "
                rounds " rounds."))
  (println "  • \"gen (fixed timestamp)\" uses milliseconds for UUIDv7/ULID")
  (println "    and seconds for KSUID, matching each format's native unit.")
  (println "  • \"-\" means the operation is not supported by that format.")
  (println "  • These are indicative JVM microbenchmarks, not JMH results.")
  (println "    Compare relative numbers, not absolute ones."))

;; ──────────────────────────────────────────────
;; Entry point
;; ──────────────────────────────────────────────

(defn -main [& _]
  (let [specs   (make-specs)
        results (run-suite specs)]
    (print-report results specs)))
