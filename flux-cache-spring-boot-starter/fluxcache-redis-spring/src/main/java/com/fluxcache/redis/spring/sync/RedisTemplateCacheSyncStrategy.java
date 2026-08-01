package com.fluxcache.redis.spring.sync;

import com.fluxcache.core.caffeine.sync.CacheSyncPostProcessor;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.model.AbstractLocalCacheDTO;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.model.PutCacheDTO;
import com.fluxcache.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

/**
 * Pub/sub cache sync via Spring Data Redis.
 */
@RequiredArgsConstructor
@Slf4j
public class RedisTemplateCacheSyncStrategy implements CacheSyncStrategy {

    private final RedisTemplate<String, Object> redisTemplate;
    private final List<CacheSyncPostProcessor> cacheSyncPostProcessors;

    @Override
    public void postClear(DeleteCacheDTO deleteCacheDTO) {
        publish(deleteCacheDTO.getTopicName(), deleteCacheDTO);
        postProcess(deleteCacheDTO);
    }

    @Override
    public void postEvict(DeleteCacheDTO deleteCacheDTO) {
        try {
            publish(deleteCacheDTO.getTopicName(), deleteCacheDTO);
        } catch (Exception e) {
            log.info("分布式缓存刷新通知异常,缓存 {}", JsonUtil.serialize2Json(deleteCacheDTO), e);
        }
        postProcess(deleteCacheDTO);
    }

    @Override
    public void sendPutEvent(PutCacheDTO putCacheDTO) {
        try {
            publish(putCacheDTO.getTopicName(), putCacheDTO);
        } catch (Exception e) {
            log.info("分布式缓存刷新通知异常,缓存 {}", putCacheDTO, e);
        }
        postProcess(putCacheDTO);
    }

    private void publish(String topic, Object payload) {
        redisTemplate.convertAndSend(topic, payload);
    }

    private void postProcess(AbstractLocalCacheDTO abstractLocalCacheDTO) {
        if (ObjectUtils.isNotEmpty(this.cacheSyncPostProcessors)) {
            this.cacheSyncPostProcessors.forEach(p -> p.postProcess(abstractLocalCacheDTO));
        }
    }
}
