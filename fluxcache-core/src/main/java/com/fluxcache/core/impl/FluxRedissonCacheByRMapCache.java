package com.fluxcache.core.impl;

import com.fluxcache.core.enums.CacheOrder;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.redisson.api.RFuture;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

/**
 * @author : wh
 * @date : 2024/11/13 22:09
 * @description:
 */
@Slf4j
public class FluxRedissonCacheByRMapCache<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

    private final FluxCacheCacheable cacheable;

    /**
     * cache config
     */
    private final RedissonClient redissonClient;

    // todo  maybe use parent name, but not sure, because in order to be compatible with older versions 
    private final String name;

    protected FluxRedissonCacheByRMapCache(boolean allowNullValues, RedissonClient redissonClient,
        FluxCacheCacheable cacheable, FluxCacheMonitor cacheMonitor, FluxCacheProperties cacheProperties) {
        super(allowNullValues, cacheMonitor, cacheable.getCacheName(), cacheProperties);
        this.redissonClient = redissonClient;
        this.cacheable = cacheable;
        this.name = String.join(":", "FluxCache", cacheable.getCacheName());
    }

    public RMapCache<K, V> getRMapCache() {
        if (log.isDebugEnabled()) {
            log.debug("getRMapCache name {}", name);
        }
        return redissonClient.getMapCache(name);
    }

    public RMapCache<K, Object> getPutRMapCache() {
        if (log.isDebugEnabled()) {
            log.debug("getRMapCache name {}", name);
        }
        return redissonClient.getMapCache(name);
    }

    @Override
    public CacheOrder ordered() {
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void putValue(K key, Object value) {
        RMapCache<K, Object> cache = getPutRMapCache();
        Long ttl = getTtl();
        cache.put(key, value, ttl, this.cacheable.getUnit());
        if (log.isDebugEnabled()) {
            log.debug("redis put key {} value {}", key, value);
        }
    }

    private Long getTtl() {
        Long ttl = this.cacheable.getTtl();
        // 防止缓存雪崩
        if (Objects.equals(this.cacheable.getUnit(), TimeUnit.MINUTES) || Objects.equals(this.cacheable.getUnit(), TimeUnit.SECONDS)) {
            ttl = ttl + RandomUtils.nextInt(1, 10);
        }
        return ttl;
    }

    @Override
    public void evictValue(K key) {
        RMapCache<K, V> cache = getRMapCache();
        cache.remove(key);
        if (log.isDebugEnabled()) {
            log.debug("redis evict cache name {} key {}", this.name, key);
        }

    }

    @Override
    public void bathEvictValue(List<K> keys) {
        RMapCache<K, V> cache = getRMapCache();
        keys.forEach(cache::remove);
        if (log.isDebugEnabled()) {
            log.debug("remove redis cache name {} keys {}", this.name, keys);
        }

    }

    @Override
    public void clear() {
        RMapCache<K, V> cache = getRMapCache();
        cache.clear();
        log.info("clear redis cache name {}", this.name);
    }

    @Override
    public Map<K, V> getValues(List<K> keys) {
        RMapCache<K, V> cache = getRMapCache();
        Map<K, V> valuesMap = cache.getAll(new HashSet<>(keys));
        if (log.isDebugEnabled()) {
            log.info("redis lookup cache {} key {}", this.name, keys);
        }
        return valuesMap;
    }

    @Override
    public Map<K, V> getValuesAsync(List<K> keys) {
        RMapCache<K, V> cache = getRMapCache();
        RFuture<Map<K, V>> mapRFuture = cache.getAllAsync(new HashSet<>(keys));
        try {
            Map<K, V> valuesMap = mapRFuture.get();
            if (log.isDebugEnabled()) {
                log.info("redis lookup cache {} key {}", this.name, keys);
            }
            return valuesMap;
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("cache [" + this.name + "] getAllAsync value error", e);
        }
    }

    @Override
    public void putValues(Map<K, V> map) {
        RMapCache<K, V> cache = getRMapCache();
        cache.putAll(map, getTtl(), this.cacheable.getUnit());
    }

    @Override
    public void putValuesAsync(Map<K, V> map) {
        RMapCache<K, V> cache = getRMapCache();
        cache.putAllAsync(map, getTtl(), this.cacheable.getUnit());
    }

    @Override
    public V getValue(K key, Callable<V> valueLoader) {
        RMapCache<K, V> cache = getRMapCache();
        if (log.isDebugEnabled()) {
            log.info("redis get cache name {} key {}", this.name, key);
        }
        return fromStoreValue(cache.get(key));
    }

    @Override
    protected V lookup(K key) {
        RMapCache<K, V> cache = getRMapCache();
        if (log.isDebugEnabled()) {
            log.info("redis lookup cache {} key {}", this.name, key);
        }
        return fromStoreValue(cache.get(key));
    }
}
