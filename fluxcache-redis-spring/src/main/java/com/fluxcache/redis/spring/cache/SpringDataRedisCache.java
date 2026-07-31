package com.fluxcache.redis.spring.cache;

import com.fluxcache.core.exception.FluxCacheNotSupperException;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.model.FluxCacheCacheable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Redis cache backed by Spring Data {@link RedisTemplate} value operations
 * (key = {@code FluxCache:{cacheName}:{key}}).
 */
@Slf4j
public class SpringDataRedisCache<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

    private final FluxCacheCacheable cacheable;
    private final RedisTemplate<String, Object> redisTemplate;
    private final String redisName;

    public SpringDataRedisCache(boolean allowNullValues,
                                RedisTemplate<String, Object> redisTemplate,
                                FluxCacheCacheable cacheable) {
        super(allowNullValues, cacheable.getCacheName());
        this.redisTemplate = redisTemplate;
        this.cacheable = cacheable;
        this.redisName = String.join(":", "FluxCache", cacheable.getCacheName());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<K, V> getValues(List<K> keys) {
        if (ObjectUtils.isEmpty(keys)) {
            return Map.of();
        }
        Map<K, V> retMap = new HashMap<>(keys.size());
        for (K key : keys) {
            Object value = redisTemplate.opsForValue().get(buildKey(key));
            if (value != null) {
                retMap.put(key, (V) value);
            }
        }
        return retMap;
    }

    @Override
    protected Map<K, V> getValuesAsync(List<K> keys) {
        return getValues(keys);
    }

    @Override
    protected void putValues(Map<K, V> map) {
        if (ObjectUtils.isEmpty(map)) {
            return;
        }
        map.forEach(this::putValue);
    }

    @Override
    protected void putValuesAsync(Map<K, V> map) {
        putValues(map);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected V getValue(K key, Callable<V> valueLoader) {
        return (V) redisTemplate.opsForValue().get(buildKey(key));
    }

    @Override
    protected void putValue(K key, Object value) {
        Long ttl = getTtl();
        redisTemplate.opsForValue().set(buildKey(key), value, Duration.of(ttl, cacheable.getUnit().toChronoUnit()));
        if (log.isDebugEnabled()) {
            log.debug("redis put cache name {} key {}", this.redisName, buildKey(key));
        }
    }

    @Override
    protected void evictValue(K key) {
        redisTemplate.delete(buildKey(key));
        if (log.isDebugEnabled()) {
            log.debug("redis evict cache name {} key {}", this.redisName, buildKey(key));
        }
    }

    @Override
    protected void batchEvictValue(List<K> keys) {
        if (ObjectUtils.isEmpty(keys)) {
            return;
        }
        Collection<String> redisKeys = keys.stream().map(this::buildKey).collect(java.util.stream.Collectors.toList());
        redisTemplate.delete(redisKeys);
    }

    @Override
    protected V lookup(K key) {
        return getValue(key, null);
    }

    @Override
    public void clear() {
        throw new FluxCacheNotSupperException(
                "REDIS bucket style cache does not support clear all, use batchEvict by keys instead");
    }

    private String buildKey(Object key) {
        if (ObjectUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Flux cache key must not be null");
        }
        return String.join(":", this.redisName, key.toString());
    }

    private Long getTtl() {
        Long ttl = this.cacheable.getTtl();
        if (Objects.equals(this.cacheable.getUnit(), TimeUnit.MINUTES)
                || Objects.equals(this.cacheable.getUnit(), TimeUnit.SECONDS)) {
            ttl = ttl + RandomUtils.nextInt(1, 10);
        }
        return ttl;
    }
}
