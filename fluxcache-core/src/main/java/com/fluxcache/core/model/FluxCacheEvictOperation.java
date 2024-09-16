package com.fluxcache.core.model;

/**
 * @author : wh
 * @date : 2024/9/16 21:35
 * @description:
 */
public class FluxCacheEvictOperation extends FluxCacheOperation {

    public FluxCacheEvictOperation(Builder b) {
        super(b);
    }

    public static class Builder extends FluxCacheOperation.Builder {

        @Override
        public FluxCacheOperation build() {
            return new FluxCacheEvictOperation(this);
        }

    }
}