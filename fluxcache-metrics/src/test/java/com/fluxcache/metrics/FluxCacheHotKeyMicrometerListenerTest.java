package com.fluxcache.metrics;

import com.fluxcache.core.monitor.FluxHotKeySnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 热 Key Micrometer 指标绑定。
 *
 * @author : wh
 */
public class FluxCacheHotKeyMicrometerListenerTest {

    private SimpleMeterRegistry registry;

    private FluxCacheHotKeyMicrometerListener listener;

    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        listener = new FluxCacheHotKeyMicrometerListener(registry);
    }

    private FluxHotKeySnapshot snapshot(String cache, String key, long qps, double hitRate) {
        return FluxHotKeySnapshot.builder()
                .cacheName(cache)
                .key(key)
                .hitCount(90)
                .missCount(30)
                .qps(qps)
                .hitRate(hitRate)
                .hot(true)
                .hotSince(1L)
                .lastActiveTime(2L)
                .build();
    }

    @Test
    public void hotDetected_publishesQpsGaugeAndDetectedCounter() {
        listener.onHotKeyDetected(snapshot("cache.a", "hotkey", 12L, 0.99));

        assertEquals(12.0, registry.get("flux.cache.hot.key.qps")
                .tags("cache", "cache.a", "key", "hotkey").gauge().value(), 0.0001);
        // 全局检测计数器已惰性注册且真实累加（回归：曾恒为 0）
        assertEquals(1.0, registry.get("flux.cache.hot.key.detected.total")
                .functionCounter().count(), 0.0001);

        listener.onHotKeyDetected(snapshot("cache.b", "other", 5L, 0.9));
        assertEquals(2.0, registry.get("flux.cache.hot.key.detected.total")
                .functionCounter().count(), 0.0001);
    }

    @Test
    public void hotRecovered_zeroOutQpsGauge() {
        listener.onHotKeyDetected(snapshot("cache.a", "hotkey", 12L, 0.99));
        listener.onHotKeyRecovered(snapshot("cache.a", "hotkey", 0L, 0.0));

        assertEquals(0.0, registry.get("flux.cache.hot.key.qps")
                .tags("cache", "cache.a", "key", "hotkey").gauge().value(), 0.0001);
    }

    @Test
    public void distinctHotKeys_isolatedTags() {
        listener.onHotKeyDetected(snapshot("cache.a", "k1", 3L, 0.5));
        listener.onHotKeyDetected(snapshot("cache.a", "k2", 9L, 0.5));

        assertEquals(3.0, registry.get("flux.cache.hot.key.qps")
                .tags("cache", "cache.a", "key", "k1").gauge().value(), 0.0001);
        assertEquals(9.0, registry.get("flux.cache.hot.key.qps")
                .tags("cache", "cache.a", "key", "k2").gauge().value(), 0.0001);
    }

    @Test
    public void nullRegistryOrSnapshot_noOp() {
        FluxCacheHotKeyMicrometerListener noop = new FluxCacheHotKeyMicrometerListener(null);
        noop.onHotKeyDetected(snapshot("c", "k", 1L, 0.9));
        noop.onHotKeyRecovered(snapshot("c", "k", 0L, 0.0));

        listener.onHotKeyDetected(null);
        listener.onHotKeyRecovered(null);

        assertTrue(registry.getMeters().isEmpty());
    }
}