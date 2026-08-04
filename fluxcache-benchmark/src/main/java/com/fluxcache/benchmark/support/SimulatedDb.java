package com.fluxcache.benchmark.support;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Simulated slow datasource (e.g. MySQL) with bounded concurrency, used to model a
 * production DB whose connection pool caps parallel executions.
 *
 * <p>Default: capacity 4 concurrent executions, 2 ms per query. With no single-flight
 * protection, N concurrent misses are serialized into N/4 batches and each request pays
 * the full queueing time; single-flight collapses the wave into one query.
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class SimulatedDb {

    public static final long QUERY_NANOS = 2_000_000L;

    private final Semaphore capacity;

    private final long queryNanos;

    private final AtomicLong queryCalls = new AtomicLong();

    public SimulatedDb() {
        this(4, QUERY_NANOS);
    }

    public SimulatedDb(int capacity, long queryNanos) {
        this.capacity = new Semaphore(capacity);
        this.queryNanos = queryNanos;
    }

    public String load(String key) {
        queryCalls.incrementAndGet();
        capacity.acquireUninterruptibly();
        try {
            LockSupport.parkNanos(queryNanos);
            return "value-for-" + key;
        } finally {
            capacity.release();
        }
    }

    public long queryCalls() {
        return queryCalls.get();
    }
}