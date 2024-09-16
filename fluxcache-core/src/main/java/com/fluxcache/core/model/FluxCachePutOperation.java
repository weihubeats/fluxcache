package com.fluxcache.core.model;

import org.springframework.cache.interceptor.CachePutOperation;

/**
 * @author : wh
 * @date : 2024/9/16 21:33
 * @description:
 */
public class FluxCachePutOperation extends FluxCacheOperation{




    public FluxCachePutOperation(FluxCachePutOperation.Builder b) {
        super(b);
    }



    public static class Builder extends FluxCacheOperation.Builder {
        @Override
        public FluxCachePutOperation build() {
            return new FluxCachePutOperation(this);
        }

    }


}
