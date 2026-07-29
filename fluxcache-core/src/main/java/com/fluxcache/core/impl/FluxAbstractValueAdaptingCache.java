package com.fluxcache.core.impl;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxSimpleValueWrapper;
import com.fluxcache.core.model.FluxNullValue;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Null-value adapting template for {@link FluxCache}.
 * Subclasses implement store operations only.
 *
 * @author : wh
 * @date : 2024/10/6 13:51
 */
public abstract class FluxAbstractValueAdaptingCache<K, V> implements FluxCache<K, V> {

    private final boolean allowCacheNull;

    protected final String name;

    protected FluxAbstractValueAdaptingCache(boolean allowCacheNull, String name) {
        this.allowCacheNull = allowCacheNull;
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    @Nullable
    public ValueWrapper<V> get(K key) {
        V storeVal = lookup(key);
        return toValueWrapper(storeVal);
    }

    @Override
    @Nullable
    public V get(K key, @Nullable Class<V> type) {
        V value = fromStoreValue(lookup(key));
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException(
                    "Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return value;
    }

    @Override
    public V get(K key, Callable<V> valueLoader) {
        return getValue(key, valueLoader);
    }

    @Override
    public void put(K key, @Nullable Object value) {
        if (value == null && !allowCacheNull) {
            return;
        }
        putValue(key, toStoreValue(value));
    }

    @Override
    public Map<K, V> getAll(List<K> keys, Class<V> type) {
        if (Objects.isNull(keys)) {
            throw new IllegalArgumentException(
                    "Cache '" + getName() + "' getAll is configured to not allow null list");
        }
        return adaptStoreMap(getValues(keys));
    }

    @Override
    public Map<K, V> getAllAsync(List<K> keys, Class<V> type) {
        if (Objects.isNull(keys)) {
            throw new IllegalArgumentException(
                    "Cache '" + getName() + "' getAll is configured to not allow null list");
        }
        return adaptStoreMap(getValuesAsync(keys));
    }

    private Map<K, V> adaptStoreMap(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        Map<K, V> values = new HashMap<>(map.size());
        map.forEach((k, v) -> values.put(k, fromStoreValue(v)));
        return values;
    }

    @Override
    public void putAll(@Nullable Map<K, V> map) {
        if (Objects.isNull(map)) {
            throw new IllegalArgumentException(
                    "Cache '" + getName() + "' putAll is configured to not allow null map");
        }
        putValues(map);
    }

    @Override
    public void putAllAsync(@Nullable Map<K, V> map) {
        if (Objects.isNull(map)) {
            throw new IllegalArgumentException(
                    "Cache '" + getName() + "' putAll is configured to not allow null map");
        }
        putValuesAsync(map);
    }

    @Override
    public void evict(K key) {
        evictValue(key);
    }

    @Override
    public void batchEvict(List<K> keys) {
        batchEvictValue(keys);
    }

    @Override
    public boolean allowCacheNull() {
        return this.allowCacheNull;
    }

    protected abstract Map<K, V> getValues(List<K> keys);

    protected abstract Map<K, V> getValuesAsync(List<K> keys);

    protected abstract void putValues(@Nullable Map<K, V> map);

    protected abstract void putValuesAsync(@Nullable Map<K, V> map);

    @Nullable
    protected abstract V getValue(K key, Callable<V> valueLoader);

    protected abstract void putValue(K key, Object value);

    protected abstract void evictValue(K key);

    protected abstract void batchEvictValue(List<K> keys);

    /**
     * Raw store lookup (may return {@link FluxNullValue}).
     */
    @Nullable
    protected abstract V lookup(K key);

    @Nullable
    protected V fromStoreValue(@Nullable V storeValue) {
        if (this.allowCacheNull && storeValue instanceof FluxNullValue) {
            return null;
        }
        return storeValue;
    }

    protected Object toStoreValue(@Nullable Object userValue) {
        if (userValue == null) {
            if (this.allowCacheNull) {
                return FluxNullValue.INSTANCE;
            }
            throw new IllegalArgumentException(
                    "Cache '" + getName() + "' is configured to not allow null values but null was provided");
        }
        return userValue;
    }

    @Nullable
    protected FluxCache.ValueWrapper<V> toValueWrapper(@Nullable V storeValue) {
        return (storeValue != null ? new FluxSimpleValueWrapper<>(fromStoreValue(storeValue)) : null);
    }
}
