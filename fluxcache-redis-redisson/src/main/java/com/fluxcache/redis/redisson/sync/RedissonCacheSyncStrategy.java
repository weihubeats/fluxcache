package com.fluxcache.redis.redisson.sync;

import com.fluxcache.core.caffeine.sync.CacheSyncPostProcessor;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.model.AbstractLocalCacheDTO;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.model.PutCacheDTO;
import com.fluxcache.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class RedissonCacheSyncStrategy implements CacheSyncStrategy {

    private final RedissonClient redissonClient;
    private final List<CacheSyncPostProcessor> cacheSyncPostProcessors;

    @Override
    public void postClear(DeleteCacheDTO deleteCacheDTO) {
        RTopic topic = redissonClient.getTopic(deleteCacheDTO.getTopicName());
        try {
            topic.publish(deleteCacheDTO);
        } catch (Exception e) {
            log.info("分布式缓存刷新通知异常,缓存 {}", deleteCacheDTO, e);
        }
        postProcess(deleteCacheDTO);
    }

    @Override
    public void postEvict(DeleteCacheDTO deleteCacheDTO) {
        RTopic topic = redissonClient.getTopic(deleteCacheDTO.getTopicName());
        try {
            topic.publish(deleteCacheDTO);
        } catch (Exception e) {
            log.info("分布式缓存刷新通知异常,缓存 {}", JsonUtil.serialize2Json(deleteCacheDTO), e);
        }
        postProcess(deleteCacheDTO);
    }

    @Override
    public void sendPutEvent(PutCacheDTO putCacheDTO) {
        RTopic topic = redissonClient.getTopic(putCacheDTO.getTopicName());
        try {
            topic.publish(putCacheDTO);
        } catch (Exception e) {
            log.info("分布式缓存刷新通知异常,缓存 {}", putCacheDTO, e);
        }
        postProcess(putCacheDTO);
    }

    private void postProcess(AbstractLocalCacheDTO abstractLocalCacheDTO) {
        if (ObjectUtils.isNotEmpty(this.cacheSyncPostProcessors)) {
            this.cacheSyncPostProcessors.forEach(p -> p.postProcessAfterClear(abstractLocalCacheDTO));
        }
    }
}
