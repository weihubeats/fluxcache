package com.fluxcache.core;

import java.util.List;
import java.util.Map;

/**
 * Local-only cache mutations that must not publish distributed sync events
 * (used by Redis pub/sub listeners to avoid feedback loops).
 *
 * @author : wh
 * @date : 2024/11/10 17:39
 */
public interface LocalCache<K, V> {

    default void evictDirectly(K key) {
    }

    default void batchEvictDirectly(List<K> keys) {
    }

    default boolean clearDirectly() {
        return false;
    }

    default void putDirectly(K key, Object value) {
    }

    /**
     * Batch put of already store-adapted values without publishing sync events.
     */
    default void putAllDirectly(Map<K, ?> values) {
        values.forEach(this::putDirectly);
    }
}
