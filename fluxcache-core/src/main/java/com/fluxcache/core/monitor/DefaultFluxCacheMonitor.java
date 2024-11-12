package com.fluxcache.core.monitor;

import com.fluxcache.core.config.CacheThreadPoolExecutor;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.TriConsumer;

/**
 * @author : wh
 * @date : 2024/11/12 12:44
 * @description:
 */
@RequiredArgsConstructor
public class DefaultFluxCacheMonitor implements FluxCacheMonitor {

    private static final Map<MonitorEventEnum, TriConsumer<FluxCacheStatics, Long, Long>> enumBiConsumerMap = new HashMap<>();

    static {
        enumBiConsumerMap.put(MonitorEventEnum.CACHE_HIT, FluxCacheStatics::incrementHit);
        enumBiConsumerMap.put(MonitorEventEnum.CACHE_MISSING, FluxCacheStatics::incrementMissing);
        enumBiConsumerMap.put(MonitorEventEnum.CACHE_PUT, FluxCacheStatics::incrementPut);
        enumBiConsumerMap.put(MonitorEventEnum.CACHE_EVICT, FluxCacheStatics::incrementEvict);
    }

    private final CacheThreadPoolExecutor cacheThreadPoolExecutor;

    private final FluxCacheProperties cacheProperties;

    /**
     * cacheStatics
     */
    private final ConcurrentMap<String, FluxCacheStatics> cacheStaticsMap = new ConcurrentHashMap<>(16);

    @Override
    public void createCacheStaticsMap(ConcurrentMap<String, FluxCacheOperation> data) {
        data.forEach((key, value) -> {
            FluxCacheStatics statics = new FluxCacheStatics();
            cacheStaticsMap.put(key, statics);
        });
    }

    @Override
    public FluxCacheStatics getCacheStatics(String cacheName) {
        return cacheStaticsMap.getOrDefault(cacheName, new FluxCacheStatics());
    }

    @Override
    public void publishMonitorEvent(FluxCacheMonitorEvent FluxMonitorEvent) {
        FluxCacheStatics cacheStatics = cacheStaticsMap.get(FluxMonitorEvent.getCacheName());
        if (cacheStatics.isEmpty()) {
            cacheStatics.init();
        }
        MonitorEventEnum eventEnum = FluxMonitorEvent.getMonitorEventEnum();
        Long loadTime = FluxMonitorEvent.getLoadTime();
        Long count = FluxMonitorEvent.getCount();
        if (cacheProperties.isAsyncMonitorEnable()) {
            cacheThreadPoolExecutor.execute(() -> {
                enumBiConsumerMap.get(eventEnum).accept(cacheStatics, count, loadTime);
            });
        } else {
            enumBiConsumerMap.get(eventEnum).accept(cacheStatics, count, loadTime);
        }
    }

    @Override
    public void createNewCacheStatics(String cacheName) {
        cacheStaticsMap.put(cacheName, new FluxCacheStatics());
    }
}

