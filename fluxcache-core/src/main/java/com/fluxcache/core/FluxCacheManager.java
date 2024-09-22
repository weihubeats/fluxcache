package com.fluxcache.core;

import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheStatics;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author : wh
 * @date : 2024/9/22 16:22
 * @description:
 */
public interface FluxCacheManager {

    void createCache(FluxMultilevelCacheCacheable cacheable);

    <K, V> FluxCache<K, V> getCache(String cacheName);

    <K, V> boolean putCache(String key, FluxCache<K, V> cache);

    List<FluxCache> getAllCaches();

    <K, V> V getCacheOrPut(String cacheName, K key, Callable<V> valueLoader);

    /**
     * 清除指定缓存
     *
     * @param cacheName
     * @param keys
     * @return
     */
    <K, V> boolean evictCache(String cacheName, List<K> keys);

    <K, V> boolean clearCacheByName(String cacheName);

    FluxCacheOperation getCacheMetaData(String cacheName);

    List<FluxCacheOperation> getAllCacheMetaData();

    /**
     * 注册缓存
     *
     * @param cacheOperations
     * @return
     */
    default void registerCache(FluxCacheOperation... cacheOperations) {
    }

    FluxCacheStatics getCacheStatics(String cacheName);
}
