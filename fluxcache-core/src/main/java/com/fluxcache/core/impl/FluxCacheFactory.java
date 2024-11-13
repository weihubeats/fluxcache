package com.fluxcache.core.impl;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.model.FluxCacheCacheableConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Arrays;
import java.util.Objects;
import org.redisson.api.RedissonClient;

/**
 * @author : wh
 * @date : 2024/11/13 22:04
 * @description:
 */
public class FluxCacheFactory {


    public static FluxCache<?, ?> createFluxCache(FluxMultilevelCacheCacheable ca, RedissonClient redissonClient,
        FluxCacheProperties cacheProperties, CacheSyncStrategy cacheSyncStrategy, FluxCacheMonitor cacheMonitor) {
        FluxCacheLevel cacheLevel = Objects.equals(ca.getFluxCacheLevel(), FluxCacheLevel.NULL) ? cacheProperties.getDefaultCacheLevel() : ca.getFluxCacheLevel();
        // 一级缓存
        if (Objects.equals(cacheLevel, FluxCacheLevel.FirstCacheable)) {
            FluxCacheCacheableConfig config = ca.getFirstCacheConfig();
            return CacheTypeStrategy.getStrategy(config.getCacheType()).createFluxCache((FluxCacheCacheable) ca.convertsFluxCacheCacheable(config), redissonClient, cacheSyncStrategy, cacheProperties, cacheMonitor);
        }
        // 二级缓存
        if (FluxCacheLevel.isSecondaryCacheable(cacheLevel)) {
            FluxCacheCacheableConfig config = ca.getFirstCacheConfig();

            FluxCacheCacheableConfig secondaryCacheConfig = ca.getSecondaryCacheConfig();

            FluxCacheCacheable cacheCacheable = (FluxCacheCacheable) ca.convertsFluxCacheCacheable(secondaryCacheConfig);

            FluxAbstractValueAdaptingCache<?, ?> FluxFirstCache = CacheTypeStrategy.getStrategy(secondaryCacheConfig.getCacheType()).createFluxCache(cacheCacheable, redissonClient, cacheSyncStrategy, cacheProperties, cacheMonitor);
            FluxAbstractValueAdaptingCache<?, ?> FluxSecondaryCache = CacheTypeStrategy.getStrategy(config.getCacheType()).createFluxCache(cacheCacheable, redissonClient, cacheSyncStrategy, cacheProperties, cacheMonitor);

            FluxRedissonCaffeineCache<?, ?> cache = new FluxRedissonCaffeineCache(true, ca.getCacheName(), FluxFirstCache, FluxSecondaryCache, cacheMonitor, cacheProperties);
            return cache;
        }
        return null;
    }


    private enum CacheTypeStrategy {

        CAFFEINE(FluxCacheType.CAFFEINE) {
            @Override
            protected FluxAbstractValueAdaptingCache<?, ?> createFluxCache(FluxCacheCacheable ca,
                RedissonClient redissonClient,
                CacheSyncStrategy cacheSyncStrategy, FluxCacheProperties cacheProperties,
                FluxCacheMonitor cacheMonitor) {
                Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
                    .expireAfterWrite(ca.getTtl(), ca.getUnit())
                    .initialCapacity(ca.getInitSize())
                    .maximumSize(ca.getMaxSize())
                    .build();
                return new FluxCaffeineCache<>(ca.getCacheName(), caffeineCache, cacheSyncStrategy, cacheProperties, cacheMonitor);
            }
        },
        REDIS(FluxCacheType.REDIS) {
            @Override
            protected FluxAbstractValueAdaptingCache<?, ?> createFluxCache(FluxCacheCacheable ca,
                RedissonClient redissonClient,
                CacheSyncStrategy cacheSyncStrategy, FluxCacheProperties cacheProperties,
                FluxCacheMonitor cacheMonitor) {
                return new FluxRedissonCacheByRMapCache<>(true, redissonClient, ca, cacheMonitor, cacheProperties);
            }
        },
        REDIS_BUCKET(FluxCacheType.REDIS_BUCKET) {
            @Override
            protected FluxAbstractValueAdaptingCache<?, ?> createFluxCache(FluxCacheCacheable ca,
                RedissonClient redissonClient,
                CacheSyncStrategy cacheSyncStrategy, FluxCacheProperties cacheProperties,
                FluxCacheMonitor cacheMonitor) {
                return new FluxRedissonCacheByBucket<>(true, redissonClient, ca, cacheMonitor, cacheProperties);
            }
        };

        private final FluxCacheType cacheType;

        CacheTypeStrategy(FluxCacheType cacheType) {
            this.cacheType = cacheType;
        }

        protected abstract FluxAbstractValueAdaptingCache<?, ?> createFluxCache(FluxCacheCacheable ca,
            RedissonClient redissonClient,
            CacheSyncStrategy cacheSyncStrategy, FluxCacheProperties cacheProperties, FluxCacheMonitor cacheMonitor);

        public static CacheTypeStrategy getStrategy(FluxCacheType cacheType) {
            return Arrays.stream(values())
                .filter(strategy -> strategy.cacheType == cacheType)
                .findFirst()
                .orElse(null);

        }

    }

}
