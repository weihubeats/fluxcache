package com.fluxcache.redis.spring.cache;

import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.utils.TtlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.util.ArrayList;
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
        // 一次 MGET 代替 N 次 GET，减少 RTT
        List<String> redisKeys = new ArrayList<>(keys.size());
        for (K key : keys) {
            redisKeys.add(buildKey(key));
        }
        List<Object> values = redisTemplate.opsForValue().multiGet(redisKeys);
        Map<K, V> retMap = new HashMap<>(keys.size());
        if (ObjectUtils.isEmpty(values)) {
            return retMap;
        }
        int size = Math.min(keys.size(), values.size());
        for (int i = 0; i < size; i++) {
            Object value = values.get(i);
            if (value != null) {
                retMap.put(keys.get(i), (V) value);
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
        // pipeline 批量写入，一次 RTT 代替 N 次 SET
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K1, V1> Object execute(RedisOperations<K1, V1> operations) {
                for (Map.Entry<K, V> entry : map.entrySet()) {
                    operations.opsForValue().set(
                            (K1) buildKey(entry.getKey()), (V1) entry.getValue(), effectiveTtl());
                }
                return null;
            }
        });
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
        redisTemplate.opsForValue().set(buildKey(key), value, effectiveTtl());
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
        String pattern = this.redisName + ":*";
        List<String> toDelete = new ArrayList<>();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(500).build())) {
                while (cursor.hasNext()) {
                    // key serializer is StringRedisSerializer, raw bytes are plain UTF-8
                    toDelete.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        if (!toDelete.isEmpty()) {
            redisTemplate.delete(toDelete);
        }
        log.info("clear redis cache name {} keys {}", this.redisName, toDelete.size());
    }

    private String buildKey(Object key) {
        if (ObjectUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Flux cache key must not be null");
        }
        return String.join(":", this.redisName, key.toString());
    }

    private Duration effectiveTtl() {
        return TtlUtils.randomizedTtl(this.cacheable.getTtl(), this.cacheable.getUnit());
    }
}
