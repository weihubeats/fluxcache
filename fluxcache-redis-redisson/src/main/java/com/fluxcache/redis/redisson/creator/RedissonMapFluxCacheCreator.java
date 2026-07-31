package com.fluxcache.redis.redisson.creator;

import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.spi.FluxCacheCreateContext;
import com.fluxcache.core.spi.FluxCacheCreator;
import com.fluxcache.redis.redisson.cache.RedissonMapCache;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;

@RequiredArgsConstructor
public class RedissonMapFluxCacheCreator implements FluxCacheCreator {

    private final RedissonClient redissonClient;

    @Override
    public FluxCacheType supportType() {
        return FluxCacheType.REDIS_MAP;
    }

    @Override
    public FluxAbstractValueAdaptingCache<?, ?> create(FluxCacheCacheable cacheable, FluxCacheCreateContext context) {
        return new RedissonMapCache<>(cacheable.isAllowCacheNull(), redissonClient, cacheable);
    }
}
