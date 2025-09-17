package com.fluxcache.core.monitor;

import com.fluxcache.core.config.CacheThreadPoolExecutor;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.TriConsumer;
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

    private static final Map<MonitorEventEnum, TriConsumer<FluxCacheStatics, Long, Long>> EVENT_APPLIERS = new HashMap<>();

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

        if (statics.isEmpty()) {
            statics.init();
        }

        TriConsumer<FluxCacheStatics, Long, Long> applier = EVENT_APPLIERS.get(event.getMonitorEventEnum());
        if (Objects.isNull(applier)) {
            return;
        }

        Runnable task = () -> applier.accept(statics, event.count(), event.loadTime());

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

