package com.fluxcache.core.caffeine;

import java.util.List;

/**
 * @author : wh
 * @date : 2024/9/19 21:47
 * @description:
 */
public interface LocalCache<K, V> {

    default void evictDirectly(K key) {}

    default void bathEvictDirectly(List<K> keys) {}

    default boolean clearDirectly() {return false;}
    
    default void putDirectly(K key, Object value) {}

}
