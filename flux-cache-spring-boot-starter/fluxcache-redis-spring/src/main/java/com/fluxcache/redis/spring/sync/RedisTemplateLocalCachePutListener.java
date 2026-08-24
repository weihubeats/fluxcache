package com.fluxcache.redis.spring.sync;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.PutCacheDTO;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Subscribes to local-cache put events. Subscription happens on bean init
 * (not after ApplicationReady) so events published during deployment are not lost.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisTemplateLocalCachePutListener implements InitializingBean, DisposableBean, MessageListener {

    private final FluxCacheManager cacheManager;
    private final FluxCacheProperties cacheProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    private ChannelTopic topic;

    @Override
    public void afterPropertiesSet() {
        String topicName = PutCacheDTO.topicName(cacheProperties.namespace(), PutCacheDTO.CACHE_PUT_TOPIC_PREFIX);
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
        if (!(body instanceof PutCacheDTO)) {
            return;
        }
        PutCacheDTO putCacheDTO = (PutCacheDTO) body;
        FluxCache cache = cacheManager.getCache(putCacheDTO.getCacheName());
        if (cache == null) {
            return;
        }
        cache.putDirectly(putCacheDTO.getKey(), putCacheDTO.getCacheValue());
        if (log.isDebugEnabled()) {
            log.debug("caffeine put key {} cache {}", putCacheDTO.getKey(), JsonUtil.serialize2Json(putCacheDTO));
        }
    }
}
