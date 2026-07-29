package com.fluxcache.core.impl;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.exception.FluxCacheNotSupperException;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.spi.FluxCacheCreateContext;
import com.fluxcache.core.spi.FluxCacheCreatorRegistry;
import org.redisson.api.RedissonClient;

import java.util.Objects;

/**
 * Assembles single-level / multi-level caches via {@link FluxCacheCreatorRegistry}.
 *
 * @author : wh
 * @date : 2024/11/13 22:04
 */
public class FluxCacheFactory {

    private final FluxCacheCreatorRegistry creatorRegistry;

    public FluxCacheFactory(FluxCacheCreatorRegistry creatorRegistry) {
        this.creatorRegistry = Objects.requireNonNull(creatorRegistry, "creatorRegistry must not be null");
    }

    /**
     * Factory with built-in creators (mainly for tests / non-Spring usage).
     */
    public static FluxCacheFactory withDefaults() {
        return new FluxCacheFactory(FluxCacheCreatorRegistry.withDefaults());
    }

    public FluxCache<?, ?> createFluxCache(FluxMultilevelCacheCacheable ca, RedissonClient redissonClient,
                                           FluxCacheProperties cacheProperties, CacheSyncStrategy cacheSyncStrategy,
                                           FluxCacheMonitor cacheMonitor) {
        FluxCacheLevel cacheLevel = Objects.equals(ca.getFluxCacheLevel(), FluxCacheLevel.NULL)
                ? cacheProperties.getDefaultCacheLevel()
                : ca.getFluxCacheLevel();
        FluxCacheCreateContext context = new FluxCacheCreateContext(
                redissonClient, cacheSyncStrategy, cacheProperties, cacheMonitor);

        if (Objects.equals(cacheLevel, FluxCacheLevel.FirstCacheable)) {
            FluxCacheConfig config = requireConfig(ca.getFirstCacheConfig(), "first", ca.getCacheName());
            return createSingleLevelCache(ca, config, context);
        }

        if (FluxCacheLevel.isSecondaryCacheable(cacheLevel)) {
            FluxCacheConfig firstCfg = requireConfig(ca.getFirstCacheConfig(), "first", ca.getCacheName());
            FluxCacheConfig secondCfg = requireConfig(ca.getSecondaryCacheConfig(), "secondary", ca.getCacheName());

            FluxAbstractValueAdaptingCache<?, ?> fluxFirstCache = createSingleLevelCache(ca, firstCfg, context);
            FluxAbstractValueAdaptingCache<?, ?> fluxSecondaryCache = createSingleLevelCache(ca, secondCfg, context);

            @SuppressWarnings({"rawtypes", "unchecked"})
            FluxRedissonCaffeineCache<?, ?> cache = new FluxRedissonCaffeineCache(
                    ca.isAllowCacheNull(), ca.getCacheName(), fluxFirstCache, fluxSecondaryCache);
            return cache;
        }

        throw new FluxCacheNotSupperException("Unsupported cache level: " + cacheLevel);
    }

    private FluxAbstractValueAdaptingCache<?, ?> createSingleLevelCache(FluxMultilevelCacheCacheable ca,
                                                                        FluxCacheConfig config,
                                                                        FluxCacheCreateContext context) {
        FluxCacheCacheable cacheable = ca.convertsFluxCacheCacheable(config);
        return creatorRegistry.getRequired(config.getCacheType()).create(cacheable, context);
    }

    private static FluxCacheConfig requireConfig(FluxCacheConfig config, String level, String cacheName) {
        if (config == null) {
            throw new IllegalArgumentException(level + " cache config must not be null for cache: " + cacheName);
        }
        return config;
    }
}
