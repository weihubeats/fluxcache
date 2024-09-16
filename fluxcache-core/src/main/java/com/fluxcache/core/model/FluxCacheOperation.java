package com.fluxcache.core.model;

import com.fluxcache.core.enums.FluxCacheLevel;
import lombok.Data;
import org.springframework.util.Assert;

/**
 * @author : wh
 * @date : 2024/9/11 22:29
 * @description:
 */
@Data
public class FluxCacheOperation {

    private static final String NO_METHOD = "no_method";

    /**
     * 方法名
     */
    private final String methodName;

    /**
     * 缓存名 获取对应的缓存配置
     */
    private final String cacheName;
    /**
     * 缓存key 非实际的key， EL表达式的key 需要转换
     */
    private final String key;

    private final FluxCacheLevel fluxCacheLevel;

    public FluxCacheOperation(Builder b) {
        this.methodName = b.methodName;
        this.cacheName = b.cacheName;
        this.key = b.key;
        this.fluxCacheLevel = b.fluxCacheLevel;
    }

    public FluxCacheOperation(String cacheName, FluxCacheLevel fluxCacheLevel) {
        this.cacheName = cacheName;
        this.fluxCacheLevel = fluxCacheLevel;
        this.key = null;
        this.methodName = NO_METHOD;

    }

    public abstract static class Builder {

        private String methodName = "";

        private String cacheName = "";

        private String key = "";

        private FluxCacheLevel fluxCacheLevel = FluxCacheLevel.SecondaryCacheable;

        public Builder setMethodName(String methodName) {
            Assert.hasText(methodName, "methodName must not be empty");
            this.methodName = methodName;
            return this;
        }

        public Builder setCacheName(String cacheName) {
            Assert.hasText(cacheName, "cacheName must not be empty");
            this.cacheName = cacheName;
            return this;
        }

        public Builder setKey(String key) {
            this.key = key;
            return this;
        }

        public Builder setFluxCacheLevel(FluxCacheLevel fluxCacheLevel) {
            this.fluxCacheLevel = fluxCacheLevel;
            return this;
        }

        public abstract FluxCacheOperation build();
    }

}
