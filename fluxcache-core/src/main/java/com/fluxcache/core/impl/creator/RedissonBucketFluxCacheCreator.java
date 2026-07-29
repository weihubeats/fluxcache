package com.fluxcache.core.impl.creator;

import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.impl.FluxRedissonCacheByBucket;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.spi.FluxCacheCreateContext;
import com.fluxcache.core.spi.FluxCacheCreator;

/**
 * @author : wh
 * @date : 2026/7/29
 */
public class RedissonBucketFluxCacheCreator implements FluxCacheCreator {

    @Override
    public FluxCacheType supportType() {
        return FluxCacheType.REDIS_BUCKET;
    }

    @Override
    public FluxAbstractValueAdaptingCache<?, ?> create(FluxCacheCacheable cacheable, FluxCacheCreateContext context) {
        return new FluxRedissonCacheByBucket<>(cacheable.isAllowCacheNull(), context.getRedissonClient(), cacheable);
    }
}
