package com.fluxcache.core.config;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drops the oldest queued monitor event on overflow and keeps a visible counter,
 * so silent stats loss under traffic spikes can be detected. Never throws back
 * into the caller (read path).
 *
 * @author : wh
 */
@Slf4j
public class CountingDiscardOldestPolicy implements RejectedExecutionHandler {

    private final AtomicLong discarded = new AtomicLong();

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            count(1);
            return;
        }
        executor.getQueue().poll();
        int dropped = 1;
        try {
            executor.execute(r);
        } catch (RejectedExecutionException e) {
            dropped = 2;
        }
        count(dropped);
    }

    private void count(int delta) {
        long total = discarded.addAndGet(delta);
        if (total % 1000 < delta) {
            log.warn("[FluxCache] monitor queue overflow, events discarded total={}", total);
        }
    }

    public long getDiscardedCount() {
        return discarded.get();
    }
}
