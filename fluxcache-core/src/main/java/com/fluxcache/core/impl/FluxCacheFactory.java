package com.fluxcache.core.impl;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.redisson.api.RedissonClient;

import java.util.Arrays;
import java.util.Objects;

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
            FluxCacheConfig config = ca.getFirstCacheConfig();
            return CacheTypeStrategy.getStrategy(config.getCacheType()).createFluxCache((FluxCacheCacheable) ca.convertsFluxCacheCacheable(config), redissonClient, cacheSyncStrategy, cacheProperties, cacheMonitor);
        }
        // 二级缓存
        if (FluxCacheLevel.isSecondaryCacheable(cacheLevel)) {
            FluxCacheConfig firstCfg = ca.getFirstCacheConfig();

            FluxCacheConfig secondCfg = ca.getSecondaryCacheConfig();

            FluxCacheCacheable cacheCacheable = (FluxCacheCacheable) ca.convertsFluxCacheCacheable(secondCfg);

            FluxAbstractValueAdaptingCache<?, ?> fluxFirstCache = CacheTypeStrategy.getStrategy(secondCfg.getCacheType()).createFluxCache(cacheCacheable, redissonClient, cacheSyncStrategy, cacheProperties, cacheMonitor);
            FluxAbstractValueAdaptingCache<?, ?> fluxSecondaryCache = CacheTypeStrategy.getStrategy(firstCfg.getCacheType()).createFluxCache(cacheCacheable, redissonClient, cacheSyncStrategy, cacheProperties, cacheMonitor);

            FluxRedissonCaffeineCache<?, ?> cache = new FluxRedissonCaffeineCache(ca.isAllowCacheNull(), ca.getCacheName(), fluxFirstCache, fluxSecondaryCache, cacheMonitor, cacheProperties);
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
                return new FluxCaffeineCache<>(ca.getCacheName(), caffeineCache, ca.isAllowCacheNull(), cacheSyncStrategy, cacheProperties, cacheMonitor);
            }
        },
        REDIS(FluxCacheType.REDIS_R_MAP) {
            @Override
            protected FluxAbstractValueAdaptingCache<?, ?> createFluxCache(FluxCacheCacheable ca,
                                                                           RedissonClient redissonClient,
                                                                           CacheSyncStrategy cacheSyncStrategy, FluxCacheProperties cacheProperties,
                                                                           FluxCacheMonitor cacheMonitor) {
                return new FluxRedissonCacheByRMapCache<>(ca.isAllowCacheNull(), redissonClient, ca, cacheMonitor, cacheProperties);
            }
        },
        REDIS_BUCKET(FluxCacheType.REDIS_BUCKET) {
            @Override
            protected FluxAbstractValueAdaptingCache<?, ?> createFluxCache(FluxCacheCacheable ca,
                                                                           RedissonClient redissonClient,
                                                                           CacheSyncStrategy cacheSyncStrategy, FluxCacheProperties cacheProperties,
                                                                           FluxCacheMonitor cacheMonitor) {
                return new FluxRedissonCacheByBucket<>(ca.isAllowCacheNull(), redissonClient, ca, cacheMonitor, cacheProperties);
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
