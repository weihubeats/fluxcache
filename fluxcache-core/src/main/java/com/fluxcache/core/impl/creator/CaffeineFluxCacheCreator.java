package com.fluxcache.core.impl.creator;

import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.spi.FluxCacheCreateContext;
import com.fluxcache.core.spi.FluxCacheCreator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * @author : wh
 * @date : 2026/7/29
 */
public class CaffeineFluxCacheCreator implements FluxCacheCreator {

    @Override
    public FluxCacheType supportType() {
        return FluxCacheType.CAFFEINE;
    }

    @Override
    public FluxAbstractValueAdaptingCache<?, ?> create(FluxCacheCacheable cacheable, FluxCacheCreateContext context) {
        Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheable.getTtl(), cacheable.getUnit())
                .initialCapacity(cacheable.getInitSize())
                .maximumSize(cacheable.getMaxSize())
                .build();
        return new FluxCaffeineCache<>(cacheable.getCacheName(), caffeineCache, cacheable.isAllowCacheNull(),
                context.getCacheSyncStrategy(), context.getCacheProperties());
    }
}
