package com.fluxcache.core.caffeine.sync;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.PutCacheDTO;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/**
 * @author : wh
 * @date : 2024/11/10 22:56
 * @description:
 */
@Slf4j
@RequiredArgsConstructor
public class RedissonLocalCachePutListener implements ApplicationRunner, Ordered {

    private final FluxCacheManager cacheManager;

    private final FluxCacheProperties cacheProperties;

    private final RedissonClient redissonClient;

    @Override
    public void run(ApplicationArguments args) {
        final String topicName = PutCacheDTO.topicName(cacheProperties.namespace(), PutCacheDTO.CACHE_PUT_TOPIC_PREFIX);
        RTopic topic = redissonClient.getTopic(topicName);
        topic.addListener(PutCacheDTO.class, this::onMessage);
    }

    private void onMessage(CharSequence channel, PutCacheDTO putCacheDTO) {
        // todo put handler
        FluxCache cache = cacheManager.getCache(putCacheDTO.getCacheName());
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

