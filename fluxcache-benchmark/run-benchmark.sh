#!/usr/bin/env bash
# Build and run the JMH benchmark suite, then dump JSON results into docs/benchmark/.
#
# Usage:
#   ./run-benchmark.sh [benchmark-class-name [jmh-extra-args...]]
#
# Examples:
#   ./run-benchmark.sh                              # full suite
#   ./run-benchmark.sh FluxCacheLatencyBenchmark    # latency only
set -euo pipefail

cd "$(dirname "$0")"

BENCH_CLASS="${1:-}"
EXTRA_ARGS="${@:2}"

mvn -Pbenchmark -pl fluxcache-benchmark -am clean package -Dgpg.skip=true -q

JAR=target/benchmarks.jar
OUT_DIR=../docs/benchmark
mkdir -p "$OUT_DIR"

COMMON_ARGS=( -rf json -f 2 -wi 3 -i 5 )
if [[ -n "$BENCH_CLASS" ]]; then
  COMMON_ARGS=( "$BENCH_CLASS" -rf json -f 2 -wi 3 -i 5 )
fi

echo ">> running: java -jar $JAR ${COMMON_ARGS[*]} ${EXTRA_ARGS[*]}"
java -jar "$JAR" "${COMMON_ARGS[@]}" ${EXTRA_ARGS} \
  -rff "$OUT_DIR/results.json" \
  -o "$OUT_DIR/run.log"

echo ">> results: $OUT_DIR/results.json"
echo ">> log:     $OUT_DIR/run.log"
