package com.fluxcache.core.impl;

import com.fluxcache.core.model.FluxCacheCacheable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.redisson.api.RFuture;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author : wh
 * @date : 2024/11/13 22:09
 */
@Slf4j
public class FluxRedissonCacheByRMapCache<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

    private final FluxCacheCacheable cacheable;

    private final RedissonClient redissonClient;

    /**
     * Redis map name (logical cache name is {@link #name}).
     */
    private final String redisName;

    public FluxRedissonCacheByRMapCache(boolean allowNullValues, RedissonClient redissonClient,
                                        FluxCacheCacheable cacheable) {
        super(allowNullValues, cacheable.getCacheName());
        this.redissonClient = redissonClient;
        this.cacheable = cacheable;
        this.redisName = String.join(":", "FluxCache", cacheable.getCacheName());
    }

    public RMapCache<K, V> getRMapCache() {
        if (log.isDebugEnabled()) {
            log.debug("getRMapCache name {}", redisName);
        }
        return redissonClient.getMapCache(redisName);
    }

    public RMapCache<K, Object> getPutRMapCache() {
        if (log.isDebugEnabled()) {
            log.debug("getRMapCache name {}", redisName);
        }
        return redissonClient.getMapCache(redisName);
    }

    @Override
    protected void putValue(K key, Object value) {
        RMapCache<K, Object> cache = getPutRMapCache();
        Long ttl = getTtl();
        cache.put(key, value, ttl, this.cacheable.getUnit());
        if (log.isDebugEnabled()) {
            log.debug("redis put key {} value {}", key, value);
        }
    }

    private Long getTtl() {
        Long ttl = this.cacheable.getTtl();
        if (Objects.equals(this.cacheable.getUnit(), TimeUnit.MINUTES)
                || Objects.equals(this.cacheable.getUnit(), TimeUnit.SECONDS)) {
            ttl = ttl + RandomUtils.nextInt(1, 10);
        }
        return ttl;
    }

    @Override
    protected void evictValue(K key) {
        RMapCache<K, V> cache = getRMapCache();
        cache.remove(key);
        if (log.isDebugEnabled()) {
            log.debug("redis evict cache name {} key {}", this.redisName, key);
        }
    }

    @Override
    protected void batchEvictValue(List<K> keys) {
        RMapCache<K, V> cache = getRMapCache();
        keys.forEach(cache::remove);
        if (log.isDebugEnabled()) {
            log.debug("remove redis cache name {} keys {}", this.redisName, keys);
        }
    }

    @Override
    public void clear() {
        RMapCache<K, V> cache = getRMapCache();
        cache.clear();
        log.info("clear redis cache name {}", this.redisName);
    }

    @Override
    protected Map<K, V> getValues(List<K> keys) {
        RMapCache<K, V> cache = getRMapCache();
        Map<K, V> valuesMap = cache.getAll(new HashSet<>(keys));
        if (log.isDebugEnabled()) {
            log.info("redis lookup cache {} key {}", this.redisName, keys);
        }
        return valuesMap;
    }

    @Override
    protected Map<K, V> getValuesAsync(List<K> keys) {
        RMapCache<K, V> cache = getRMapCache();
        RFuture<Map<K, V>> mapRFuture = cache.getAllAsync(new HashSet<>(keys));
        try {
            Map<K, V> valuesMap = mapRFuture.get();
            if (log.isDebugEnabled()) {
                log.info("redis lookup cache {} key {}", this.redisName, keys);
            }
            return valuesMap;
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("cache [" + this.redisName + "] getAllAsync value error", e);
        }
    }

    @Override
    protected void putValues(Map<K, V> map) {
        RMapCache<K, V> cache = getRMapCache();
        cache.putAll(map, getTtl(), this.cacheable.getUnit());
    }

    @Override
    protected void putValuesAsync(Map<K, V> map) {
        RMapCache<K, V> cache = getRMapCache();
        cache.putAllAsync(map, getTtl(), this.cacheable.getUnit());
    }

    @Override
    protected V getValue(K key, Callable<V> valueLoader) {
        RMapCache<K, V> cache = getRMapCache();
        if (log.isDebugEnabled()) {
            log.info("redis get cache name {} key {}", this.redisName, key);
        }
        return fromStoreValue(cache.get(key));
    }

    @Override
    protected V lookup(K key) {
        RMapCache<K, V> cache = getRMapCache();
        if (log.isDebugEnabled()) {
            log.info("redis lookup cache {} key {}", this.redisName, key);
        }
        return fromStoreValue(cache.get(key));
    }
}
