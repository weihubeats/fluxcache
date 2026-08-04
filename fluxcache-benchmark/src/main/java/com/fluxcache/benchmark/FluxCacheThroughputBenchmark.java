package com.fluxcache.benchmark;

import com.fluxcache.benchmark.support.BenchmarkSetup;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Production-like read mix (90% L1 hit / 10% L2 hit) aggregated throughput, compared with
 * a pure-remote (Redis) reads and pure-local (Caffeine) caches.
 *
 * @author : wh
 * @date : 2026/8/4
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(8)
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class FluxCacheThroughputBenchmark {

    private static final int WARM_KEYS = 90;
    private static final int COLD_KEYS = 10;

    private static String keyOf(int index) {
        return "throughput-key-" + index;
    }

    private static int pickKey() {
        return ThreadLocalRandom.current().nextInt(WARM_KEYS + COLD_KEYS);
    }

    @State(Scope.Benchmark)
    public static class FluxCacheState {

        BenchmarkSetup.Env env;
        FluxCaffeineCache firstCache;

        @Setup(Level.Trial)
        public void setup() {
            env = new BenchmarkSetup.Env();
            for (int i = 0; i < WARM_KEYS; i++) {
                env.cache.put(keyOf(i), "v" + i);
            }
            for (int i = 0; i < WARM_KEYS + COLD_KEYS; i++) {
                env.redis.put(keyOf(i), "v" + i);
            }
            firstCache = env.firstCache();
        }
    }

    @State(Scope.Benchmark)
    public static class PureRedisState {

        BenchmarkSetup.Env env;

        @Setup(Level.Trial)
        public void setup() {
            env = new BenchmarkSetup.Env();
            for (int i = 0; i < WARM_KEYS + COLD_KEYS; i++) {
                env.redis.put(keyOf(i), "v" + i);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class LocalCacheState {

        com.github.benmanes.caffeine.cache.Cache<String, Object> caffeine;
        com.alicp.jetcache.Cache<String, Object> jetCache;

        @Setup(Level.Trial)
        public void setup() {
            caffeine = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .initialCapacity(16)
                    .maximumSize(100_000)
                    .build();
            jetCache = com.alicp.jetcache.embedded.CaffeineCacheBuilder.createCaffeineCacheBuilder()
                    .limit(100_000)
                    .buildCache();
            for (int i = 0; i < WARM_KEYS; i++) {
                caffeine.put(keyOf(i), "v" + i);
                jetCache.put(keyOf(i), "v" + i);
            }
        }
    }

    @Benchmark
    public Object fluxCacheHit90(FluxCacheState state) throws Throwable {
        int key = pickKey();
        if (key >= WARM_KEYS) {
            // cold key: keep L1 cold so the call exercises the L2 (simulated Redis) path
            state.firstCache.evictDirectly(keyOf(key));
        }
        return state.env.invokeWithKey(keyOf(key));
    }

    @Benchmark
    public Object pureRedisAllRemote(PureRedisState state) {
        return state.env.redis.get(keyOf(pickKey()));
    }

    @Benchmark
    public Object springCaffeineAllLocal(LocalCacheState state) {
        return state.caffeine.getIfPresent(keyOf(pickKey()));
    }

    @Benchmark
    public Object jetCacheAllLocal(LocalCacheState state) {
        return state.jetCache.get(keyOf(pickKey()));
    }
}