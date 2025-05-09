package com.fluxcache.core.impl;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxSimpleValueWrapper;
import com.fluxcache.core.model.FluxNullValue;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.monitor.FluxCacheMonitorEvent;
import com.fluxcache.core.monitor.MonitorEventEnum;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/10/6 13:51
 * @description:
 */
public abstract class FluxAbstractValueAdaptingCache<K, V> implements FluxCache<K, V> {

    /**
     * 是否缓存null
     */
    private final boolean allowCacheNull;

    private final FluxCacheMonitor cacheMonitor;

    protected String name;

    private final FluxCacheProperties cacheProperties;

    /**
     * Create an {@code AbstractValueAdaptingCache} with the given setting.
     *
     * @param allowCacheNull 
     */
    protected FluxAbstractValueAdaptingCache(boolean allowCacheNull, FluxCacheMonitor cacheMonitor, String name,
        FluxCacheProperties cacheProperties) {
        this.allowCacheNull = allowCacheNull;
        this.cacheMonitor = cacheMonitor;
        this.name = name;
        this.cacheProperties = cacheProperties;
    }
    

    @Override
    @Nullable
    public ValueWrapper<V> get(K key) {
        long startTime = System.currentTimeMillis();
        ValueWrapper<V> wrapper = toValueWrapper(lookup(key));
        publishCacheMonitorEvent(Objects.nonNull(wrapper) ? MonitorEventEnum.CACHE_HIT : MonitorEventEnum.CACHE_MISSING, 1L, startTime);
        return wrapper;
    }

    @Override
    @Nullable
    public V get(K key, @Nullable Class<V> type) {
        long startTime = System.currentTimeMillis();
        V value = fromStoreValue(lookup(key));
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException(
                "Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        publishCacheMonitorEvent(Objects.nonNull(value) ? MonitorEventEnum.CACHE_HIT : MonitorEventEnum.CACHE_MISSING, 1L, startTime);
        return value;
    }

    @Override
    public V get(K key, Callable<V> valueLoader) {
        long startTime = System.currentTimeMillis();
        V value = getValue(key, valueLoader);
        publishCacheMonitorEvent(value == null || !value.getClass().isAssignableFrom(valueLoader.getClass()) ?
            MonitorEventEnum.CACHE_MISSING : MonitorEventEnum.CACHE_HIT, 1L, startTime);
        return value;
    }

    @Override
    public void put(K key, @Nullable Object value) {
        if (Objects.isNull(value) && !allowCacheNull) {
            return;
        }
        long startTime = System.currentTimeMillis();
        putValue(key, toStoreValue(value));
        publishCacheMonitorEvent(MonitorEventEnum.CACHE_PUT, 1L, startTime);
    }

    @Override
    public Map<K, V> getAll(List<K> keys, Class<V> type) {
        long startTime = System.currentTimeMillis();
        if (Objects.isNull(keys)) {
            throw new IllegalArgumentException(
                "Cache '" + getName() + "' getAll is configured to not allow null list");
        }
        Map<K, V> map = getValues(keys);
        return getMap(startTime, map, keys.size());
    }

    private Map<K, V> getMap(long startTime, Map<K, V> map, int size) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        Map<K, V> values = new HashMap<>(map);
        values.replaceAll((key, value) -> fromStoreValue(value));
        publishCacheMonitorEvent(MonitorEventEnum.CACHE_HIT, values.size(), startTime);
        publishCacheMonitorEvent(MonitorEventEnum.CACHE_MISSING, size - values.size(), startTime);
        return values;
    }

    public abstract Map<K, V> getValues(List<K> keys);

    @Override
    public Map<K, V> getAllAsync(List<K> keys, Class<V> type) {
        long startTime = System.currentTimeMillis();
        if (Objects.isNull(keys)) {
            throw new IllegalArgumentException(
                "Cache '" + getName() + "' getAll is configured to not allow null list");
        }
        Map<K, V> map = getValuesAsync(keys);
        return getMap(startTime, map, keys.size());
    }

