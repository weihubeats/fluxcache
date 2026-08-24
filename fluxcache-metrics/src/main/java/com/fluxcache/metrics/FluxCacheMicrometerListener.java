package com.fluxcache.metrics;

import com.fluxcache.core.monitor.FluxCacheMetricsListener;
import com.fluxcache.core.monitor.FluxCacheMonitorEvent;
import com.fluxcache.core.monitor.MonitorEventEnum;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * 将 FluxCache 监控事件桥接到 Micrometer（Prometheus / Grafana）。
 *
 * <p>指标约定（Prometheus 前缀自动转为 {@code flux_cache_*}）：</p>
 * <ul>
 *     <li>{@code flux.cache.hit.total} / {@code flux.cache.miss.total} / {@code flux.cache.eviction.total}：FunctionCounter，按 cache 打标签</li>
 *     <li>{@code flux.cache.load.time}：Timer，记录 L2/DB 真实加载耗时（PUT 事件），含 p50/p95/p99 与直方图</li>
 *     <li>{@code flux.cache.hit.rate} / {@code flux.cache.miss.rate}：Gauge，命中/未命中率</li>
 * </ul>
 *
 * <p>registry 为空（例如未装配 MeterRegistry）时不注册任何指标并安全跳过，不影响缓存链路。</p>
 *
 * @author : wh
 */
public class FluxCacheMicrometerListener implements FluxCacheMetricsListener {

    private static final String TAG_CACHE = "cache";

    private final MeterRegistry meterRegistry;

    private final ConcurrentMap<String, CacheMetrics> cacheMetricsMap = new ConcurrentHashMap<>();

    public FluxCacheMicrometerListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onCacheRegistered(String cacheName) {
        if (meterRegistry == null) {
            return;
        }
        cacheMetricsMap.computeIfAbsent(cacheName, CacheMetrics::new).register(meterRegistry);
    }

    @Override
    public void onMonitorEvent(FluxCacheMonitorEvent event) {
        if (meterRegistry == null || event == null || event.getCacheName() == null) {
            return;
        }
        CacheMetrics metrics = cacheMetricsMap.computeIfAbsent(event.getCacheName(), CacheMetrics::new);
        metrics.register(meterRegistry);

        MonitorEventEnum type = event.getMonitorEventEnum();
        long count = event.getCount();
        if (type == null) {
            return;
        }
        switch (type) {
            case CACHE_HIT:
                metrics.hit.addAndGet(count);
                break;
            case CACHE_MISSING:
                metrics.miss.addAndGet(count);
                break;
            case CACHE_EVICT:
                metrics.eviction.addAndGet(count);
                break;
            case CACHE_PUT:
                if (event.getLoadTime() > 0) {
                    metrics.recordLoad(event.getLoadTime(), TimeUnit.MILLISECONDS);
                }
                break;
            default:
                break;
        }
    }

    /**
     * 每个缓存的指标聚合源，原子计数 + 惰性注册。
     */
    static final class CacheMetrics {

        private static final AtomicLongFieldUpdater<CacheMetrics> REGISTERED =
                AtomicLongFieldUpdater.newUpdater(CacheMetrics.class, "registered");

        private final String cacheName;

        private final AtomicLong hit = new AtomicLong();

        private final AtomicLong miss = new AtomicLong();

        private final AtomicLong eviction = new AtomicLong();

        private volatile long registered = 0L;

        private volatile Timer loadTimer;

        private CacheMetrics(String cacheName) {
            this.cacheName = cacheName;
        }

        void register(MeterRegistry registry) {
            if (REGISTERED.get(this) != 0L) {
                return;
            }
            if (REGISTERED.compareAndSet(this, 0L, 1L)) {
                FunctionCounter.builder("flux.cache.hit.total", hit, AtomicLong::get)
                        .description("Total cache hits")
                        .tag(TAG_CACHE, cacheName)
                        .register(registry);
                FunctionCounter.builder("flux.cache.miss.total", miss, AtomicLong::get)
                        .description("Total cache misses")
                        .tag(TAG_CACHE, cacheName)
                        .register(registry);
                FunctionCounter.builder("flux.cache.eviction.total", eviction, AtomicLong::get)
                        .description("Total cache evictions")
                        .tag(TAG_CACHE, cacheName)
                        .register(registry);
                Gauge.builder("flux.cache.hit.rate", this, CacheMetrics::hitRate)
                        .description("Cache hit rate (hit / (hit + miss))")
                        .tag(TAG_CACHE, cacheName)
                        .register(registry);
                Gauge.builder("flux.cache.miss.rate", this, CacheMetrics::missRate)
                        .description("Cache miss rate (miss / (hit + miss))")
                        .tag(TAG_CACHE, cacheName)
                        .register(registry);
                this.loadTimer = Timer.builder("flux.cache.load.time")
                        .description("Time spent loading into the cache (L2/DB)")
                        .tag(TAG_CACHE, cacheName)
                        .publishPercentileHistogram()
                        .register(registry);
            }
        }

        /**
         * Safe against the registration race: a concurrent event may observe
         * registered != 0 while loadTimer is still being assigned.
         */
        void recordLoad(long amount, TimeUnit unit) {
            Timer timer = this.loadTimer;
            if (timer != null) {
                timer.record(amount, unit);
            }
        }

        private double hitRate() {
            long total = hit.get() + miss.get();
            return total == 0L ? 0.0d : (double) hit.get() / total;
        }

        private double missRate() {
            long total = hit.get() + miss.get();
            return total == 0L ? 0.0d : (double) miss.get() / total;
        }
    }
}