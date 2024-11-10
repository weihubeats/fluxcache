package com.fluxcache.core;

import java.util.List;

/**
 * @author : wh
 * @date : 2024/11/10 17:39
 * @description:
 */
public interface LocalCache<K, V> {

    default void evictDirectly(K key) {};

    default void bathEvictDirectly(List<K> keys) {};

    default boolean clearDirectly() {return false;};

    default void putDirectly(K key, Object value) {};

}