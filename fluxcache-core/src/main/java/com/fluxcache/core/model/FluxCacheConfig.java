package com.fluxcache.core.model;

import com.fluxcache.core.annotation.FirstCacheable;
import com.fluxcache.core.annotation.SecondaryCacheable;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * @author : wh
 * @date : 2025/9/16 14:18
 * @description:
 */
@Data
public class FluxCacheConfig {

    public static final long DEFAULT_TTL = 30L;

    public static final int DEFAULT_INIT_SIZE = 16;

    public static final int DEFAULT_MAX_SIZE = 10000;

    private final Long ttl;

    private final int initSize;

    private final TimeUnit unit;

    private int maxSize;

    private FluxCacheType cacheType;

    /**
     * 一级缓存配置：注解未设置的值(ttl&lt;=0 / size&lt;0 / type=NULL)回落到全局配置，
     * 全局也未设置时使用内置默认值。
     */
    public static FluxCacheConfig from(FirstCacheable cacheable, FluxCacheProperties.FirstCacheConfig global) {
        return merge(cacheable.ttl(), cacheable.unit(), cacheable.initSize(), cacheable.maxSize(),
                cacheable.fluxCacheType(), FluxCacheType.CAFFEINE, global);
    }

    /**
     * 二级缓存配置：合并规则同 {@link #from(FirstCacheable, FluxCacheProperties.FirstCacheConfig)}，
     * 未设置时的兜底类型为 REDIS。
     */
    public static FluxCacheConfig from(SecondaryCacheable cacheable, FluxCacheProperties.SecondaryCacheConfig global) {
        return merge(cacheable.ttl(), cacheable.unit(), cacheable.initSize(), cacheable.maxSize(),
                cacheable.fluxCacheType(), FluxCacheType.REDIS, global);
    }

    private static FluxCacheConfig merge(long annoTtl, TimeUnit annoUnit, int annoInitSize, int annoMaxSize,
                                         FluxCacheType annoType, FluxCacheType builtinType,
                                         FluxCacheProperties.CacheConfig global) {
        long ttl;
        TimeUnit unit;
        if (annoTtl > 0) {
            ttl = annoTtl;
            unit = annoUnit;
        } else if (global != null && global.getTtl() > 0) {
            ttl = global.getTtl();
            unit = global.getTimeUnit();
        } else {
            ttl = DEFAULT_TTL;
            unit = TimeUnit.MINUTES;
        }
        int initSize = annoInitSize >= 0 ? annoInitSize
                : (global != null && global.getInitSize() >= 0 ? global.getInitSize() : DEFAULT_INIT_SIZE);
        int maxSize = annoMaxSize >= 0 ? annoMaxSize
                : (global != null && global.getMaxSize() >= 0 ? global.getMaxSize() : DEFAULT_MAX_SIZE);
        FluxCacheType cacheType = (annoType != null && annoType != FluxCacheType.NULL) ? annoType
                : (global != null && global.getCacheType() != null) ? global.getCacheType() : builtinType;
        return new FluxCacheConfig.Builder()
                .setTtl(ttl)
                .setUnit(unit)
                .setInitSize(initSize)
                .setMaxSize(maxSize)
                .setCacheType(cacheType)
                .build();
    }

    public FluxCacheConfig(Builder builder) {
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

        public FluxCacheConfig build() {
            return new FluxCacheConfig(this);

        }
    }

}