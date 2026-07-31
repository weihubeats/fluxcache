package com.fluxcache.redis.redisson.cache;

import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
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
 * Redis cache backed by Redisson {@link RMapCache} (per-entry TTL).
 */
@Slf4j
public class RedissonMapCache<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

    private final FluxCacheCacheable cacheable;
    private final RedissonClient redissonClient;
    private final String redisName;

    public RedissonMapCache(boolean allowNullValues, RedissonClient redissonClient,
                            FluxCacheCacheable cacheable) {
        super(allowNullValues, cacheable.getCacheName());
        this.redissonClient = redissonClient;
        this.cacheable = cacheable;
        this.redisName = String.join(":", "FluxCache", cacheable.getCacheName());
    }

    public RMapCache<K, V> getRMapCache() {
        return redissonClient.getMapCache(redisName);
    }

    public RMapCache<K, Object> getPutRMapCache() {
        return redissonClient.getMapCache(redisName);
    }

    @Override
    protected void putValue(K key, Object value) {
        getPutRMapCache().put(key, value, getTtl(), this.cacheable.getUnit());
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
        getRMapCache().remove(key);
    }

    @Override
    protected void batchEvictValue(List<K> keys) {
        RMapCache<K, V> cache = getRMapCache();
        keys.forEach(cache::remove);
    }

    @Override
    public void clear() {
        getRMapCache().clear();
        log.info("clear redis cache name {}", this.redisName);
    }

    @Override
    protected Map<K, V> getValues(List<K> keys) {
        return getRMapCache().getAll(new HashSet<>(keys));
    }

    @Override
    protected Map<K, V> getValuesAsync(List<K> keys) {
        RFuture<Map<K, V>> mapRFuture = getRMapCache().getAllAsync(new HashSet<>(keys));
        try {
            return mapRFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("cache [" + this.redisName + "] getAllAsync value error", e);
        }
    }

    @Override
    protected void putValues(Map<K, V> map) {
        getRMapCache().putAll(map, getTtl(), this.cacheable.getUnit());
    }

    @Override
    protected void putValuesAsync(Map<K, V> map) {
        getRMapCache().putAllAsync(map, getTtl(), this.cacheable.getUnit());
    }

    @Override
    protected V getValue(K key, Callable<V> valueLoader) {
        return fromStoreValue(getRMapCache().get(key));
    }

    @Override
    protected V lookup(K key) {
        return fromStoreValue(getRMapCache().get(key));
    }
}
