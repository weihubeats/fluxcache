package com.fluxcache.core.caffeine.sync;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.impl.FluxRedissonCaffeineCache;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.util.ObjectUtils;

/**
 * @author : wh
 * @date : 2024/11/10 15:58
 * @description:
 */
@Slf4j
@RequiredArgsConstructor
public class RedissonLocalCacheEvictListener implements ApplicationRunner, Ordered {

    private final FluxCacheManager cacheManager;

    private final FluxCacheProperties cacheProperties;

    private final RedissonClient redissonClient;

    @Override
    public void run(ApplicationArguments args) {
        final String topicName = DeleteCacheDTO.topicName(cacheProperties.namespace(), DeleteCacheDTO.CACHE_EVICT_TOPIC_PREFIX);
        RTopic topic = redissonClient.getTopic(topicName);
        topic.addListener(DeleteCacheDTO.class, this::onMessage);
    }

    @Override
    public int getOrder() {
        return 0;
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
        if (cache instanceof FluxRedissonCaffeineCache) {
            FluxRedissonCaffeineCache fluxRedissonCaffeineCache = (FluxRedissonCaffeineCache) cache;
            caffeineCache = fluxRedissonCaffeineCache.getFluxFirstCache();
        }
        if (Objects.isNull(caffeineCache)) {
            return;
        }
        if (deleteCacheDTO.isAll()) {
            caffeineCache.clearDirectly();
        } else {
            caffeineCache.bathEvictDirectly(deleteCacheDTO.getKeys());
        }
        if (log.isDebugEnabled()) {
            log.debug("cacheName {} 本地缓存清除完成 key {}", deleteCacheDTO.getCacheName(), deleteCacheDTO.getKeys());
        }

    }
}

