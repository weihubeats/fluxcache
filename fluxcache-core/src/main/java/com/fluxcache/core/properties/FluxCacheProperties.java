package com.fluxcache.core.properties;

import com.fluxcache.core.enums.FluxCacheLevel;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.ObjectUtils;

import static com.fluxcache.core.properties.FluxCacheProperties.FLUX_CACHE;

/**
 * @author : wh
 * @date : 2024/9/16 21:18
 * @description:
 */
@Data
@ConfigurationProperties(prefix = FLUX_CACHE)
public class FluxCacheProperties {

    public static final String FLUX_CACHE = "flux.cache";

    @Value("${spring.application.name}")
    private String applicationName;

    private String namespace;

    /**
     * 默认缓存级别
     */
    private FluxCacheLevel defaultCacheLevel = FluxCacheLevel.SecondaryCacheable;

    /**
     * 是否开启监控
     */
    private boolean cacheMonitorEnable = true;

    /**
     * 是否开启异步监控
     */
    private boolean asyncMonitorEnable = true;

    @NestedConfigurationProperty
    private FirstCacheConfig firstCache;

    @NestedConfigurationProperty
    private SecondaryCacheConfig secondaryCache;

    @Data
    public static class FirstCacheConfig extends CacheConfig {

    }

    @Data
    public static class SecondaryCacheConfig extends CacheConfig {

    }

    @Data
    public abstract static class CacheConfig {

        /**
         * 默认过期时间30分钟
         */
        private long ttl = 30L;
        /**
         * 单位分钟
         */
        private TimeUnit timeUnit = TimeUnit.MINUTES;

        private FluxCacheType fluxCacheType;

    }

    /**
     * 获取缓存级别
     *
     * @return
     */
    public FluxCacheLevel fluxCacheLevel(FluxCacheLevel fluxCacheLevel) {
        return Objects.equals(fluxCacheLevel, FluxCacheLevel.NULL) ? this.defaultCacheLevel : fluxCacheLevel;
    }

    public String namespace() {
        return ObjectUtils.isEmpty(namespace) ? this.applicationName : namespace;
    }

}
