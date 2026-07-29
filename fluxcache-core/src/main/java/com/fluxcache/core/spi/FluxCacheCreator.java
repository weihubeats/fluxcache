package com.fluxcache.core.spi;

import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.model.FluxCacheCacheable;

/**
 * SPI for creating a single-level cache instance.
 * Register a Spring {@code @Bean} of this type to add or override a backend.
 * For the same {@link FluxCacheType}, the last registered creator wins.
 *
 * @author : wh
 * @date : 2026/7/29
 */
public interface FluxCacheCreator {

    /**
     * Cache backend this creator supports.
     */
    FluxCacheType supportType();

    /**
     * Create a single-level cache from normalized cacheable config.
     */
    FluxAbstractValueAdaptingCache<?, ?> create(FluxCacheCacheable cacheable, FluxCacheCreateContext context);
}
