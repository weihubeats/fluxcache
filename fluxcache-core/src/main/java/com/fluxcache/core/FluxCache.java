package com.fluxcache.core;

import com.fluxcache.core.enums.CacheOrder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/9/19 21:49
 * @description:
 */
public interface FluxCache<K, V> extends LocalCache<K, V> {

    V get(K key, Callable<V> valueLoader);

    CacheOrder ordered();

    String getName();

    FluxCache.ValueWrapper<V> get(K key);

    @Nullable
    V get(K key, @Nullable Class<V> type);

    @Nullable
    Map<K, V> getAll(List<K> keys, @Nullable Class<V> type);

    @Nullable
    Map<K, V> getAllAsync(List<K> keys, @Nullable Class<V> type);

    void put(K key, @Nullable Object value);

    void putAll(@Nullable Map<K, V> object);

    void putAllAsync(@Nullable Map<K, V> object);

    @Nullable
    default FluxCache.ValueWrapper putIfAbsent(K key, @Nullable V value) {
        FluxCache.ValueWrapper existingValue = get(key);
        if (existingValue == null) {
            put(key, value);
        }
        return existingValue;
    }

    /**
     * 失效缓存
     *
     * @param key
     */
    void evict(K key);

    /**
     * 批量失效
     *
     * @param keys
     */
    void bathEvict(List<K> keys);

    default boolean evictIfPresent(K key) {
        evict(key);
        return false;
    }

    /**
     * 清理所有缓存
     */
    void clear();

    /**
     * 清理缓存
     *
     * @return
     */
    default boolean invalidate() {
        clear();
        return false;
    }

    /**
     * 是否缓存null
     * @return
     */
    boolean allowCacheNull();

    @FunctionalInterface
    interface ValueWrapper<V> {

        /**
         * Return the actual value in the cache.
         */
        @Nullable
        V get();
    }

    class ValueRetrievalException extends RuntimeException {
        @Nullable
        private final Object key;

        public ValueRetrievalException(@Nullable Object key, Callable<?> loader, Throwable ex) {
            super(String.format("Value for key '%s' could not be loaded using '%s'", key, loader), ex);
            this.key = key;
        }

        @Nullable
        public Object getKey() {
            return this.key;
        }
    }

}
