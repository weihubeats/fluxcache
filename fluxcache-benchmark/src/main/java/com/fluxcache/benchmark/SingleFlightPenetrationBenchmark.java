package com.fluxcache.benchmark;

import com.fluxcache.benchmark.support.BenchmarkSetup;
import com.fluxcache.benchmark.support.SimulatedDb;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.impl.FluxMultiLevelCache;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concurrent cache-breakdown (击穿) protection. 16 threads hammer the <b>same cold key</b>;
 * the datasource (simulated MySQL) only allows 4 parallel queries and each takes 2 ms.
 *
 * <p>Every operation invalidates the local cache first to force a full miss, then goes
 * through the annotation path. With single-flight ON the wave collapses into one datasource
 * query; with it OFF every request hits the datasource and is serialized by the pool.
 *
 * @author : wh
 * @date : 2026/8/4
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(16)
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class SingleFlightPenetrationBenchmark {

    private static final String PEN_KEY = "breakdown-key-1";

    /**
     * Shared per-fork environment. Single-flight is enabled.
     */
    @State(Scope.Benchmark)
    public static class SingleFlightOnState {

        BenchmarkSetup.Env env;
        FluxCaffeineCache firstCache;
        SimulatedDb db;
        AtomicLong loadCalls = new AtomicLong();

        @Setup(Level.Trial)
        public void setup() {
            env = new BenchmarkSetup.Env(true, FluxCacheLevel.FirstCacheable);
            firstCache = env.firstCache();
            db = new SimulatedDb();
        }

        @TearDown(Level.Trial)
        public void report() throws java.io.IOException {
            String dir = System.getProperty("jmh.report.dir", "target");
            java.nio.file.Files.write(java.nio.file.Paths.get(dir, "single-flight-on-dbqueries.txt"),
                    String.valueOf(db.queryCalls()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("[report] singleFlight=ON  dbQueries=" + db.queryCalls() + "  (expected: ~ops/16)");
        }
    }

    /**
     * Shared per-fork environment. Single-flight is disabled.
     */
    @State(Scope.Benchmark)
    public static class SingleFlightOffState {

        BenchmarkSetup.Env env;
        FluxCaffeineCache firstCache;
        SimulatedDb db;

        @Setup(Level.Trial)
        public void setup() {
            env = new BenchmarkSetup.Env(false, FluxCacheLevel.FirstCacheable);
            firstCache = env.firstCache();
            db = new SimulatedDb();
        }

        @TearDown(Level.Trial)
        public void report() throws java.io.IOException {
            String dir = System.getProperty("jmh.report.dir", "target");
            java.nio.file.Files.write(java.nio.file.Paths.get(dir, "single-flight-off-dbqueries.txt"),
                    String.valueOf(db.queryCalls()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("[report] singleFlight=OFF dbQueries=" + db.queryCalls() + "  (expected: ~ops)");
        }
    }

    @Benchmark
    public Object singleFlightOn(SingleFlightOnState state) throws Throwable {
        state.firstCache.evictDirectly(PEN_KEY);
        return state.env.invokeWithKey(PEN_KEY, () -> state.db.load(PEN_KEY));
    }

    @Benchmark
    public Object singleFlightOff(SingleFlightOffState state) throws Throwable {
        state.firstCache.evictDirectly(PEN_KEY);
        return state.env.invokeWithKey(PEN_KEY, () -> state.db.load(PEN_KEY));
    }
}