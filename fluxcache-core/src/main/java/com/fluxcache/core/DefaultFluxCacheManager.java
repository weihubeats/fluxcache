package com.fluxcache.core;

import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.exception.FluxCacheMetaDataException;
import com.fluxcache.core.impl.FluxCacheFactory;
import com.fluxcache.core.interceptor.FluxCacheOperationSource;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.monitor.FluxCacheMonitorEvent;
import com.fluxcache.core.monitor.FluxCacheStatics;
import com.fluxcache.core.monitor.MonitorEventEnum;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author : wh
 * @date : 2024/11/14 00:25
 * @description:
 */
@RequiredArgsConstructor
@Slf4j
public class DefaultFluxCacheManager implements FluxCacheManager, BeanPostProcessor {

    /**
     * cache
     */
    private final ConcurrentMap<String, FluxCache> cacheMap = new ConcurrentHashMap<>(16);

    /**
     * cache metaData
     */
    private final ConcurrentMap<String, FluxCacheOperation> fluxCacheMetaData = new ConcurrentHashMap<>(16);

    private final RedissonClient redissonClient;

    private final CacheSyncStrategy cacheSyncStrategy;

    private final FluxCacheProperties cacheProperties;

    private final FluxCacheOperationSource fluxCacheOperationSource;

    private final FluxCacheMonitor fluxCacheMonitor;

    @Override
    public void createCache(FluxMultilevelCacheCacheable cacheable) {
        FluxCache<?, ?> cache = FluxCacheFactory.createFluxCache(cacheable, redissonClient, cacheProperties, cacheSyncStrategy, fluxCacheMonitor);
        cacheMap.put(cacheable.getCacheName(), cache);
        fluxCacheMetaData.put(cacheable.getCacheName(), cacheable);
        if (cacheProperties.isCacheMonitorEnable()) {
            fluxCacheMonitor.createNewCacheStatics(cacheable.getCacheName());
        }
        log.info("create cacheName {}", cacheable.getCacheName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> FluxCache<K, V> getCache(String cacheName) {
        return (FluxCache<K, V>) cacheMap.get(cacheName);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> V getCacheOrPut(String cacheName, K key, Callable<V> valueLoader) {
        FluxCache<K, V> cache = cacheMap.get(cacheName);
        if (Objects.isNull(cache)) {
            // 无缓存实例：直接加载并返回（不统计）
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // 先尝试命中
        try {
            FluxCache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                publish(cacheName, MonitorEventEnum.CACHE_HIT, keyToString(key), 1L, 0L);
                return (V) wrapper.get();
            }
        } catch (Exception getEx) {
            log.error("cache.get error cache={} key={}", cacheName, key, getEx);
            // 视为 miss 继续加载
        }

        // 未命中 -> 加载 & 写入
        publish(cacheName, MonitorEventEnum.CACHE_MISSING, keyToString(key), 1L, 0L);

        long begin = System.nanoTime();
        V value;
        try {
            value = valueLoader.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        long loadMs = (System.nanoTime() - begin) / 1_000_000;

        cache.put(key, value);
        publish(cacheName, MonitorEventEnum.CACHE_PUT, keyToString(key), 1L, loadMs);
        return value;
    }

    @Override
    public <K, V> boolean putCache(String key, FluxCache<K, V> cache) {
        cacheMap.put(key, cache);
        publish(cache.getName(), MonitorEventEnum.CACHE_PUT, key, 1L, 0L);
        return true;
    }

    @Override
    public List<FluxCache> getAllCaches() {
        return new ArrayList<>(cacheMap.values());
    }

    @Override
    public <K, V> boolean evictCache(String cacheName, List<K> keys) {
        FluxCache<K, V> cache = getCache(cacheName);
        if (Objects.isNull(cache)) {
            return false;
        }
        cache.bathEvict(keys);
        publish(cacheName, MonitorEventEnum.CACHE_EVICT, "*", keys != null ? keys.size() : 0L, 0L);
        log.info("evict cache key {}", keys);
        return true;
    }

    @Override
    public <K, V> boolean clearCacheByName(String cacheName) {
        FluxCache<K, V> cache = getCache(cacheName);
        if (Objects.isNull(cache)) {
            return false;
        }
        cache.clear();
        publish(cacheName, MonitorEventEnum.CACHE_EVICT, "*", 1L, 0L);
        log.info("clear cache name {}", cacheName);
        return true;
    }

    @Override
    public FluxCacheOperation getCacheMetaData(String cacheName) {
        return fluxCacheMetaData.get(cacheName);
    }

    @Override
    public List<FluxCacheOperation> getAllCacheMetaData() {
        return new ArrayList<>(fluxCacheMetaData.values());
    }

    @Override
    public FluxCacheStatics getCacheStatics(String cacheName) {
        return fluxCacheMonitor.getCacheStatics(cacheName);
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(AopUtils.getTargetClass(bean));
        for (Method method : methods) {
            FluxCacheOperation op = fluxCacheOperationSource.getCacheOperation(method, bean.getClass());
            if (Objects.nonNull(op) && op instanceof FluxMultilevelCacheCacheable) {
                FluxMultilevelCacheCacheable ca = (FluxMultilevelCacheCacheable) op;
                if (!cacheMap.containsKey(op.getCacheName())) {
                    // 相同缓存名 只能有一个元数据
                    FluxCache cache = FluxCacheFactory.createFluxCache(ca, redissonClient, cacheProperties, cacheSyncStrategy, fluxCacheMonitor);
                    log.info("create cacheName {}", ca.getCacheName());
                    cacheMap.put(op.getCacheName(), cache);
                    fluxCacheMetaData.put(op.getCacheName(), op);
                    if (cacheProperties.isCacheMonitorEnable()) {
                        fluxCacheMonitor.createNewCacheStatics(op.getCacheName());
                    }

                } else {
                    log.warn("Metadata with the same cache name already exists, cache Name {}", ca.getCacheName());
                    throw new FluxCacheMetaDataException("Metadata with the same cache name already exists, cache Name " + ca.getCacheName());
                }

            }
        }
        if (cacheProperties.isCacheMonitorEnable()) {
            fluxCacheMonitor.createCacheStaticsMap(fluxCacheMetaData);
        }
        return bean;
    }

    private String keyToString(Object key) {
        if (key == null)
            return "null";
        try {
            String s = String.valueOf(key);
            // 如需脱敏可在此处理
            return s.length() > 256 ? s.substring(0, 256) : s;
        } catch (Exception e) {
            return key.getClass().getName();
        }
    }

    private void publish(String cacheName, MonitorEventEnum type, String key, long count, long loadMs) {
        if (cacheProperties.isCacheMonitorEnable()) {
            try {
                fluxCacheMonitor.publishMonitorEvent(
                        FluxCacheMonitorEvent.builder()
                                .cacheName(cacheName)
                                .monitorEventEnum(type)
                                .count(count)
                                .loadTime(loadMs)
                                .timestamp(System.currentTimeMillis())
                                .key(key)
                                .forceRefresh(false)
                                .build()
                );
            } catch (Exception e) {
                log.warn("publish monitor event failed cache={} type={} key={}", cacheName, type, key, e);
            }

        }

    }
}
