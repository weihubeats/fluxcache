package com.fluxcache.core.caffeine.sync;

import com.fluxcache.core.model.AbstractLocalCacheDTO;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.model.PutCacheDTO;
import com.fluxcache.core.utils.JsonUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

/**
 * @author : wh
 * @date : 2024/11/10 22:55
 * @description:
 */
@RequiredArgsConstructor
@Slf4j
public class RedissonCacheSyncStrategy implements CacheSyncStrategy {

    private final RedissonClient redissonClient;

    private final List<CacheSyncPostProcessor> cacheSyncPostProcessors;

    @Override
    public void postClear(DeleteCacheDTO deleteCacheDTO) {
        RTopic topic = redissonClient.getTopic(deleteCacheDTO.getTopicName());
        try {
            // todo 后续是否对多节点返回数据监控
            topic.publish(deleteCacheDTO);
        } catch (Exception e) {
            log.info("分布式缓存刷新通知异常,缓存 {}", deleteCacheDTO, e);
            //todo 异常后置处理
        }
        postProcess(deleteCacheDTO);

    }

    @Override
    public void postEvict(DeleteCacheDTO deleteCacheDTO) {
        RTopic topic = redissonClient.getTopic(deleteCacheDTO.getTopicName());
        try {
            // todo 后续是否对多节点返回数据监控
            topic.publish(deleteCacheDTO);
        } catch (Exception e) {
            log.info("分布式缓存刷新通知异常,缓存 {}", JsonUtil.serialize2Json(deleteCacheDTO), e);
            //todo 异常后置处理
        }
        postProcess(deleteCacheDTO);
    }

    @Override
    public void sendPutEvent(PutCacheDTO putCacheDTO) {
        RTopic topic = redissonClient.getTopic(putCacheDTO.getTopicName());
        try {
            // todo 后续是否对多节点返回数据监控
            topic.publish(putCacheDTO);
        } catch (Exception e) {
            log.info("分布式缓存刷新通知异常,缓存 {}", putCacheDTO, e);
            //todo 异常后置处理
        }
        postProcess(putCacheDTO);
    }

    private void postProcess(AbstractLocalCacheDTO abstractLocalCacheDTO) {
        if (ObjectUtils.isNotEmpty(this.cacheSyncPostProcessors)) {
            this.cacheSyncPostProcessors.forEach(cacheSyncPostProcessor -> cacheSyncPostProcessor.postProcessAfterClear(abstractLocalCacheDTO));
        }
    }
}
