package com.fluxcache.core.properties;

import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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

    /**
     * 是否缓存null
     */
    private boolean allowCacheNull = true;

    /**
     * 是否缓存Optional.empty()
     */
    private boolean allowCacheEmptyOptional = true;

    /**
     * 是否开启单飞(single-flight)缓存击穿防护：
     * 同一 cacheName + key 的并发未命中只允许一个线程执行加载，其余线程等待其结果
     */
    private boolean singleFlightEnable = true;

    /**
     * 单飞等待超时时间(ms)，等待超时后线程回退为自行加载，避免加载线程异常/卡死拖垮所有请求
     */
    private long singleFlightTimeoutMillis = 5000L;

    @NestedConfigurationProperty
    private FirstCacheConfig firstCache;

    @NestedConfigurationProperty
    private SecondaryCacheConfig secondaryCache;

    @NestedConfigurationProperty
    private RedisCacheConfig redis = new RedisCacheConfig();

    /**
     * 监控统计相关配置
     */
    @NestedConfigurationProperty
    private MonitoringConfig monitoring = new MonitoringConfig();

    /**
     * 监控统计配置
     */
    @Data
    public static class MonitoringConfig {
        /**
         * 滚动窗口长度（分钟）
         */
        private int windowMinutes = 30;

        /**
         * 最多保留窗口数
         */
        private int maxWindows = 48;

        /**
         * 异步监控线程池核心线程数
         */
        private int monitorCorePoolSize = 1;

        /**
         * 异步监控线程池最大线程数
         */
        private int monitorMaxPoolSize = 3;

        /**
         * 异步监控线程池队列容量
         */
        private int monitorQueueSize = 2000;
    }

    @Data
    public static class FirstCacheConfig extends CacheConfig {

    }

    @Data
    public static class SecondaryCacheConfig extends CacheConfig {

    }

    /**
     * Redis 值序列化相关配置
     */
    @Data
    public static class RedisCacheConfig {

        /**
         * Redis 值反序列化允许的类型前缀白名单（防 Jackson 反序列化 gadget 攻击）。
         * 缓存值的业务 POJO 包名需要加入白名单；配置 "*" 表示允许所有类型（不推荐，等同关闭防护）
         */
        private List<String> serializationAllowedPrefixes = new ArrayList<>(Arrays.asList(
                "java.lang.", "java.util.", "java.time.", "java.math.", "com.fluxcache."
        ));

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

        private FluxCacheType cacheType;

    }

    /**
     * 获取缓存级别
     *
     * @return
     */
    public FluxCacheLevel fluxCacheLevel(FluxCacheLevel cacheLevel) {
        if (Objects.isNull(cacheLevel) || Objects.equals(cacheLevel, FluxCacheLevel.NULL)) {
            return this.defaultCacheLevel;
        }
        return cacheLevel;
        
    }

    public String namespace() {
        return ObjectUtils.isEmpty(namespace) ? this.applicationName : namespace;
    }

}
