package com.fluxcache.core.config;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.interceptor.FluxCacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/11/12 12:36
 * @description:
 */
public interface FluxCachingConfigurer {

    @Nullable
    default FluxCacheManager cacheManager() {
        return null;
    }

    @Nullable
    default CacheResolver cacheResolver() {
        return null;
    }

    @Nullable
    default FluxKeyGenerator keyGenerator() {
        return null;
    }


    @Nullable
    default FluxCacheErrorHandler errorHandler() {
        return null;
    }

}
