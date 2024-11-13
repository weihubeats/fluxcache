package com.fluxcache.core;

import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.exception.FluxCacheMetaDataException;
import com.fluxcache.core.impl.FluxCacheFactory;
import com.fluxcache.core.interceptor.FluxCacheOperationSource;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.monitor.FluxCacheStatics;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

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
    private final ConcurrentMap<String, FluxCacheOperation> FluxCacheMetaData = new ConcurrentHashMap<>(16);

    private final RedissonClient redissonClient;

    private final CacheSyncStrategy cacheSyncStrategy;

    private final FluxCacheProperties cacheProperties;

    private final FluxCacheOperationSource fluxCacheOperationSource;

    private final FluxCacheMonitor fluxCacheMonitor;

    @Override
    public void createCache(FluxMultilevelCacheCacheable cacheable) {
        FluxCache<?, ?> cache = FluxCacheFactory.createFluxCache(cacheable, redissonClient, cacheProperties, cacheSyncStrategy, fluxCacheMonitor);
        cacheMap.put(cacheable.getCacheName(), cache);
        FluxCacheMetaData.put(cacheable.getCacheName(), cacheable);
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
        V cacheValue = null;
        if (Objects.nonNull(cache)) {
            V t = cache.get(key, valueLoader);
            if (ObjectUtils.isEmpty(t)) {
                try {
                    cacheValue = valueLoader.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                cache.put(key, cacheValue);
            } else {
                return t;
            }
        }
        return cacheValue;
    }

    @Override
    public <K, V> boolean putCache(String key, FluxCache<K, V> cache) {
        cacheMap.put(key, cache);
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
        log.info("clear cache name {}", cacheName);
        return true;
    }

    @Override
    public FluxCacheOperation getCacheMetaData(String cacheName) {
        return FluxCacheMetaData.get(cacheName);
    }

    @Override
    public List<FluxCacheOperation> getAllCacheMetaData() {
        return new ArrayList<>(FluxCacheMetaData.values());
    }

    @Override
    public FluxCacheStatics getCacheStatics(String cacheName) {
        return fluxCacheMonitor.getCacheStatics(cacheName);
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(AopUtils.getTargetClass(bean));
        for (Method method : methods) {
            FluxCacheOperation FluxCacheOperation = fluxCacheOperationSource.getCacheOperation(method, bean.getClass());
            if (Objects.nonNull(FluxCacheOperation) && FluxCacheOperation instanceof FluxMultilevelCacheCacheable) {
                FluxMultilevelCacheCacheable ca = (FluxMultilevelCacheCacheable) FluxCacheOperation;
                if (!cacheMap.containsKey(FluxCacheOperation.getCacheName())) {
                    // 相同缓存名 只能有一个元数据
                    FluxCache cache = FluxCacheFactory.createFluxCache(ca, redissonClient, cacheProperties, cacheSyncStrategy, fluxCacheMonitor);
                    log.info("create cacheName {}", ca.getCacheName());
                    cacheMap.put(FluxCacheOperation.getCacheName(), cache);
                    FluxCacheMetaData.put(FluxCacheOperation.getCacheName(), FluxCacheOperation);

                } else {
                    log.warn("Metadata with the same cache name already exists, cache Name {}", ca.getCacheName());
                    throw new FluxCacheMetaDataException("Metadata with the same cache name already exists, cache Name " + ca.getCacheName());
                }

            }
        }
        if (cacheProperties.isCacheMonitorEnable()) {
            fluxCacheMonitor.createCacheStaticsMap(FluxCacheMetaData);
        }
        return bean;
    }
}
