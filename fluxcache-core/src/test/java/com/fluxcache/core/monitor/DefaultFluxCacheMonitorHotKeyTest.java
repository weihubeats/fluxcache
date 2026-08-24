package com.fluxcache.core.monitor;

import com.fluxcache.core.config.CacheThreadPoolExecutor;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 监听链路：{@link DefaultFluxCacheMonitor} 将 HIT/MISS 事件转发给热 Key 探测器。
 *
 * @author : wh
 */
public class DefaultFluxCacheMonitorHotKeyTest {

    private static final String CACHE = "hot-monitor";

    private final AtomicLong time = new AtomicLong(1_000_000_000L);

    private DefaultFluxCacheMonitor monitor;

    private FluxHotKeyDetector detector;

    private int detectedCount;

    @Before
    public void setUp() {
        FluxCacheProperties properties = new FluxCacheProperties();
        properties.setAsyncMonitorEnable(false);

        FluxCacheProperties.HotKeyConfig hotKey = properties.getHotKey();
        hotKey.setEnabled(true);
        hotKey.setWindowSeconds(20);
        hotKey.setSlotSeconds(10);
        hotKey.setHotQpsThreshold(2.0);
        hotKey.setHotMissThreshold(5L);
        hotKey.setConfirmTicks(2);
        hotKey.setNotifyIntervalMs(30_000L);

        CacheThreadPoolExecutor pool = new CacheThreadPoolExecutor(1, 2, 4, 60,
                "hot-monitor-", new ThreadPoolExecutor.DiscardOldestPolicy());
        monitor = new DefaultFluxCacheMonitor(pool, properties);
        detector = new FluxHotKeyDetector(hotKey, time::get);
        detector.addFluxHotKeyListener(new FluxHotKeyListener() {
            @Override
            public void onHotKeyDetected(FluxHotKeySnapshot snapshot) {
                detectedCount++;
            }
        });
        monitor.setHotKeyDetector(detector);
    }

    private FluxCacheMonitorEvent hitEvent(String key, long count) {
        return FluxCacheMonitorEvent.builder()
                .cacheName(CACHE)
                .monitorEventEnum(MonitorEventEnum.CACHE_HIT)
                .count(count)
                .key(key)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private FluxCacheMonitorEvent missEvent(String key, long count) {
        return FluxCacheMonitorEvent.builder()
                .cacheName(CACHE)
                .monitorEventEnum(MonitorEventEnum.CACHE_MISSING)
                .count(count)
                .key(key)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Test
    public void monitorRoutesHitMissToDetectorAndNotifies() {
        monitor.publishMonitorEvent(hitEvent("hotkey", 60));
        monitor.publishMonitorEvent(missEvent("hotkey", 30));
        assertEquals(0, detectedCount);

        time.addAndGet(10_001L);
        monitor.publishMonitorEvent(hitEvent("hotkey", 60));
        monitor.publishMonitorEvent(missEvent("hotkey", 30));

        assertTrue(detector.isHotKey(CACHE, "hotkey"));
        assertEquals(1, detectedCount);
    }

    @Test
    public void nonReadOrEmptyKeys_doNotFeedDetector() {
        monitor.publishMonitorEvent(FluxCacheMonitorEvent.builder()
                .cacheName(CACHE)
                .monitorEventEnum(MonitorEventEnum.CACHE_PUT)
                .count(1)
                .key("k")
                .timestamp(System.currentTimeMillis())
                .build());
        monitor.publishMonitorEvent(hitEvent("", 1));
        monitor.publishMonitorEvent(missEvent(null, 1));

        assertEquals(0, detector.getTrackedCount());
        assertEquals(0, detectedCount);
    }

    @Test
    public void hotKeyDisabled_noDetector_noBreakage() {
        FluxCacheProperties bare = new FluxCacheProperties();
        bare.setAsyncMonitorEnable(false);
        DefaultFluxCacheMonitor bareMonitor = new DefaultFluxCacheMonitor(
                new CacheThreadPoolExecutor(1, 2, 4, 60, "x-", new ThreadPoolExecutor.DiscardOldestPolicy()),
                bare);
        bareMonitor.publishMonitorEvent(hitEvent("k", 10));
        bareMonitor.publishMonitorEvent(missEvent("k", 3));
        assertTrue(detectedCount == 0);
    }
}