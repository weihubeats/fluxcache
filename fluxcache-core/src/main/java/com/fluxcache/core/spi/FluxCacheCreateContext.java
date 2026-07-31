package com.fluxcache.core.spi;

import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Shared dependencies for {@link FluxCacheCreator}.
 *
 * @author : wh
 * @date : 2026/7/29
 */
@Getter
@RequiredArgsConstructor
public final class FluxCacheCreateContext {

    private final CacheSyncStrategy cacheSyncStrategy;
    private final FluxCacheProperties cacheProperties;
    private final FluxCacheMonitor cacheMonitor;
}
