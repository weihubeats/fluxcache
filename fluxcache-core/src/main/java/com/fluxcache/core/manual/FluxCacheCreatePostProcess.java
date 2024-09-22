package com.fluxcache.core.manual;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * @author : wh
 * @date : 2024/9/22 16:21
 * @description:
 */
@RequiredArgsConstructor
public class FluxCacheCreatePostProcess implements SmartInitializingSingleton {

    private final FluxCacheDataRegistered cacheDataRegistered;

    private final FluxCacheManager cacheManager;

    private final FluxCacheProperties cacheProperties;

    private final FluxCacheMonitor cacheMonitor;

    @Override
    public void afterSingletonsInstantiated() {
        if (Objects.nonNull(cacheDataRegistered)) {
            List<FluxMultilevelCacheCacheable> cacheables = cacheDataRegistered.registerCache();
            cacheables.forEach(v -> {
                cacheManager.createCache(v);
                if (cacheProperties.isCacheMonitorEnable()) {
                    cacheMonitor.createNewCacheStatics(v.getCacheName());
                }
            });
        }
    }
}