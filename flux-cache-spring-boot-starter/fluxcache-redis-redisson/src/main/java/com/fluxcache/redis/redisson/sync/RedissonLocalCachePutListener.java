package com.fluxcache.redis.redisson.sync;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.PutCacheDTO;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@Slf4j
@RequiredArgsConstructor
public class RedissonLocalCachePutListener implements InitializingBean, DisposableBean {

    private final FluxCacheManager cacheManager;
    private final FluxCacheProperties cacheProperties;
    private final RedissonClient redissonClient;

    private int listenerId = -1;

    @Override
    public void afterPropertiesSet() {
        String topicName = PutCacheDTO.topicName(cacheProperties.namespace(), PutCacheDTO.CACHE_PUT_TOPIC_PREFIX);
        RTopic topic = redissonClient.getTopic(topicName);
        this.listenerId = topic.addListener(PutCacheDTO.class, this::onMessage);
    }

    @Override
    public void destroy() {
        if (listenerId >= 0) {
            redissonClient.getTopic(
                    PutCacheDTO.topicName(cacheProperties.namespace(), PutCacheDTO.CACHE_PUT_TOPIC_PREFIX))
                    .removeListener(listenerId);
        }
    }

    private void onMessage(CharSequence channel, PutCacheDTO putCacheDTO) {
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
