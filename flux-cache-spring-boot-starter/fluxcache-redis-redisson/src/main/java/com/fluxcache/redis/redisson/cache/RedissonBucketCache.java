package com.fluxcache.redis.redisson.cache;

import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.utils.TtlUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Redis cache backed by Redisson {@link RBucket}.
 */
@Slf4j
public class RedissonBucketCache<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

    private final FluxCacheCacheable cacheable;
    private final RedissonClient redissonClient;
    private final String redisName;

    public RedissonBucketCache(boolean allowNullValues, RedissonClient redissonClient,
                               FluxCacheCacheable cacheable) {
        super(allowNullValues, cacheable.getCacheName());
        this.redissonClient = redissonClient;
        this.cacheable = cacheable;
        this.redisName = String.join(":", "FluxCache", cacheable.getCacheName());
    }

    @Override
    protected Map<K, V> getValues(List<K> keys) {
        if (ObjectUtils.isEmpty(keys)) {
            return Map.of();
        }
        Map<K, V> retMap = new HashMap<>(keys.size());
        Set<String> cacheKeys = keys.stream().map(this::buildKey).collect(Collectors.toSet());
        Map<String, V> kvMap = this.redissonClient.getBuckets().get(cacheKeys.toArray(new String[0]));
        if (kvMap == null || kvMap.isEmpty()) {
            return retMap;
        }
        for (K key : keys) {
            V value = kvMap.get(buildKey(key));
            if (value != null) {
                retMap.put(key, value);
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
        // one batch round trip instead of N SETs
        RBatch batch = this.redissonClient.createBatch();
        map.forEach((k, v) -> batch.getBucket(buildKey(k)).setAsync(v, effectiveTtl()));
        batch.execute();
    }

    @Override
    protected void putValuesAsync(Map<K, V> map) {
        if (ObjectUtils.isEmpty(map)) {
            return;
        }
        map.forEach((k, v) -> this.redissonClient.getBucket(buildKey(k)).setAsync(v, effectiveTtl())
                .whenComplete((r, e) -> {
                    if (e != null) {
                        log.warn("redis async put failed cache name {} key {}", this.redisName, buildKey(k), e);
                    }
                }));
    }

    @Override
    protected V getValue(K key, Callable<V> valueLoader) {
        RBucket<V> bucket = this.redissonClient.getBucket(buildKey(key));
        return bucket.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void putValue(K key, Object value) {
        RBucket<V> bucket = this.redissonClient.getBucket(buildKey(key));
        bucket.set((V) value, effectiveTtl());
        if (log.isDebugEnabled()) {
            log.debug("redis put cache name {} key {}", this.redisName, buildKey(key));
        }
    }

    @Override
    protected void evictValue(K key) {
        this.redissonClient.getBucket(buildKey(key)).delete();
        if (log.isDebugEnabled()) {
            log.debug("redis evict cache name {} key {}", this.redisName, buildKey(key));
        }
    }

    @Override
    protected void batchEvictValue(List<K> keys) {
        RBatch batch = this.redissonClient.createBatch();
        keys.forEach(key -> batch.getBucket(buildKey(key)).deleteAsync());
        batch.execute();
    }

    @Override
    protected V lookup(K key) {
        return getValue(key, null);
    }

    @Override
    public void clear() {
        String pattern = this.redisName + ":*";
        List<String> toDelete = new ArrayList<>();
        for (String key : this.redissonClient.getKeys().getKeysByPattern(pattern, 500)) {
            toDelete.add(key);
        }
        for (int i = 0; i < toDelete.size(); i += 500) {
            List<String> chunk = toDelete.subList(i, Math.min(i + 500, toDelete.size()));
            this.redissonClient.getKeys().delete(chunk.toArray(new String[0]));
        }
        log.info("clear redis cache name {} keys {}", this.redisName, toDelete.size());
    }

    private String buildKey(Object key) {
        if (ObjectUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Flux cache must not null");
        }
        return String.join(":", this.redisName, key.toString());
    }

    private Duration effectiveTtl() {
        return TtlUtils.randomizedTtl(this.cacheable.getTtl(), this.cacheable.getUnit());
    }
}
