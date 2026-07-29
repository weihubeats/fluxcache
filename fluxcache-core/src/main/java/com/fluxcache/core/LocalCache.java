package com.fluxcache.core;

import java.util.List;

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
}
