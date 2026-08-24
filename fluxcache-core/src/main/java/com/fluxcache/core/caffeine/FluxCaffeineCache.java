package com.fluxcache.core.caffeine;

import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.model.PutCacheDTO;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.utils.JsonUtil;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * @author : wh
 * @date : 2024/10/6 13:50
 */
@Slf4j
public class FluxCaffeineCache<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

    private final CacheSyncStrategy cacheSyncStrategy;

    private final FluxCacheProperties fluxCacheProperties;

    private final com.github.benmanes.caffeine.cache.Cache cache;

    public FluxCaffeineCache(String name, com.github.benmanes.caffeine.cache.Cache cache,
                             CacheSyncStrategy cacheSyncStrategy, FluxCacheProperties cacheProperties) {
        this(name, cache, true, cacheSyncStrategy, cacheProperties);
    }

    public FluxCaffeineCache(String name, com.github.benmanes.caffeine.cache.Cache cache,
                             boolean allowCacheNull, CacheSyncStrategy cacheSyncStrategy,
                             FluxCacheProperties cacheProperties) {
        super(allowCacheNull, name);
        Assert.notNull(name, "Name must not be null");
        Assert.notNull(cache, "Cache must not be null");
        this.cache = cache;
        this.cacheSyncStrategy = cacheSyncStrategy;
        this.fluxCacheProperties = cacheProperties;
    }

    public final com.github.benmanes.caffeine.cache.Cache getNativeCache() {
        return this.cache;
    }

    @Override
    protected Map<K, V> getValues(List<K> keys) {
        if (this.cache instanceof LoadingCache) {
            return ((LoadingCache<K, V>) this.cache).getAll(keys);
        }
        return this.cache.getAllPresent(keys);
    }

    @Override
    protected Map<K, V> getValuesAsync(List<K> keys) {
        if (this.cache instanceof LoadingCache) {
            return ((LoadingCache<K, V>) this.cache).getAll(keys);
        }
        return this.cache.getAllPresent(keys);
    }

    @Override
    protected void putValues(Map<K, V> map) {
        map.forEach((k, v) -> putValue(k, toStoreValue(v)));
    }

    @Override
    protected void putValuesAsync(Map<K, V> map) {
        map.forEach((k, v) -> putValue(k, toStoreValue(v)));
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    protected V getValue(K key, final Callable<V> valueLoader) {
        return fromStoreValue((V) this.cache.get(key, new LoadFunction(valueLoader)));
    }

    @Override
    @Nullable
    protected V lookup(K key) {
        if (this.cache instanceof LoadingCache) {
            return ((LoadingCache<K, V>) this.cache).get(key);
        }
        return (V) this.cache.getIfPresent(key);
    }

    @Override
    protected void putValue(K key, @Nullable Object value) {
        // value is already store-adapted when called from put(); putAll path converts in putValues
        this.cache.put(key, value);
        if (log.isDebugEnabled()) {
            log.debug("caffeine cache put key {} value {}", key, JsonUtil.serialize2Json(value));
        }
        this.sendPutEvent(key, value);
    }

    @Override
    @Nullable
    public ValueWrapper<V> putIfAbsent(K key, @Nullable final V value) {
        PutIfAbsentFunction callable = new PutIfAbsentFunction(value);
        V result = (V) this.cache.get(key, callable);
        return (callable.called ? null : toValueWrapper(result));
    }

    @Override
    protected void evictValue(K key) {
        this.cache.invalidate(key);
        this.postEvict(key);
    }

    @Override
    public void evictDirectly(K key) {
        this.cache.invalidate(key);
    }

    @Override
    protected void batchEvictValue(List<K> keys) {
        keys.forEach(this.cache::invalidate);
        if (log.isDebugEnabled()) {
            log.debug("batchEvict key {}", keys);
        }
        this.postEvict(keys);
    }

    @Override
    public void batchEvictDirectly(List<K> keys) {
        for (K key : keys) {
            this.cache.invalidate(key);
        }
    }

    @Override
    public boolean evictIfPresent(K key) {
        return (this.cache.asMap().remove(key) != null);
    }

    @Override
    public void clear() {
        this.cache.invalidateAll();
        log.info("caffeine clear cache name {}", this.name);
        this.postClear();
    }

    @Override
    public boolean clearDirectly() {
        this.cache.invalidateAll();
        return true;
    }

    @Override
    public void putDirectly(K key, Object value) {
        this.cache.put(key, toStoreValue(value));
    }

    @Override
    public void putAllDirectly(Map<K, ?> values) {
        this.cache.putAll(values);
    }

    @Override
    public boolean invalidate() {
        boolean notEmpty = !this.cache.asMap().isEmpty();
        this.cache.invalidateAll();
        return notEmpty;
    }

    protected void sendPutEvent(K key, Object value) {
        PutCacheDTO putCacheDTO = new PutCacheDTO(this.name, key, value);
        putCacheDTO.setTopicName(putCacheDTO.topicName(fluxCacheProperties.namespace()));
        cacheSyncStrategy.sendPutEvent(putCacheDTO);
    }

    protected void postEvict(K key) {
        DeleteCacheDTO deleteCacheDTO = new DeleteCacheDTO(this.name, Collections.singletonList(key));
        deleteCacheDTO.setTopicName(deleteCacheDTO.topicName(fluxCacheProperties.namespace()));
        cacheSyncStrategy.postEvict(deleteCacheDTO);
    }

    protected void postEvict(List<K> key) {
        DeleteCacheDTO deleteCacheDTO = new DeleteCacheDTO(this.name, (List<Object>) key);
        deleteCacheDTO.setTopicName(deleteCacheDTO.topicName(fluxCacheProperties.namespace()));
        cacheSyncStrategy.postEvict(deleteCacheDTO);
    }

    protected void postClear() {
        final DeleteCacheDTO deleteCacheDTO = new DeleteCacheDTO(this.name);
        deleteCacheDTO.setTopicName(deleteCacheDTO.topicName(fluxCacheProperties.namespace()));
        cacheSyncStrategy.postClear(deleteCacheDTO);
    }

    private class PutIfAbsentFunction implements Function<K, V> {

        @Nullable
        private final Object value;

        private boolean called;

        public PutIfAbsentFunction(@Nullable Object value) {
            this.value = value;
        }

        @Override
        public Object apply(Object key) {
            this.called = true;
            return toStoreValue(this.value);
        }
    }

    private class LoadFunction implements Function<K, V> {

        private final Callable<?> valueLoader;

        public LoadFunction(Callable<?> valueLoader) {
            this.valueLoader = valueLoader;
        }

        @Override
        public Object apply(Object o) {
            try {
                return toStoreValue(this.valueLoader.call());
            } catch (Exception ex) {
                throw new ValueRetrievalException(o, this.valueLoader, ex);
            }
        }
    }

}
