package com.fluxcache.metrics;

import com.fluxcache.core.monitor.FluxCacheMonitorEvent;
import com.fluxcache.core.monitor.MonitorEventEnum;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Micrometer 指标桥接：计数、耗时 Timer、命中率 Gauge、空 registry 容错、按缓存维度隔离。
 *
 * @author : wh
 */
public class FluxCacheMicrometerListenerTest {

    private static final String CACHE = "studentLocalRedis";

    private SimpleMeterRegistry registry;

    private FluxCacheMicrometerListener listener;

    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        listener = new FluxCacheMicrometerListener(registry);
        listener.onCacheRegistered(CACHE);
    }

    private FluxCacheMonitorEvent event(MonitorEventEnum type, long count, long loadTime) {
        return FluxCacheMonitorEvent.builder()
                .cacheName(CACHE)
                .monitorEventEnum(type)
                .count(count)
                .loadTime(loadTime)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Test
    public void hitAndMissEvents_updateCountersAndRate() {
        listener.onMonitorEvent(event(MonitorEventEnum.CACHE_HIT, 3, 0));
        listener.onMonitorEvent(event(MonitorEventEnum.CACHE_HIT, 1, 0));
        listener.onMonitorEvent(event(MonitorEventEnum.CACHE_MISSING, 1, 0));

        assertEquals(4, registry.get("flux.cache.hit.total").tag("cache", CACHE).functionCounter().count(), 0.0001);
        assertEquals(1, registry.get("flux.cache.miss.total").tag("cache", CACHE).functionCounter().count(), 0.0001);
        assertEquals(0.8, registry.get("flux.cache.hit.rate").tag("cache", CACHE).gauge().value(), 0.0001);
        assertEquals(0.2, registry.get("flux.cache.miss.rate").tag("cache", CACHE).gauge().value(), 0.0001);
    }

    @Test
    public void putEvent_recordsLoadTimeTimer() {
        listener.onMonitorEvent(event(MonitorEventEnum.CACHE_PUT, 1, 500));

        io.micrometer.core.instrument.Timer timer =
                registry.get("flux.cache.load.time").tag("cache", CACHE).timer();
        assertEquals(1, timer.count());
        assertEquals(500, timer.max(TimeUnit.MILLISECONDS), 0.0001);
    }

    @Test
    public void evictEvent_updatesEvictionCounter() {
        listener.onMonitorEvent(event(MonitorEventEnum.CACHE_EVICT, 7, 0));

        assertEquals(7, registry.get("flux.cache.eviction.total").tag("cache", CACHE).functionCounter().count(), 0.0001);
    }

    @Test
    public void eventWithUnknownCache_autoRegistersMeters() {
        listener.onMonitorEvent(FluxCacheMonitorEvent.builder()
                .cacheName("auto-created")
                .monitorEventEnum(MonitorEventEnum.CACHE_HIT)
                .count(1)
                .build());

        assertNotNull(registry.find("flux.cache.hit.total").tag("cache", "auto-created").functionCounter());
        assertEquals(1, registry.get("flux.cache.hit.total").tag("cache", "auto-created").functionCounter().count(), 0.0001);
    }

    @Test
    public void noRequests_rateIsZero() {
        assertEquals(0.0, registry.get("flux.cache.hit.rate").tag("cache", CACHE).gauge().value(), 0.0001);
        assertEquals(0.0, registry.get("flux.cache.miss.rate").tag("cache", CACHE).gauge().value(), 0.0001);
    }

    @Test
    public void nullRegistry_isSafeNoop() {
        FluxCacheMicrometerListener noop = new FluxCacheMicrometerListener(null);
        noop.onCacheRegistered("any");
        noop.onMonitorEvent(event(MonitorEventEnum.CACHE_HIT, 1, 0));
        noop.onMonitorEvent(null);
    }

    @Test
    public void metricsAreIsolatedPerCache() {
        listener.onMonitorEvent(event(MonitorEventEnum.CACHE_HIT, 2, 0));
        listener.onMonitorEvent(FluxCacheMonitorEvent.builder()
                .cacheName("other")
                .monitorEventEnum(MonitorEventEnum.CACHE_MISSING)
                .count(9)
                .build());

        assertEquals(2, registry.get("flux.cache.hit.total").tag("cache", CACHE).functionCounter().count(), 0.0001);
        assertEquals(9, registry.get("flux.cache.miss.total").tag("cache", "other").functionCounter().count(), 0.0001);
    }

    @Test
    public void doubleRegistration_isIdempotent() {
        listener.onCacheRegistered(CACHE);
        assertEquals(1, registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("flux.cache.hit.total")
                        && m.getId().getTag("cache").equals(CACHE))
                .count());
    }

    @Test
    public void putEvent_withZeroLoadTime_skipsTimer() {
        listener.onMonitorEvent(event(MonitorEventEnum.CACHE_PUT, 1, 0));

        io.micrometer.core.instrument.Timer timer =
                registry.get("flux.cache.load.time").tag("cache", CACHE).timer();
        assertEquals(0, timer.count());
    }

    @Test
    public void eventWithNullType_isSkipped() {
        listener.onMonitorEvent(FluxCacheMonitorEvent.builder()
                .cacheName(CACHE)
                .monitorEventEnum(null)
                .build());

        assertEquals(0, registry.get("flux.cache.hit.total").tag("cache", CACHE).functionCounter().count(), 0.0001);
        assertEquals(0, registry.get("flux.cache.miss.total").tag("cache", CACHE).functionCounter().count(), 0.0001);
    }

    @Test
    public void eventMissingCacheName_isIgnored() {
        listener.onMonitorEvent(FluxCacheMonitorEvent.builder()
                .monitorEventEnum(MonitorEventEnum.CACHE_HIT)
                .build());

        // 事件被忽略，不应新增任何 hit.total 指标（仅 setUp 注册的 CACHE 存在）
        assertEquals(1, registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("flux.cache.hit.total"))
                .count());
    }

    @Test
    public void expireEvent_hitsDefaultBranch_safely() {
        listener.onMonitorEvent(event(MonitorEventEnum.CACHE_EXPIRE, 5, 0));

        assertEquals(0, registry.get("flux.cache.hit.total").tag("cache", CACHE).functionCounter().count(), 0.0001);
        assertEquals(0, registry.get("flux.cache.miss.total").tag("cache", CACHE).functionCounter().count(), 0.0001);
    }
}