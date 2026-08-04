package com.fluxcache.benchmark.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

/**
 * Deterministic pure-Redis baseline.
 *
 * <p>Redis access latency is simulated as a fixed network round-trip (default 2 ms, the typical
 * LAN / same-region round trip). The benchmark runs without an external Redis server so results
 * are reproducible on any machine; real-world numbers scale linearly with the actual RTT.
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class SimulatedRedis {

    private static final long DEFAULT_LATENCY_NANOS = 2_000_000L;

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    private final long latencyNanos;

    public SimulatedRedis() {
        this(DEFAULT_LATENCY_NANOS);
    }

    public SimulatedRedis(long latencyNanos) {
        this.latencyNanos = latencyNanos;
    }

    public Object get(String key) {
        roundTrip();
        return store.get(key);
    }

    public void put(String key, Object value) {
        roundTrip();
        store.put(key, value);
    }

    public void delete(String key) {
        roundTrip();
        store.remove(key);
    }

    public void clear() {
        roundTrip();
        store.clear();
    }

    public long latencyNanos() {
        return latencyNanos;
    }

    private void roundTrip() {
        LockSupport.parkNanos(latencyNanos);
    }
}