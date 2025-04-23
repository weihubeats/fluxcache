package com.fluxcache.core.model;

import java.util.concurrent.TimeUnit;
import lombok.Getter;

/**
 * @author : wh
 * @date : 2024/9/16 21:29
 * @description:
 */
@Getter
public class FluxCacheCacheable extends FluxCacheOperation{

    private final Long ttl;

    private final int initSize;

    private final TimeUnit unit;

    private final int maxSize;

    private final boolean allowCacheNull;

    public FluxCacheCacheable(Builder b) {
        super(b);
        this.ttl = b.ttl;
        this.initSize = b.initSize;
        this.unit = b.unit;
        this.maxSize = b.maxSize;
        this.allowCacheNull = b.allowCacheNull;
    }

    public static class Builder extends FluxCacheOperation.Builder {

        private Long ttl;

        private int initSize;

        private TimeUnit unit;

        private int maxSize;

        private boolean allowCacheNull;

        public FluxCacheCacheable.Builder setTtl(Long ttl) {
            this.ttl = ttl;
            return this;
        }

        public FluxCacheCacheable.Builder setInitSize(int initSize) {
            this.initSize = initSize;
            return this;
        }

        public FluxCacheCacheable.Builder setUnit(TimeUnit unit) {
            this.unit = unit;
            return this;
        }

        public FluxCacheCacheable.Builder setMaxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public FluxCacheCacheable.Builder setAllowCacheNull(boolean allowCacheNull) {
            this.allowCacheNull = allowCacheNull;
            return this;
        }

        @Override
        public FluxCacheCacheable build() {
            return new FluxCacheCacheable(this);
        }
    }
}
