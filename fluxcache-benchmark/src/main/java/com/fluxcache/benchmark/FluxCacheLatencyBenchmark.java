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
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * End-to-end latency: FluxCache (full annotation path) vs pure Redis (2 ms simulated
 * round trip) vs Spring Cache + Caffeine vs JetCache.
 *
 * <p>Methodology:
 * <ul>
 *   <li>Redis latency is simulated as a fixed 2 ms round trip
 *       ({@link BenchmarkSetup.Env#redis}), the typical same-region / LAN RTT. No external
 *       Redis server is required, so results are reproducible on any machine.</li>
 *   <li>FluxCache numbers include the full annotation interception path (SpEL key resolution,
 *       monitor event, single-flight lookup), which is what production sees.</li>
 * </ul>
 *
 * @author : wh
 * @date : 2026/8/4
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class FluxCacheLatencyBenchmark {

    private static final String HOT_KEY = "hot-key-1";
    private static final String L2_WARMED_KEY = "l2-warmed-key-1";

    @State(Scope.Benchmark)
    public static class FluxCacheState {

        BenchmarkSetup.Env env;
        FluxCaffeineCache firstCache;

        @Setup(Level.Trial)
        public void setup() {
            env = new BenchmarkSetup.Env();
            env.cache.put(HOT_KEY, "value");
            env.redis.put(L2_WARMED_KEY, "value");
            firstCache = env.firstCache();
        }
    }

    @State(Scope.Benchmark)
    public static class RedisState {

        BenchmarkSetup.Env env;

        @Setup(Level.Trial)
        public void setup() {
            env = new BenchmarkSetup.Env();
            env.redis.put(HOT_KEY, "value");
        }
    }

    @State(Scope.Benchmark)
    public static class SpringCaffeineState {

        org.springframework.cache.caffeine.CaffeineCache cache;

        @Setup(Level.Trial)
        public void setup() {
            com.github.benmanes.caffeine.cache.Caffeine<Object, Object> caffeine =
                    com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                            .initialCapacity(16)
                            .maximumSize(100_000);
            cache = new org.springframework.cache.caffeine.CaffeineCache("bench", caffeine.build());
            cache.put(HOT_KEY, "value");
        }
    }

    @State(Scope.Benchmark)
    public static class JetCacheState {

        com.alicp.jetcache.Cache<String, Object> cache;

        @Setup(Level.Trial)
        public void setup() {
            cache = com.alicp.jetcache.embedded.CaffeineCacheBuilder.createCaffeineCacheBuilder()
                    .limit(100_000)
                    .buildCache();
            cache.put(HOT_KEY, "value");
        }
    }

    @Benchmark
    public Object fluxCacheL1Hit(FluxCacheState state) throws Throwable {
        return state.env.invokeWithKey(HOT_KEY);
    }

    @Benchmark
    public Object fluxCacheDirectL1Hit(FluxCacheState state) {
        return state.env.cache.get(HOT_KEY);
    }

    @Benchmark
    public Object fluxCacheL2Hit(FluxCacheState state) throws Throwable {
        // keep L1 cold so every call exercises the L2 (simulated Redis) path
        state.firstCache.evictDirectly(L2_WARMED_KEY);
        return state.env.invokeWithKey(L2_WARMED_KEY);
    }

    @Benchmark
    public Object pureRedisGet(RedisState state) {
        return state.env.redis.get(HOT_KEY);
    }

    @Benchmark
    public Object springCacheCaffeineHit(SpringCaffeineState state) {
        return state.cache.get(HOT_KEY);
    }

    @Benchmark
    public Object jetCacheCaffeineHit(JetCacheState state) {
        return state.cache.get(HOT_KEY);
    }
}