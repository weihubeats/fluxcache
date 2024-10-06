package com.fluxcache.core.monitor;

import com.fluxcache.core.model.FluxCacheOperation;
import java.util.concurrent.ConcurrentMap;

/**
 * @author : wh
 * @date : 2024/9/22 16:23
 * @description:
 */
public interface FluxCacheMonitor {

    void createNewCacheStatics(String cacheName);

    FluxCacheStatics getCacheStatics(String cacheName);

    void publishMonitorEvent(FluxCacheMonitorEvent fluxCacheMonitorEvent);

    void createCacheStaticsMap(ConcurrentMap<String, FluxCacheOperation> data);




}