    public abstract Map<K, V> getValuesAsync(List<K> keys);

    @Override
    public void putAll(@Nullable Map<K, V> map) {
        long startTime = System.currentTimeMillis();
        if (Objects.isNull(map)) {
            throw new IllegalArgumentException(
                "Cache '" + getName() + "' putAll is configured to not allow null map");
        }
        putValues(map);
        publishCacheMonitorEvent(MonitorEventEnum.CACHE_PUT, map.size(), startTime);
    }

    @Override
    public void putAllAsync(@Nullable Map<K, V> map) {
        long startTime = System.currentTimeMillis();
        if (Objects.isNull(map)) {
            throw new IllegalArgumentException(
                "Cache '" + getName() + "' putAll is configured to not allow null map");
        }
        putValuesAsync(map);
        publishCacheMonitorEvent(MonitorEventEnum.CACHE_PUT, map.size(), startTime);
    }

    protected abstract void putValues(@Nullable Map<K, V> map);

    protected abstract void putValuesAsync(@Nullable Map<K, V> map);

    @Override
    public void evict(K key) {
        long startTime = System.currentTimeMillis();
        evictValue(key);
        publishCacheMonitorEvent(MonitorEventEnum.CACHE_EVICT, 1L, startTime);
    }

    @Override
    public void bathEvict(List<K> keys) {
        long startTime = System.currentTimeMillis();
        bathEvictValue(keys);
        publishCacheMonitorEvent(MonitorEventEnum.CACHE_EVICT, keys.size(), startTime);
    }

    @Nullable
    public abstract V getValue(K key, Callable<V> valueLoader);

    @Nullable
    public abstract void putValue(K key, Object value);

    @Nullable
    public abstract void evictValue(K key);

    @Nullable
    public abstract void bathEvictValue(List<K> keys);

    /**
     * Perform an actual lookup in the underlying store.
     *
     * @param key the key whose associated value is to be returned
     * @return the raw store value for the key, or {@code null} if none
     */
    @Nullable
    protected abstract V lookup(K key);

    /**
     * Convert the given value from the internal store to a user value
     * returned from the get method (adapting {@code null}).
     *
     * @param storeValue the store value
     * @return the value to return to the user
     */
    @Nullable
    protected V fromStoreValue(@Nullable V storeValue) {
        if (this.allowCacheNull && storeValue instanceof FluxNullValue) {
            return null;
        }
        return storeValue;
    }

    /**
     * Convert the given user value, as passed into the put method,
     * to a value in the internal store (adapting {@code null}).
     *
     * @param userValue the given user value
     * @return the value to store
     */
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

    public boolean allowCacheNull() {
        return this.allowCacheNull;
    }

    /**
     * Wrap the given store value with a {@link org.springframework.cache.support.SimpleValueWrapper}, also going
     * through {@link #fromStoreValue} conversion. Useful for {@link #get(Object)}
     * and {@link #putIfAbsent(Object, Object)} implementations.
     *
     * @param storeValue the original value
     * @return the wrapped value
     */
    @Nullable
    protected FluxCache.ValueWrapper<V> toValueWrapper(@Nullable V storeValue) {
        return (storeValue != null ? new FluxSimpleValueWrapper<>(fromStoreValue(storeValue)) : null);
    }

    private void publishCacheMonitorEvent(MonitorEventEnum eventEnum, long count, long startTime) {
        if (cacheProperties.isCacheMonitorEnable()) {
            FluxCacheMonitorEvent event = new FluxCacheMonitorEvent();
            event.setCacheName(this.name);
            event.setMonitorEventEnum(eventEnum);
            event.setLoadTime(System.currentTimeMillis() - startTime);
            event.setCount(count);
            cacheMonitor.publishMonitorEvent(event);
        }
    }

}
