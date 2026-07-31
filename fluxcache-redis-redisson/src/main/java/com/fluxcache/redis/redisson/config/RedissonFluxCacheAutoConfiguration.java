package com.fluxcache.redis.redisson.config;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.caffeine.sync.CacheSyncPostProcessor;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.config.FluxCacheCreatorAutoConfiguration;
import com.fluxcache.core.lock.FluxDistributedLock;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.spi.FluxCacheCreator;
import com.fluxcache.redis.redisson.creator.RedissonBucketFluxCacheCreator;
import com.fluxcache.redis.redisson.creator.RedissonMapFluxCacheCreator;
import com.fluxcache.redis.redisson.lock.RedissonDistributedLock;
import com.fluxcache.redis.redisson.sync.RedissonCacheSyncStrategy;
import com.fluxcache.redis.redisson.sync.RedissonLocalCacheEvictListener;
import com.fluxcache.redis.redisson.sync.RedissonLocalCachePutListener;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * Redisson auto-configuration for FluxCache (no spring-data-redis dependency).
 * Enabled when {@code fluxcache-redis-redisson} is on the classpath and a {@link RedissonClient} bean exists.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnBean(RedissonClient.class)
@AutoConfigureBefore(FluxCacheCreatorAutoConfiguration.class)
public class RedissonFluxCacheAutoConfiguration {

    @Bean
    @Order(FluxCacheCreatorAutoConfiguration.BUILTIN_CREATOR_ORDER)
    @ConditionalOnMissingBean(name = "redissonBucketFluxCacheCreator")
    public FluxCacheCreator redissonBucketFluxCacheCreator(RedissonClient redissonClient) {
        return new RedissonBucketFluxCacheCreator(redissonClient);
    }

    @Bean
    @Order(FluxCacheCreatorAutoConfiguration.BUILTIN_CREATOR_ORDER)
    @ConditionalOnMissingBean(name = "redissonMapFluxCacheCreator")
    public FluxCacheCreator redissonMapFluxCacheCreator(RedissonClient redissonClient) {
        return new RedissonMapFluxCacheCreator(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean(CacheSyncStrategy.class)
    public CacheSyncStrategy redissonCacheSyncStrategy(RedissonClient redissonClient,
                                                       List<CacheSyncPostProcessor> cacheSyncPostProcessors) {
        return new RedissonCacheSyncStrategy(redissonClient, cacheSyncPostProcessors);
    }

    @Bean
    public RedissonLocalCacheEvictListener redissonLocalCacheEvictListener(FluxCacheManager cacheManager,
                                                                           FluxCacheProperties cacheProperties,
                                                                           RedissonClient redissonClient) {
        return new RedissonLocalCacheEvictListener(cacheManager, cacheProperties, redissonClient);
    }

    @Bean
    public RedissonLocalCachePutListener redissonLocalCachePutListener(FluxCacheManager cacheManager,
                                                                       FluxCacheProperties cacheProperties,
                                                                       RedissonClient redissonClient) {
        return new RedissonLocalCachePutListener(cacheManager, cacheProperties, redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean(FluxDistributedLock.class)
    public FluxDistributedLock redissonDistributedLock(RedissonClient redissonClient) {
        return new RedissonDistributedLock(redissonClient);
    }
}
