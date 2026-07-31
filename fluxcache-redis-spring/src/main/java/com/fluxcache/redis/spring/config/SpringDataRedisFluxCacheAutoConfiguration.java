package com.fluxcache.redis.spring.config;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.caffeine.sync.CacheSyncPostProcessor;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.config.FluxCacheCreatorAutoConfiguration;
import com.fluxcache.core.lock.FluxDistributedLock;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.spi.FluxCacheCreator;
import com.fluxcache.redis.spring.creator.SpringDataRedisFluxCacheCreator;
import com.fluxcache.redis.spring.lock.RedisTemplateDistributedLock;
import com.fluxcache.redis.spring.sync.RedisTemplateCacheSyncStrategy;
import com.fluxcache.redis.spring.sync.RedisTemplateLocalCacheEvictListener;
import com.fluxcache.redis.spring.sync.RedisTemplateLocalCachePutListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Spring Data Redis auto-configuration for FluxCache.
 * Enabled when {@code fluxcache-redis-spring} is on the classpath and a {@link RedisConnectionFactory} bean exists.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnBean(RedisConnectionFactory.class)
@AutoConfigureBefore(FluxCacheCreatorAutoConfiguration.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class SpringDataRedisFluxCacheAutoConfiguration {

    public static final String FLUX_CACHE_REDIS_TEMPLATE = "fluxCacheRedisTemplate";

    @Bean(name = FLUX_CACHE_REDIS_TEMPLATE)
    @ConditionalOnMissingBean(name = FLUX_CACHE_REDIS_TEMPLATE)
    public RedisTemplate<String, Object> fluxCacheRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        RedisSerializer<Object> valueSerializer = new GenericJackson2JsonRedisSerializer(new ObjectMapper());
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @Order(FluxCacheCreatorAutoConfiguration.BUILTIN_CREATOR_ORDER)
    @ConditionalOnMissingBean(name = "springDataRedisFluxCacheCreator")
    public FluxCacheCreator springDataRedisFluxCacheCreator(
            @org.springframework.beans.factory.annotation.Qualifier(FLUX_CACHE_REDIS_TEMPLATE)
            RedisTemplate<String, Object> fluxCacheRedisTemplate) {
        return new SpringDataRedisFluxCacheCreator(fluxCacheRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(CacheSyncStrategy.class)
    public CacheSyncStrategy redisTemplateCacheSyncStrategy(
            @org.springframework.beans.factory.annotation.Qualifier(FLUX_CACHE_REDIS_TEMPLATE)
            RedisTemplate<String, Object> fluxCacheRedisTemplate,
            List<CacheSyncPostProcessor> cacheSyncPostProcessors) {
        return new RedisTemplateCacheSyncStrategy(fluxCacheRedisTemplate, cacheSyncPostProcessors);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisMessageListenerContainer fluxCacheRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    public RedisTemplateLocalCacheEvictListener redisTemplateLocalCacheEvictListener(
            FluxCacheManager cacheManager,
            FluxCacheProperties cacheProperties,
            @org.springframework.beans.factory.annotation.Qualifier(FLUX_CACHE_REDIS_TEMPLATE)
            RedisTemplate<String, Object> fluxCacheRedisTemplate,
            RedisMessageListenerContainer fluxCacheRedisMessageListenerContainer) {
        return new RedisTemplateLocalCacheEvictListener(
                cacheManager, cacheProperties, fluxCacheRedisTemplate, fluxCacheRedisMessageListenerContainer);
    }

    @Bean
    public RedisTemplateLocalCachePutListener redisTemplateLocalCachePutListener(
            FluxCacheManager cacheManager,
            FluxCacheProperties cacheProperties,
            @org.springframework.beans.factory.annotation.Qualifier(FLUX_CACHE_REDIS_TEMPLATE)
            RedisTemplate<String, Object> fluxCacheRedisTemplate,
            RedisMessageListenerContainer fluxCacheRedisMessageListenerContainer) {
        return new RedisTemplateLocalCachePutListener(
                cacheManager, cacheProperties, fluxCacheRedisTemplate, fluxCacheRedisMessageListenerContainer);
    }

    @Bean
    @ConditionalOnMissingBean(FluxDistributedLock.class)
    public FluxDistributedLock redisTemplateDistributedLock(StringRedisTemplate stringRedisTemplate) {
        return new RedisTemplateDistributedLock(stringRedisTemplate);
    }
}
