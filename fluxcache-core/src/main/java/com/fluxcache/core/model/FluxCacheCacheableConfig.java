package com.fluxcache.core.model;

import com.fluxcache.core.annotation.FirstCacheable;
import com.fluxcache.core.annotation.FluxCacheType;
import com.fluxcache.core.annotation.SecondaryCacheable;
import java.util.concurrent.TimeUnit;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/9/16 21:21
 * @description:
 */
@Data
public class FluxCacheCacheableConfig {

    private final Long ttl;

    private final int initSize;

    private final TimeUnit unit;

    private int maxSize;

    private FluxCacheType cacheType;

    public FluxCacheCacheableConfig(FirstCacheable cacheable) {
        this.ttl = cacheable.ttl();
        this.initSize = cacheable.initSize();
        this.unit = cacheable.unit();
        this.maxSize = cacheable.maxSize();
        this.cacheType = cacheable.fluxCacheType();
    }

    public FluxCacheCacheableConfig(SecondaryCacheable cacheable) {
        this.ttl = cacheable.ttl();
        this.initSize = cacheable.initSize();
        this.unit = cacheable.unit();
        this.maxSize = cacheable.maxSize();
        this.cacheType = cacheable.fluxCacheType();
    }

    public FluxCacheCacheableConfig(Builder builder) {
        this.ttl = builder.ttl;
        this.initSize = builder.initSize;
        this.unit = builder.unit;
        this.maxSize = builder.maxSize;
        this.cacheType = builder.cacheType;
    }

    public static class Builder {

        private Long ttl;

        private int initSize;

        private TimeUnit unit;

        private int maxSize;

        private FluxCacheType cacheType;

        public Builder setTtl(Long ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder setInitSize(int initSize) {
            this.initSize = initSize;
            return this;
        }

        public Builder setUnit(TimeUnit unit) {
            this.unit = unit;
            return this;
        }

        public Builder setMaxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder setCacheType(FluxCacheType cacheType) {
            this.cacheType = cacheType;
            return this;
        }

        public FluxCacheCacheableConfig build() {
            return new FluxCacheCacheableConfig(this);

        }
    }

}
