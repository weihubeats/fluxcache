package com.fluxcache.core.model;

import com.fluxcache.core.enums.FluxCacheLevel;
import lombok.Getter;

/**
 * @author : wh
 * @date : 2024/9/16 21:25
 * @description:
 */
@Getter
public class FluxMultilevelCacheCacheable extends FluxCacheOperation {

    private final FluxCacheCacheableConfig firstCacheConfig;

    private final FluxCacheCacheableConfig secondaryCacheConfig;

    private final boolean allowCacheNull;

    public FluxMultilevelCacheCacheable(Builder builder) {
        super(builder);
        this.firstCacheConfig = builder.firstCacheConfig;
        this.secondaryCacheConfig = builder.secondaryCacheConfig;
        this.allowCacheNull = builder.allowNullValues;
    }

    public FluxMultilevelCacheCacheable(CacheConfigBuilder builder) {
        super(builder.cacheName, builder.fluxCacheLevel);
        this.firstCacheConfig = builder.firstCacheConfig;
        this.secondaryCacheConfig = builder.secondaryCacheConfig;
        this.allowCacheNull = builder.allowNullValues;
    }

    public FluxCacheOperation convertsFluxCacheCacheable(FluxCacheCacheableConfig cacheableConfig) {

        return new FluxCacheCacheable.Builder()
            .setUnit(cacheableConfig.getUnit())
            .setTtl(cacheableConfig.getTtl())
            .setInitSize(cacheableConfig.getInitSize())
            .setMaxSize(cacheableConfig.getMaxSize())
            .setAllowCacheNull(this.isAllowCacheNull())
            .setCacheName(this.getCacheName())
            .setKey(this.getKey())
            .setMethodName(this.getMethodName())
            .build();
    }

    public static class Builder extends FluxCacheOperation.Builder {

        private FluxCacheCacheableConfig firstCacheConfig;

        private FluxCacheCacheableConfig secondaryCacheConfig;

        private boolean allowNullValues;

        public Builder setFirstCacheConfig(FluxCacheCacheableConfig firstCacheConfig) {
            this.firstCacheConfig = firstCacheConfig;
            return this;
        }

        public Builder setSecondaryCacheable(FluxCacheCacheableConfig secondaryCacheConfig) {
            this.secondaryCacheConfig = secondaryCacheConfig;
            return this;
        }

        public Builder setAllowNullValues(boolean allowNullValues) {
            this.allowNullValues = allowNullValues;
            return this;
        }

        @Override
        public FluxCacheOperation build() {
            return new FluxMultilevelCacheCacheable(this);
        }
    }

    public static class CacheConfigBuilder {

        /**
         * 缓存名 获取对应的缓存配置
         */
        private String cacheName;

        private FluxCacheLevel fluxCacheLevel;

        private FluxCacheCacheableConfig firstCacheConfig;

        private FluxCacheCacheableConfig secondaryCacheConfig;

        private boolean allowNullValues;

        public CacheConfigBuilder setCacheName(String cacheName) {
            this.cacheName = cacheName;
            return this;
        }

        public CacheConfigBuilder setFluxCacheLevel(FluxCacheLevel fluxCacheLevel) {
            this.fluxCacheLevel = fluxCacheLevel;
            return this;
        }

        public CacheConfigBuilder setFirstCacheConfig(FluxCacheCacheableConfig firstCacheConfig) {
            this.firstCacheConfig = firstCacheConfig;
            return this;
        }

        public CacheConfigBuilder setSecondaryCacheConfig(FluxCacheCacheableConfig secondaryCacheConfig) {
            this.secondaryCacheConfig = secondaryCacheConfig;
            return this;
        }

        public CacheConfigBuilder setAllowNullValues(boolean allowNullValues) {
            this.allowNullValues = allowNullValues;
            return this;
        }

        public FluxMultilevelCacheCacheable build() {
            return new FluxMultilevelCacheCacheable(this);
        }

    }
}
