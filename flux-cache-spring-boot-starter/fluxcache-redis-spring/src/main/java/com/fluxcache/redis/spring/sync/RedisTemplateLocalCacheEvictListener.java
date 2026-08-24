package com.fluxcache.redis.spring.sync;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.impl.FluxMultiLevelCache;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

/**
 * Subscribes to local-cache evict events. Subscription happens on bean init
 * (not after ApplicationReady) so invalidations published during deployment are not lost.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisTemplateLocalCacheEvictListener implements InitializingBean, DisposableBean, MessageListener {

    private final FluxCacheManager cacheManager;
    private final FluxCacheProperties cacheProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    private ChannelTopic topic;

    @Override
    public void afterPropertiesSet() {
        String topicName = DeleteCacheDTO.topicName(cacheProperties.namespace(), DeleteCacheDTO.CACHE_EVICT_TOPIC_PREFIX);
        this.topic = new ChannelTopic(topicName);
        listenerContainer.addMessageListener(this, topic);
    }

    @Override
    public void destroy() {
        if (topic != null) {
            listenerContainer.removeMessageListener(this, topic);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        Object body = redisTemplate.getValueSerializer().deserialize(message.getBody());
        if (!(body instanceof DeleteCacheDTO)) {
            return;
        }
        DeleteCacheDTO deleteCacheDTO = (DeleteCacheDTO) body;
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
