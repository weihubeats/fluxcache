package com.fluxcache.redis.redisson.sync;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.impl.FluxMultiLevelCache;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class RedissonLocalCacheEvictListener implements InitializingBean, DisposableBean {

    private final FluxCacheManager cacheManager;
    private final FluxCacheProperties cacheProperties;
    private final RedissonClient redissonClient;

    private int listenerId = -1;

    @Override
    public void afterPropertiesSet() {
        String topicName = DeleteCacheDTO.topicName(cacheProperties.namespace(), DeleteCacheDTO.CACHE_EVICT_TOPIC_PREFIX);
        RTopic topic = redissonClient.getTopic(topicName);
        this.listenerId = topic.addListener(DeleteCacheDTO.class, this::onMessage);
    }

    @Override
    public void destroy() {
        if (listenerId >= 0) {
            redissonClient.getTopic(
                    DeleteCacheDTO.topicName(cacheProperties.namespace(), DeleteCacheDTO.CACHE_EVICT_TOPIC_PREFIX))
                    .removeListener(listenerId);
        }
    }

    private void onMessage(CharSequence channel, DeleteCacheDTO deleteCacheDTO) {
        if (deleteCacheDTO == null) {
            return;
        }
        FluxCache cache = cacheManager.getCache(deleteCacheDTO.getCacheName());
        if (ObjectUtils.isEmpty(cache)) {
            return;
        }
        FluxAbstractValueAdaptingCache caffeineCache = null;
        if (cache instanceof FluxCaffeineCache) {
            caffeineCache = (FluxCaffeineCache) cache;
        }
        if (cache instanceof FluxMultiLevelCache) {
            caffeineCache = ((FluxMultiLevelCache) cache).getFluxFirstCache();
        }
        if (Objects.isNull(caffeineCache)) {
            return;
        }
        if (deleteCacheDTO.isAll()) {
            caffeineCache.clearDirectly();
        } else {
            caffeineCache.batchEvictDirectly(deleteCacheDTO.getKeys());
        }
        if (log.isDebugEnabled()) {
            log.debug("cacheName {} 本地缓存清除完成 key {}", deleteCacheDTO.getCacheName(), deleteCacheDTO.getKeys());
        }
    }
}
