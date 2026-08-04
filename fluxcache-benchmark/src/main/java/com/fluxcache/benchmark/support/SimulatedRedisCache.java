package com.fluxcache.benchmark.support;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * A Redis-backed {@link com.fluxcache.core.FluxCache} level-2 implementation used by the
 * benchmark. Reads / writes go through {@link SimulatedRedis} and therefore cost one
 * simulated network round trip.
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class SimulatedRedisCache extends FluxAbstractValueAdaptingCache<String, Object> {

    private final SimulatedRedis redis;

    public SimulatedRedisCache(String name, SimulatedRedis redis) {
        super(true, name);
        this.redis = redis;
    }

    @Override
    protected Map<String, Object> getValues(List<String> keys) {
        Map<String, Object> found = new HashMap<>(keys.size());
        for (String key : keys) {
            Object value = redis.get(key);
            if (value != null) {
                found.put(key, value);
            }
        }
        return found;
    }

    @Override
    protected Map<String, Object> getValuesAsync(List<String> keys) {
        return getValues(keys);
    }

    @Override
    protected void putValues(Map<String, Object> map) {
        map.forEach(redis::put);
    }

    @Override
    protected void putValuesAsync(Map<String, Object> map) {
        map.forEach(redis::put);
    }

    @Override
    protected Object getValue(String key, Callable<Object> valueLoader) {
        Object cached = lookup(key);
        if (Objects.nonNull(cached)) {
            return fromStoreValue(cached);
        }
        try {
            Object value = valueLoader.call();
            putValue(key, toStoreValue(value));
            return value;
        } catch (Exception ex) {
            throw new FluxCache.ValueRetrievalException(key, valueLoader, ex);
        }
    }

    @Override
    protected void putValue(String key, Object value) {
        redis.put(key, value);
    }

    @Override
    protected void evictValue(String key) {
        redis.delete(key);
    }

    @Override
    protected void batchEvictValue(List<String> keys) {
        keys.forEach(redis::delete);
    }

    @Override
    protected Object lookup(String key) {
        return redis.get(key);
    }

    @Override
    public void clear() {
        redis.clear();
    }
}