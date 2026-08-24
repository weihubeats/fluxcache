package com.fluxcache.core.config;

import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author : wh
 */
public class CountingDiscardOldestPolicyTest {

    private ThreadPoolExecutor pool(int queueSize) {
        return new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize), r -> new Thread(r, "test"),
                new CountingDiscardOldestPolicy());
    }

    @Test
    public void overflow_discardsAndCounts() throws Exception {
        CountingDiscardOldestPolicy policy = new CountingDiscardOldestPolicy();
        AtomicInteger executed = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1), r -> new Thread(r), policy);
        executor.execute(() -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executed.incrementAndGet();
        });
        // fill the single queue slot
        executor.execute(executed::incrementAndGet);
        // rejected: oldest queued task is dropped, this one takes its place
        executor.execute(executed::incrementAndGet);
        long discarded = policy.getDiscardedCount();
        assertEquals(1L, discarded);
        executor.shutdownNow();
    }

    @Test
    public void shutdown_rejectsWithoutThrow() {
        CountingDiscardOldestPolicy policy = new CountingDiscardOldestPolicy();
        ThreadPoolExecutor executor = pool(1);
        executor.shutdownNow();
        policy.rejectedExecution(() -> { }, executor);
        assertEquals(1L, policy.getDiscardedCount());
    }
}
