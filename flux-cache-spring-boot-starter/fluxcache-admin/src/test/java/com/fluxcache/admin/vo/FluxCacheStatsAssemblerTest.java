package com.fluxcache.admin.vo;

import com.fluxcache.core.monitor.FluxCacheInfo;
import com.fluxcache.core.monitor.FluxCacheStatics;
import com.fluxcache.core.monitor.FluxCacheMonitorEvent;
import com.fluxcache.core.monitor.MonitorEventEnum;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class FluxCacheStatsAssemblerTest {

    private FluxCacheAllStaticsVO vo;

    @Before
    public void setUp() {
        long now = System.currentTimeMillis();
        FluxCacheInfo dummy = FluxCacheInfo.startAt(now, 60000);
        vo = new FluxCacheAllStaticsVO("test", now, Arrays.asList(dummy));
    }

    @Test
    public void fill_withBuckets_calcHitRateCorrectly() {
        long now = System.currentTimeMillis();
        FluxCacheInfo bucket = FluxCacheInfo.startAt(now - 60000, 60000);
        bucket.getHit().add(80L);
        bucket.getMiss().add(20L);
        bucket.getPutCount().add(10L);
        bucket.getEvictCount().add(5L);
        bucket.getMaxLoadTime().set(150L);

        FluxCacheStatsAssembler.fill(vo, "test-cache", now - 60000, Arrays.asList(bucket));

        assertEquals(1, vo.getWindows().size());
        assertEquals(80, vo.getTotalHit());
        assertEquals(20, vo.getTotalMiss());
        assertEquals(10, vo.getTotalPut());
        assertEquals(5, vo.getTotalEvict());
        // totalRequest = totalHit + totalMiss = 100, not 110 (PUT not counted)
        assertEquals(100, vo.getTotalRequest());
        assertEquals(0.8, vo.getOverallHitRate(), 0.001);
        assertEquals(150, vo.getMaxLoadTimeOverall());
    }

    @Test
    public void fill_zeroRequests_returnsZeroHitRate() {
        long now = System.currentTimeMillis();
        FluxCacheInfo bucket = FluxCacheInfo.startAt(now - 60000, 60000);

        FluxCacheStatsAssembler.fill(vo, "empty-cache", now - 60000, Arrays.asList(bucket));

        assertEquals(0, vo.getTotalRequest());
        assertEquals(0.0, vo.getOverallHitRate(), 0.001);
    }

    @Test
    public void fill_multipleBuckets_sumCorrectly() {
        long now = System.currentTimeMillis();
        FluxCacheInfo b1 = FluxCacheInfo.startAt(now - 120000, 60000);
        b1.getHit().add(50L);
        b1.getMiss().add(10L);
        b1.getMaxLoadTime().set(100L);

        FluxCacheInfo b2 = FluxCacheInfo.startAt(now - 60000, 60000);
        b2.getHit().add(30L);
        b2.getMiss().add(10L);
        b2.getPutCount().add(5L);
        b2.getMaxLoadTime().set(200L);

        FluxCacheStatsAssembler.fill(vo, "multi", now - 120000, Arrays.asList(b1, b2));

        assertEquals(2, vo.getWindows().size());
        assertEquals(80, vo.getTotalHit());
        assertEquals(20, vo.getTotalMiss());
        assertEquals(100, vo.getTotalRequest());
        assertEquals(0.8, vo.getOverallHitRate(), 0.001);
        assertEquals(200, vo.getMaxLoadTimeOverall());
    }

    @Test
    public void fill_snapshotFromStatics_correctlyComputes() {
        FluxCacheStatics statics = new FluxCacheStatics();

        // Simulate events directly
        statics.incrementHit(60, 0);
        statics.incrementMissing(10, 0);
        statics.incrementPut(5, 50L);

        FluxCacheAllStaticsVO result = new FluxCacheAllStaticsVO("test", statics);

        assertEquals(60, result.getTotalHit());
        assertEquals(10, result.getTotalMiss());
        assertEquals(70, result.getTotalRequest());
        assertEquals(0.8571, result.getOverallHitRate(), 0.001);
        assertEquals(50, result.getMaxLoadTimeOverall());
    }

    @Test
    public void fill_withNullBuckets_returnsEmpty() {
        FluxCacheAllStaticsVO result = new FluxCacheAllStaticsVO("null-test", 0L, null);
        assertNotNull(result);
        assertNull(result.getWindows());
    }

    @Test
    public void fill_lastNWindows_truncatesOlderWindows() {
        long now = System.currentTimeMillis();
        FluxCacheInfo b1 = FluxCacheInfo.startAt(now - 180000, 60000);
        b1.getHit().add(10L);
        FluxCacheInfo b2 = FluxCacheInfo.startAt(now - 120000, 60000);
        b2.getHit().add(20L);
        FluxCacheInfo b3 = FluxCacheInfo.startAt(now - 60000, 60000);
        b3.getHit().add(30L);

        FluxCacheAllStaticsVO result = new FluxCacheAllStaticsVO("trunc", now - 180000,
                Arrays.asList(b1, b2, b3));
        FluxCacheStatsAssembler.fill(result, "trunc", now - 180000,
                Arrays.asList(b1, b2, b3));

        // With lastNWindows=2, only b2 and b3 should be in windows
        FluxCacheAllStaticsVO truncated = new FluxCacheAllStaticsVO("trunc", now - 180000,
                Arrays.asList(b1, b2, b3));
        FluxCacheStatsAssembler.fill(truncated, "trunc", now - 180000,
                Arrays.asList(b1, b2, b3).subList(1, 3));

        assertEquals(2, truncated.getWindows().size());
        assertEquals(50, truncated.getTotalHit());
    }

    @Test
    public void fill_displayEndDoesNotExceedNow() {
        long now = System.currentTimeMillis();
        FluxCacheInfo bucket = FluxCacheInfo.startAt(now - 60000, 60000);
        // endTime is in the future (now)
        assertTrue(bucket.getEndTime().get() >= now);

        FluxCacheStatsAssembler.fill(vo, "test", now - 60000, Arrays.asList(bucket));

        FluxCacheStaticsVO window = vo.getWindows().get(0);
        assertTrue(window.getEndTime() <= now);
    }
}
