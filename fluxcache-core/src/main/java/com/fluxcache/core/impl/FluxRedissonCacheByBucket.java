package com.fluxcache.core.impl;

import com.fluxcache.core.enums.CacheOrder;
import com.fluxcache.core.exception.FluxCacheNotSupperException;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.util.ObjectUtils;

/**
 * @author : wh
 * @date : 2024/11/13 22:05
 * @description:
 */
@Slf4j
public class FluxRedissonCacheByBucket<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

    private final FluxCacheCacheable cacheable;

    /**
     * cache config
     */
    private final RedissonClient redissonClient;

    // todo  maybe use parent name, but not sure, because in order to be compatible with older versions 
    private final String name;

    protected FluxRedissonCacheByBucket(boolean allowNullValues, RedissonClient redissonClient,
        FluxCacheCacheable cacheable, FluxCacheMonitor cacheMonitor, FluxCacheProperties cacheProperties) {
        super(allowNullValues, cacheMonitor, cacheable.getCacheName(), cacheProperties);
        this.redissonClient = redissonClient;
        this.cacheable = cacheable;
        this.name = String.join(":", "FluxCache", cacheable.getCacheName());
    }

    @Override
    public Map<K, V> getValues(List<K> keys) {
        final Map<K, V> retMap = new HashMap<>(1 << 4);
        Set<String> cacheKeys = keys.stream().map(this::buildKey).collect(Collectors.toSet());
        if (!ObjectUtils.isEmpty(cacheKeys)) {
            Map<String, V> kvMap = this.redissonClient.getBuckets().get(cacheKeys.toArray(new String[0]));
            for (String k : cacheKeys) {
                if (Objects.nonNull(k) && Objects.nonNull(kvMap)) {
                    V v = kvMap.get(buildKey(k));
                    // todo type conversion
                    retMap.put((K)k, v);
                }
            }
            return retMap;
        }
        return null;
    }

    @Override
    public Map<K, V> getValuesAsync(List<K> keys) {
        // todo: this is a dummy step, not really async for now
        final Map<K, V> retMap = new HashMap<>(1 << 4);
        if (!ObjectUtils.isEmpty(keys)) {
            Set<String> cacheKeys = keys.stream().map(this::buildKey).collect(Collectors.toSet());
            Map<String, V> kvMap = this.redissonClient.getBuckets().get(cacheKeys.toArray(new String[0]));
            for (String k : cacheKeys) {
                if (Objects.nonNull(k) && Objects.nonNull(kvMap)) {
                    V v = kvMap.get(k);
                    retMap.put((K)k, v);
                }
            }
            return retMap;
        }
        return Map.of();
    }

    @Override
    protected void putValues(Map<K, V> map) {
        if (!ObjectUtils.isEmpty(map)) {
            map.forEach((k, v) -> {
                RBucket<V> bucket = this.redissonClient.getBucket(buildKey(k));
                Long ttl = getTtl();
                bucket.set(v, ttl, this.cacheable.getUnit());
            });
        }

    }

    @Override
    protected void putValuesAsync(Map<K, V> map) {
        if (!ObjectUtils.isEmpty(map)) {
            map.forEach((k, v) -> {
                RBucket<V> bucket = this.redissonClient.getBucket(buildKey(k));
                Long ttl = getTtl();
                bucket.setAsync(v, ttl, this.cacheable.getUnit());
            });
        }


    }

    @Override
    public V getValue(K key, Callable<V> valueLoader) {
        RBucket<V> bucket = this.redissonClient.getBucket(buildKey(key));
        return bucket.get();
    }

    @Override
    public void putValue(K key, Object value) {
        RBucket<V> bucket = this.redissonClient.getBucket(buildKey(key));
        Long ttl = getTtl();
        bucket.set((V) value, ttl, this.cacheable.getUnit());
        if (log.isDebugEnabled()) {
            log.debug("redis put cache name {} key {}", this.name, buildKey(key));
        }

    }

    @Override
    public void evictValue(K key) {
        this.redissonClient.getBucket(buildKey(key)).delete();
        if (log.isDebugEnabled()) {
            log.debug("redis evict cache name {} key {}", this.name, buildKey(key));
        }

    }

    @Override
    public void bathEvictValue(List<K> keys) {
        RBatch batch = this.redissonClient.createBatch();
        keys.forEach(key -> {
            batch.getBucket(buildKey(key)).deleteAsync();
            batch.execute();
        });

    }

    @Override
    protected V lookup(K key) {
        return getValue(key, null);
    }

    @Override
    public CacheOrder ordered() {
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void clear() {
        throw new FluxCacheNotSupperException("bucket not supper clear all, maybe use clear by keys, You can use the bathEvictValue");

    }

    private String buildKey(Object key) {
        if (ObjectUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Flux cache must not null");
        }
        return String.join(":", this.name, key.toString());
    }

    private Long getTtl() {
        Long ttl = this.cacheable.getTtl();
        // 防止缓存雪崩
        if (Objects.equals(this.cacheable.getUnit(), TimeUnit.MINUTES) || Objects.equals(this.cacheable.getUnit(), TimeUnit.SECONDS)) {
            ttl = ttl + RandomUtils.nextInt(1, 10);
        }
        return ttl;
    }

}
