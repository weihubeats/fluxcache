package com.fluxcache.core.monitor;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 滚动窗口：时间跨越后自动切桶，窗口数量受上限约束。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheStaticsTest {

    private static final long HALF_HOUR = 30L * 60 * 1000;

    private void rewindWindow(FluxCacheStatics stats, long millisBehind) throws Exception {
        FluxCacheInfo current = stats.getCurrentBucket().get();
        Field start = FluxCacheInfo.class.getDeclaredField("startTime");
        Field end = FluxCacheInfo.class.getDeclaredField("endTime");
        start.setAccessible(true);
        end.setAccessible(true);
        ((AtomicLong) start.get(current)).set(System.currentTimeMillis() - HALF_HOUR - millisBehind);
        ((AtomicLong) end.get(current)).set(System.currentTimeMillis() - millisBehind);
    }

    @Test
    public void fastPath_withinWindow_accumulates() {
        FluxCacheStatics stats = new FluxCacheStatics();
        stats.incrementHit(2, 10);
        stats.incrementHit(3, 5);
        stats.incrementPut(1, 20);
        FluxCacheInfo b = stats.getCurrentBucket().get();
        assertEquals(5, b.getHit().sum());
        assertEquals(5, b.getRequestCount().sum());
        assertEquals(20, b.getMaxLoadTime().get());
        assertEquals(1, stats.getWindow().size());
    }

    @Test
    public void rotation_createsNewBucket_capsOldEnd() throws Exception {
        FluxCacheStatics stats = new FluxCacheStatics();
        stats.incrementHit(1, 0);
        rewindWindow(stats, 1000);

        stats.incrementHit(1, 0);

        assertEquals(2, stats.getWindow().size());
        FluxCacheInfo oldBucket = stats.getWindow().peekFirst();
        FluxCacheInfo newBucket = stats.getWindow().peekLast();
        // 旧窗口结束时间被固定为 start + 30min，窗口长度稳定
        assertEquals(oldBucket.getStartTime().get() + HALF_HOUR, oldBucket.getEndTime().get());
        assertTrue(newBucket.getStartTime().get() >= oldBucket.getEndTime().get());
    }

    @Test
    public void multiWindowJump_landsInNewBucket_carriesCounts() throws Exception {
        FluxCacheStatics stats = new FluxCacheStatics();
        stats.incrementHit(2, 0);
        rewindWindow(stats, HALF_HOUR * 5 + 1000);

        stats.incrementHit(1, 0);

        // 跨越 5+ 个窗口后数据落在最后一个桶
        assertEquals(1, stats.getCurrentBucket().get().getHit().sum());
        // 旧桶仍然保留原始计数（历史不被覆盖）
        long totalHit = stats.getWindow().stream().mapToLong(b -> b.getHit().sum()).sum();
        assertEquals(3, totalHit);
    }

    @Test
    public void massiveTimeJump_prunesWindowToLimit() throws Exception {
        FluxCacheStatics stats = new FluxCacheStatics();
        rewindWindow(stats, HALF_HOUR * 200);

        stats.incrementEvict(1, 0);

        assertTrue("窗口数量应受 48 上限约束", stats.getWindow().size() <= 48);
        assertEquals(1, stats.getCurrentBucket().get().getEvictCount().sum());
    }

    @Test
    public void nullCurrentBucket_allIncrementsNoOp() throws Exception {
        FluxCacheStatics stats = new FluxCacheStatics();
        java.lang.reflect.Field f = FluxCacheStatics.class.getDeclaredField("currentBucket");
        f.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicReference<FluxCacheInfo>) f.get(stats)).set(null);

        stats.incrementHit(1, 0);
        stats.incrementMissing(1, 0);
        stats.incrementPut(1, 0);
        stats.incrementEvict(1, 0);

        assertEquals(1, stats.getWindow().size());
    }
}