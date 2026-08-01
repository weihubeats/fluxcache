package com.fluxcache.redis.spring.sync;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.PutCacheDTO;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Subscribes to local-cache put events.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisTemplateLocalCachePutListener implements ApplicationRunner, Ordered, MessageListener {

    private final FluxCacheManager cacheManager;
    private final FluxCacheProperties cacheProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    @Override
    public void run(ApplicationArguments args) {
        String topicName = PutCacheDTO.topicName(cacheProperties.namespace(), PutCacheDTO.CACHE_PUT_TOPIC_PREFIX);
        listenerContainer.addMessageListener(this, new ChannelTopic(topicName));
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

    @Override
    public int getOrder() {
        return 0;
    }
}
