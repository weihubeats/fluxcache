package com.fluxcache.core.exception;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.interceptor.FluxCacheErrorHandler;

/**
 * @author : wh
 * @date : 2024/11/14 00:20
 * @description:
 */
public class FluxSimpleCacheErrorHandler implements FluxCacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, FluxCache FluxCache, Object key) {
        throw exception;
    }

    @Override
    public void handleCachePutError(RuntimeException exception, FluxCache FluxCache, Object key, Object value) {
        throw exception;

    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, FluxCache FluxCache, Object key) {
        throw exception;
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, FluxCache FluxCache) {
        throw exception;
    }
}

