package com.fluxcache.redis.redisson.cache;

import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.utils.TtlUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RFuture;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        getPutRMapCache().put(key, value, effectiveTtl().toMillis(), TimeUnit.MILLISECONDS);
        if (log.isDebugEnabled()) {
            log.debug("redis put key {} value {}", key, value);
        }
    }

    private Duration effectiveTtl() {
        return TtlUtils.randomizedTtl(this.cacheable.getTtl(), this.cacheable.getUnit());
    }

    @Override
    protected void evictValue(K key) {
        getRMapCache().remove(key);
    }

    @Override
    protected void batchEvictValue(List<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        // single round trip instead of N removes
        @SuppressWarnings("unchecked")
        K[] keyArray = (K[]) keys.toArray();
        getRMapCache().fastRemove(keyArray);
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
        if (map == null || map.isEmpty()) {
            return;
        }
        getRMapCache().putAll(map, effectiveTtl().toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected void putValuesAsync(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        getRMapCache().putAllAsync(map, effectiveTtl().toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((r, e) -> {
                    if (e != null) {
                        log.warn("redis async putAll failed cache name {}", this.redisName, e);
                    }
                });
    }

    @Override
    protected V getValue(K key, Callable<V> valueLoader) {
        return fromStoreValue(getRMapCache().get(key));
    }

    @Override
    protected V lookup(K key) {
        // raw store value: keep FluxNullValue marker so the multi-level cache can
        // distinguish "hit with null" (penetration protection) from a real miss
        return getRMapCache().get(key);
    }
}
