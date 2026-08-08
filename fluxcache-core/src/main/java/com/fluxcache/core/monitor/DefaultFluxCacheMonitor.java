package com.fluxcache.core.monitor;

import com.fluxcache.core.config.CacheThreadPoolExecutor;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author : wh
 * @date : 2024/11/12 12:44
 * @description:
 */
@RequiredArgsConstructor
public class DefaultFluxCacheMonitor implements FluxCacheMonitor {

    private static final Logger log = LoggerFactory.getLogger(DefaultFluxCacheMonitor.class);

    @FunctionalInterface
    private interface StatApplier {

        void accept(FluxCacheStatics statics, long count, long loadTime);
    }

    private static final Map<MonitorEventEnum, StatApplier> EVENT_APPLIERS = new HashMap<>();

    static {
        EVENT_APPLIERS.put(MonitorEventEnum.CACHE_HIT, FluxCacheStatics::incrementHit);
        EVENT_APPLIERS.put(MonitorEventEnum.CACHE_MISSING, FluxCacheStatics::incrementMissing);
        EVENT_APPLIERS.put(MonitorEventEnum.CACHE_PUT, FluxCacheStatics::incrementPut);
        EVENT_APPLIERS.put(MonitorEventEnum.CACHE_EVICT, FluxCacheStatics::incrementEvict);
    }

    private final CacheThreadPoolExecutor cacheThreadPoolExecutor;

    private final FluxCacheProperties cacheProperties;

    /**
     * cacheStatics
     */
    private final ConcurrentMap<String, FluxCacheStatics> cacheStaticsMap = new ConcurrentHashMap<>(32);

    /**
     * 指标监听器（次级扩展点，如 Micrometer 指标导出），可为空
     */
    private final List<FluxCacheMetricsListener> fluxCacheMetricsListeners = new CopyOnWriteArrayList<>();

    /**
     * 热 Key 探测器，可为空（未开启时不占用读路径）
     */
    private volatile FluxHotKeyDetector hotKeyDetector;

    /**
     * 注入热 Key 探测器（由自动装配按配置装配）
     */
    public void setHotKeyDetector(FluxHotKeyDetector detector) {
        this.hotKeyDetector = detector;
    }

    /**
     * 注册指标监听器（由外部可观测性模块注入）
     */
    public void addFluxCacheMetricsListener(FluxCacheMetricsListener listener) {
        if (Objects.nonNull(listener)) {
            fluxCacheMetricsListeners.add(listener);
        }
    }

    @Override
    public void createCacheStaticsMap(ConcurrentMap<String, FluxCacheOperation> data) {
        if (ObjectUtils.isEmpty(data)) {
            return;
        }
        data.forEach((cacheName, op) -> {
            FluxCacheStatics previous = cacheStaticsMap.putIfAbsent(cacheName, new FluxCacheStatics());
            if (previous == null) {
                notifyCacheRegistered(cacheName);
            }
        });
    }

    @Override
    public FluxCacheStatics getCacheStatics(String cacheName) {
        return cacheStaticsMap.computeIfAbsent(cacheName, k -> new FluxCacheStatics());
    }

    @Override
    public void publishMonitorEvent(FluxCacheMonitorEvent event) {
        if (ObjectUtils.isEmpty(event) || Objects.isNull(event.getCacheName()) || Objects.isNull(event.getMonitorEventEnum())) {
            return;
        }
        FluxCacheStatics statics = cacheStaticsMap.computeIfAbsent(event.getCacheName(), k -> new FluxCacheStatics());

        StatApplier applier = EVENT_APPLIERS.get(event.getMonitorEventEnum());
        if (Objects.isNull(applier)) {
            log.warn("未注册的监控事件类型: cache={}, type={}", event.getCacheName(), event.getMonitorEventEnum());
            return;
        }

        // 热 Key 探测：同步、O(1)，只在开启时执行
        FluxHotKeyDetector detector = this.hotKeyDetector;
        MonitorEventEnum type = event.getMonitorEventEnum();
        if (detector != null && (type == MonitorEventEnum.CACHE_HIT || type == MonitorEventEnum.CACHE_MISSING)
                && !ObjectUtils.isEmpty(event.getKey())) {
            try {
                detector.record(event.getCacheName(), event.getKey(), type == MonitorEventEnum.CACHE_MISSING,
                        event.getCount());
            } catch (Exception e) {
                log.warn("hot key record error cache={} key={}", event.getCacheName(), event.getKey(), e);
            }
        }

        Runnable task = () -> {
            applier.accept(statics, event.getCount(), event.getLoadTime());
            fluxCacheMetricsListeners.forEach(listener -> listener.onMonitorEvent(event));
        };

        if (cacheProperties.isAsyncMonitorEnable()) {
            cacheThreadPoolExecutor.execute(task);
        } else {
            task.run();
        }

    }

    @Override
    public void createNewCacheStatics(String cacheName) {
        FluxCacheStatics previous = cacheStaticsMap.putIfAbsent(cacheName, new FluxCacheStatics());
        if (previous == null) {
            notifyCacheRegistered(cacheName);
        }
    }

    private void notifyCacheRegistered(String cacheName) {
        if (Objects.isNull(cacheName)) {
            return;
        }
        fluxCacheMetricsListeners.forEach(listener -> listener.onCacheRegistered(cacheName));
    }
}

