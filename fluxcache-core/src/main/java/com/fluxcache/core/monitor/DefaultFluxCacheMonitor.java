package com.fluxcache.core.monitor;

import com.fluxcache.core.config.CacheThreadPoolExecutor;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    @Override
    public void createCacheStaticsMap(ConcurrentMap<String, FluxCacheOperation> data) {
        if (ObjectUtils.isEmpty(data)) {
            return;
        }
        data.forEach((cacheName, op) -> cacheStaticsMap.computeIfAbsent(cacheName, k -> new FluxCacheStatics()));
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

        Runnable task = () -> applier.accept(statics, event.getCount(), event.getLoadTime());

        if (cacheProperties.isAsyncMonitorEnable()) {
            cacheThreadPoolExecutor.execute(task);
        } else {
            task.run();
        }

    }

    @Override
    public void createNewCacheStatics(String cacheName) {
        cacheStaticsMap.putIfAbsent(cacheName, new FluxCacheStatics());

    }
}

