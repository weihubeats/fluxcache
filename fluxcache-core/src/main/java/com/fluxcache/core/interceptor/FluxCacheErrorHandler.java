package com.fluxcache.core.interceptor;

import com.fluxcache.core.FluxCache;
import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/11/12 12:35
 * @description:
 */
public interface FluxCacheErrorHandler {

    void handleCacheGetError(RuntimeException exception, FluxCache fluxCache, Object key);


    void handleCachePutError(RuntimeException exception, FluxCache fluxCache, Object key, @Nullable Object value);

    void handleCacheEvictError(RuntimeException exception, FluxCache fluxCache, Object key);

    void handleCacheClearError(RuntimeException exception, FluxCache fluxCache);
}
