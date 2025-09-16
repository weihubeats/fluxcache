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

    private final FluxCacheConfig firstCacheConfig;

    private final FluxCacheConfig secondaryCacheConfig;

    private final boolean allowCacheNull;

    public FluxMultilevelCacheCacheable(Builder builder) {
        super(builder);
        this.firstCacheConfig = builder.firstCacheConfig;
        this.secondaryCacheConfig = builder.secondaryCacheConfig;
        this.allowCacheNull = builder.allowNullValues;
    }

    public FluxMultilevelCacheCacheable(CacheConfigBuilder builder) {
        super(builder.cacheName, builder.FluxCacheLevel);
        this.firstCacheConfig = builder.firstCacheConfig;
        this.secondaryCacheConfig = builder.secondaryCacheConfig;
        this.allowCacheNull = builder.allowNullValues;
    }

    public FluxCacheOperation convertsFluxCacheCacheable(FluxCacheConfig cacheableConfig) {

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

        private FluxCacheConfig firstCacheConfig;

        private FluxCacheConfig secondaryCacheConfig;

        private boolean allowNullValues;

        public Builder setFirstCacheConfig(FluxCacheConfig firstCacheConfig) {
            this.firstCacheConfig = firstCacheConfig;
            return this;
        }

        public Builder setSecondaryCacheable(FluxCacheConfig secondaryCacheConfig) {
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

    /**
     * 手动注册 todo Builder and CacheConfigBuilder is reduce
     */
    public static class CacheConfigBuilder {

        /**
         * 缓存名 获取对应的缓存配置
         */
        private String cacheName;

        private FluxCacheLevel FluxCacheLevel;

        private FluxCacheConfig firstCacheConfig;

        private FluxCacheConfig secondaryCacheConfig;

        private boolean allowNullValues;

        public CacheConfigBuilder setCacheName(String cacheName) {
            this.cacheName = cacheName;
            return this;
        }

        public CacheConfigBuilder setFluxCacheLevel(FluxCacheLevel FluxCacheLevel) {
            this.FluxCacheLevel = FluxCacheLevel;
            return this;
        }

        public CacheConfigBuilder setFirstCacheConfig(FluxCacheConfig firstCacheConfig) {
            this.firstCacheConfig = firstCacheConfig;
            return this;
        }

        public CacheConfigBuilder setSecondaryCacheConfig(FluxCacheConfig secondaryCacheConfig) {
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
