package com.fluxcache.redis.spring.creator;

import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.spi.FluxCacheCreateContext;
import com.fluxcache.core.spi.FluxCacheCreator;
import com.fluxcache.redis.spring.cache.SpringDataRedisCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Creates {@link FluxCacheType#REDIS} caches via Spring Data Redis.
 */
@RequiredArgsConstructor
public class SpringDataRedisFluxCacheCreator implements FluxCacheCreator {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public FluxCacheType supportType() {
        return FluxCacheType.REDIS;
    }

    @Override
    public FluxAbstractValueAdaptingCache<?, ?> create(FluxCacheCacheable cacheable, FluxCacheCreateContext context) {
        return new SpringDataRedisCache<>(cacheable.isAllowCacheNull(), redisTemplate, cacheable);
    }
}
