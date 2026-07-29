package com.fluxcache.core.spi;

import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.redisson.api.RedissonClient;

/**
 * Shared dependencies for {@link FluxCacheCreator}.
 *
 * @author : wh
 * @date : 2026/7/29
 */
public final class FluxCacheCreateContext {

    private final RedissonClient redissonClient;
    private final CacheSyncStrategy cacheSyncStrategy;
    private final FluxCacheProperties cacheProperties;
    private final FluxCacheMonitor cacheMonitor;

    public FluxCacheCreateContext(RedissonClient redissonClient,
                                  CacheSyncStrategy cacheSyncStrategy,
                                  FluxCacheProperties cacheProperties,
                                  FluxCacheMonitor cacheMonitor) {
        this.redissonClient = redissonClient;
        this.cacheSyncStrategy = cacheSyncStrategy;
        this.cacheProperties = cacheProperties;
        this.cacheMonitor = cacheMonitor;
    }

    public RedissonClient getRedissonClient() {
        return redissonClient;
    }

    public CacheSyncStrategy getCacheSyncStrategy() {
        return cacheSyncStrategy;
    }

    public FluxCacheProperties getCacheProperties() {
        return cacheProperties;
    }

    public FluxCacheMonitor getCacheMonitor() {
        return cacheMonitor;
    }
}
