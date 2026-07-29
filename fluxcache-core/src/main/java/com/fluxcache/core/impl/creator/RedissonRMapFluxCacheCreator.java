package com.fluxcache.core.impl.creator;

import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.impl.FluxRedissonCacheByRMapCache;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.spi.FluxCacheCreateContext;
import com.fluxcache.core.spi.FluxCacheCreator;

/**
 * @author : wh
 * @date : 2026/7/29
 */
public class RedissonRMapFluxCacheCreator implements FluxCacheCreator {

    @Override
    public FluxCacheType supportType() {
        return FluxCacheType.REDIS_R_MAP;
    }

    @Override
    public FluxAbstractValueAdaptingCache<?, ?> create(FluxCacheCacheable cacheable, FluxCacheCreateContext context) {
        return new FluxRedissonCacheByRMapCache<>(cacheable.isAllowCacheNull(), context.getRedissonClient(), cacheable);
    }
}
