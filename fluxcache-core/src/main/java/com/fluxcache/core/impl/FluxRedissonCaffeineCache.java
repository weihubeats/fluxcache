package com.fluxcache.core.impl;

import com.fluxcache.core.FluxCache;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * @author : wh
 * @date : 2024/11/10 16:00
 */
@Getter
public class FluxRedissonCaffeineCache<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

    private final FluxAbstractValueAdaptingCache<K, V> fluxFirstCache;

    private final FluxAbstractValueAdaptingCache<K, V> fluxSecondaryCache;

    protected FluxRedissonCaffeineCache(boolean allowCacheNull, String name,
                                        FluxAbstractValueAdaptingCache<K, V> fluxFirstCache,
                                        FluxAbstractValueAdaptingCache<K, V> fluxSecondaryCache) {
        super(allowCacheNull, name);
        this.fluxFirstCache = fluxFirstCache;
        this.fluxSecondaryCache = fluxSecondaryCache;
    }

    @Override
    protected Map<K, V> getValues(List<K> keys) {
        return getAllMerged(keys);
    }

    private Map<K, V> getAllMerged(List<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Map<K, V> first = fluxFirstCache.getValues(keys);
        Map<K, V> res = new HashMap<>(Math.max(keys.size(), 16));
        if (first != null && !first.isEmpty()) {
            res.putAll(first);
        }
        if (res.size() >= keys.size()) {
            return res;
        }
        List<K> remain = new ArrayList<>(keys.size() - res.size());
        for (K key : keys) {
            if (!res.containsKey(key)) {
                remain.add(key);
            }
        }
        if (!remain.isEmpty()) {
            Map<K, V> second = fluxSecondaryCache.getValues(remain);
            if (second != null && !second.isEmpty()) {
                res.putAll(second);
                fluxFirstCache.putAllAsync(second);
            }
        }
        return res;
    }

    @Override
    protected Map<K, V> getValuesAsync(List<K> keys) {
        return getAllMerged(keys);
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
    protected V getValue(K key, Callable<V> valueLoader) {
        V cached = lookup(key);
        if (Objects.nonNull(cached)) {
            return fromStoreValue(cached);
        }
        V value;
        try {
            value = valueLoader.call();
        } catch (Exception ex) {
            throw new FluxCache.ValueRetrievalException(key, valueLoader, ex);
        }
        put(key, value);
        return value;
    }

    @Override
    protected V lookup(K key) {
        V lookup = fluxFirstCache.lookup(key);
        if (Objects.nonNull(lookup)) {
            return lookup;
        }
        V secondaryLookup = fluxSecondaryCache.lookup(key);
        if (Objects.nonNull(secondaryLookup)) {
            fluxFirstCache.putDirectly(key, secondaryLookup);
        }
        return secondaryLookup;
    }

    @Override
    protected void putValue(K key, Object value) {
        fluxFirstCache.put(key, value);
        fluxSecondaryCache.put(key, value);
    }

    @Override
    protected void evictValue(K key) {
        fluxFirstCache.evict(key);
        fluxSecondaryCache.evict(key);
    }

    @Override
    protected void batchEvictValue(List<K> keys) {
        fluxFirstCache.batchEvict(keys);
        fluxSecondaryCache.batchEvict(keys);
    }

    @Override
    public void clear() {
        fluxFirstCache.clear();
        fluxSecondaryCache.clear();
    }
}
