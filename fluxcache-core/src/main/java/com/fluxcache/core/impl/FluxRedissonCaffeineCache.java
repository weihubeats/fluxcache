package com.fluxcache.core.impl;

import com.fluxcache.core.enums.CacheOrder;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import lombok.Getter;

/**
 * @author : wh
 * @date : 2024/11/10 16:00
 * @description:
 */
@Getter
public class FluxRedissonCaffeineCache<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

    private final FluxAbstractValueAdaptingCache<K, V> fluxFirstCache;

    private final FluxAbstractValueAdaptingCache<K, V> fluxSecondaryCache;

    /**
     * Create an {@code AbstractValueAdaptingCache} with the given setting.
     *
     * @param allowNullValues whether to allow for {@code null} values
     */
    protected FluxRedissonCaffeineCache(boolean allowNullValues, String name,
        FluxAbstractValueAdaptingCache<K, V> fluxFirstCache, FluxAbstractValueAdaptingCache<K, V> fluxSecondaryCache,
        FluxCacheMonitor cacheMonitor, FluxCacheProperties cacheProperties) {
        super(allowNullValues, cacheMonitor, name, cacheProperties);
        this.fluxFirstCache = fluxFirstCache;
        this.fluxSecondaryCache = fluxSecondaryCache;
    }

    @Override
    public Map<K, V> getValues(List<K> keys) {
        return getAll(keys);
    }

    private Map<K, V> getAll(List<K> keys) {
        List<K> list = new ArrayList<>(keys);
        Map<K, V> firstCacheValues = fluxFirstCache.getValuesAsync(keys);
        Map<K, V> res = new HashMap<>(firstCacheValues);
        list.removeAll(firstCacheValues.keySet());
        Map<K, V> secondaryCacheValues = fluxSecondaryCache.getValuesAsync(list);
        res.putAll(secondaryCacheValues);
        fluxFirstCache.putAllAsync(secondaryCacheValues);
        return res;
    }

    @Override
    public Map<K, V> getValuesAsync(List<K> keys) {
        return getAll(keys);
    }

    @Override
    protected void putValues(Map<K, V> map) {
        fluxFirstCache.putAll(map);
        fluxSecondaryCache.putAll(map);
    }

    @Override
    protected void putValuesAsync(Map<K, V> map) {
        fluxFirstCache.putAllAsync(map);
        fluxSecondaryCache.putAllAsync(map);
    }

    @Override
    public V getValue(K key, Callable<V> valueLoader) {
        V caffeineCacheResult = fluxFirstCache.get(key, valueLoader);
        if (Objects.nonNull(caffeineCacheResult)) {
            return caffeineCacheResult;
        }
        V redisCacheResult = fluxSecondaryCache.get(key, valueLoader);
        if (Objects.nonNull(redisCacheResult)) {
            fluxFirstCache.put(key, redisCacheResult);
            return redisCacheResult;
        }
        return null;
    }

    @Override
    protected V lookup(K key) {
        V lookup = fluxFirstCache.lookup(key);
        if (Objects.nonNull(lookup)) {
            return lookup;
        }
        V redisLookup = fluxSecondaryCache.lookup(key);
        if (Objects.nonNull(redisLookup)) {
            fluxFirstCache.putDirectly(key, redisLookup);
        }
        return redisLookup;
    }

    @Override
    public CacheOrder ordered() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void putValue(K key, Object value) {
        fluxFirstCache.put(key, value);
        fluxSecondaryCache.put(key, value);
    }

    @Override
    public void evictValue(K key) {
        fluxFirstCache.evict(key);
        fluxSecondaryCache.evict(key);
    }

    @Override
    public void bathEvictValue(List<K> keys) {
        fluxFirstCache.bathEvict(keys);
        fluxSecondaryCache.bathEvict(keys);
    }

    @Override
    public void clear() {
        fluxFirstCache.clear();
        fluxSecondaryCache.clear();

    }

}

