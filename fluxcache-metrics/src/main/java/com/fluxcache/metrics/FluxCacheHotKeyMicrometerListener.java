package com.fluxcache.metrics;

import com.fluxcache.core.monitor.FluxHotKeyListener;
import com.fluxcache.core.monitor.FluxHotKeySnapshot;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将热 Key 探测结果桥接到 Micrometer（Prometheus / Grafana）。
 *
 * <p>指标约定（Prometheus 前缀自动转为 {@code flux_cache_*}）：</p>
 * <ul>
 *     <li>{@code flux.cache.hot.key.qps}：Gauge，按 cache+key 标签，热 key 当前预估 QPS，恢复后置 0</li>
 *     <li>{@code flux.cache.hot.key.detected.total}：FunctionCounter，热 key 检测事件累计</li>
 * </ul>
 *
 * <p>registry 为空时安全跳过，不影响探测链路。</p>
 *
 * @author : wh
 */
public class FluxCacheHotKeyMicrometerListener implements FluxHotKeyListener {

    private static final String TAG_CACHE = "cache";
    private static final String TAG_KEY = "key";

    private final MeterRegistry meterRegistry;

    private final ConcurrentMap<String, HotKeyMetric> meters = new ConcurrentHashMap<>();

    private final AtomicLong detectedTotal = new AtomicLong();

    private final AtomicBoolean counterRegistered = new AtomicBoolean(false);

    public FluxCacheHotKeyMicrometerListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onHotKeyDetected(FluxHotKeySnapshot snapshot) {
        if (meterRegistry == null || snapshot == null) {
            return;
        }
        HotKeyMetric meter = meters.computeIfAbsent(
                snapshot.getCacheName() + "::" + snapshot.getKey(),
                k -> new HotKeyMetric(snapshot.getCacheName(), snapshot.getKey()));
        meter.register(meterRegistry);
        meter.qps.set((long) snapshot.getQps());
        detectedTotal.incrementAndGet();
        ensureDetectedCounter(meterRegistry);
    }

    @Override
    public void onHotKeyRecovered(FluxHotKeySnapshot snapshot) {
        if (meterRegistry == null || snapshot == null) {
            return;
        }
        HotKeyMetric meter = meters.get(snapshot.getCacheName() + "::" + snapshot.getKey());
        if (meter != null) {
            meter.qps.set(0L);
        }
    }

    private void ensureDetectedCounter(MeterRegistry registry) {
        if (counterRegistered.compareAndSet(false, true)) {
            FunctionCounter.builder("flux.cache.hot.key.detected.total",
                    detectedTotal, AtomicLong::get)
                    .description("Total hot cache key detection events")
                    .register(registry);
        }
    }

    /**
     * 单个热 key 的指标聚合源，惰性注册。
     */
    static final class HotKeyMetric {

        private final String cacheName;

        private final String key;

        private final AtomicLong qps = new AtomicLong();

        private final AtomicBoolean registered = new AtomicBoolean(false);

        HotKeyMetric(String cacheName, String key) {
            this.cacheName = cacheName;
            this.key = key;
        }

void register(MeterRegistry registry) {
            if (registered.compareAndSet(false, true)) {
                Gauge.builder("flux.cache.hot.key.qps", this, HotKeyMetric::getQps)
                        .description("Current QPS of a hot cache key (0 when recovered)")
                        .tag(TAG_CACHE, cacheName)
                        .tag(TAG_KEY, key)
                        .register(registry);
            }
        }

        long getQps() {
            return qps.get();
        }
    }
}