package com.fluxcache.core.config;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.annotation.FluxCacheAnnotationParser;
import com.fluxcache.core.annotation.FluxCacheEvict;
import com.fluxcache.core.annotation.FluxCachePut;
import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.FluxSpringCacheAnnotationParser;
import com.fluxcache.core.caffeine.RedissonLocalCacheEvictListener;
import com.fluxcache.core.caffeine.RedissonLocalCachePutListener;
import com.fluxcache.core.caffeine.sync.CacheSyncPostProcessor;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.caffeine.sync.RedissonCacheSyncStrategy;
import com.fluxcache.core.constants.ThreadPoolConstant;
import com.fluxcache.core.interceptor.FluxAnnotationCacheOperationSource;
import com.fluxcache.core.interceptor.FluxCacheAnnotationAdvisor;
import com.fluxcache.core.interceptor.FluxCacheAnnotationInterceptor;
import com.fluxcache.core.interceptor.FluxCacheOperationSource;
import com.fluxcache.core.manual.FluxCacheCreatePostProcess;
import com.fluxcache.core.manual.FluxCacheDataRegistered;
import com.fluxcache.core.monitor.DefaultFluxCacheMonitor;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.preheat.FluxCacheRefreshExecutor;
import com.fluxcache.core.preheat.FluxRefreshTaskRegistrar;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.redisson.api.RedissonClient;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static com.fluxcache.core.constants.ThreadPoolConstant.DEFAULT_CORE_POOL_SIZE;
import static com.fluxcache.core.constants.ThreadPoolConstant.DEFAULT_MAXIMUM_POOL_SIZE;
import static com.fluxcache.core.constants.ThreadPoolConstant.DEFAULT_QUEUE_SIZE;

/**
 * @author : wh
 * @date : 2024/11/12 12:38
 * @description:
 */
@Configuration(proxyBeanMethods = false)
//@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Import(FluxCacheProperties.class)
public class FluxProxyCacheAutoConfiguration {

    @Bean
    @Order(1)
    public Advisor FluxCacheAnnotationAdvisor(FluxCacheProperties cacheProperties,
        FluxCacheOperationSource FluxCacheOperationSource,
        FluxCacheManager cacheManager, FluxCacheMonitor cacheMonitor) {
        FluxCacheAnnotationInterceptor advisor = new FluxCacheAnnotationInterceptor(cacheProperties, FluxCacheOperationSource, cacheManager, cacheMonitor);
        return new FluxCacheAnnotationAdvisor(advisor, FluxCachePut.class);
    }

    @Bean
    @Order(1)
    public Advisor FluxCacheableAnnotationAdvisor(FluxCacheProperties cacheProperties,
        FluxCacheOperationSource FluxCacheOperationSource,
        FluxCacheManager cacheManager, FluxCacheMonitor cacheMonitor) {
        FluxCacheAnnotationInterceptor advisor = new FluxCacheAnnotationInterceptor(cacheProperties, FluxCacheOperationSource, cacheManager, cacheMonitor);
        return new FluxCacheAnnotationAdvisor(advisor, FluxCacheable.class);
    }

    @Bean
    @Order(1)
    public Advisor FluxCacheEvictAnnotationAdvisor(FluxCacheProperties cacheProperties,
        FluxCacheOperationSource FluxCacheOperationSource,
        FluxCacheManager cacheManager, FluxCacheMonitor cacheMonitor) {
        FluxCacheAnnotationInterceptor advisor = new FluxCacheAnnotationInterceptor(cacheProperties, FluxCacheOperationSource, cacheManager, cacheMonitor);
        return new FluxCacheAnnotationAdvisor(advisor, FluxCacheEvict.class);
    }

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public FluxCacheOperationSource FluxCacheOperationSource(FluxCacheAnnotationParser FluxCacheAnnotationParser) {
        return new FluxAnnotationCacheOperationSource(FluxCacheAnnotationParser);
    }

    @Bean
    public FluxCacheAnnotationParser FluxSpringCacheAnnotationParser(FluxCacheProperties cacheProperties) {
        return new FluxSpringCacheAnnotationParser(cacheProperties);
    }

    @Bean
    @ConditionalOnClass(RedissonClient.class)
    public CacheSyncStrategy redissonCacheSyncStrategy(RedissonClient redissonClient,
        List<CacheSyncPostProcessor> cacheSyncPostProcessors) {
        return new RedissonCacheSyncStrategy(redissonClient, cacheSyncPostProcessors);
    }

    @Bean
    @ConditionalOnClass(RedissonClient.class)
    public RedissonLocalCacheEvictListener redissonLocalCacheEvictListener(FluxCacheManager cacheManager,
        FluxCacheProperties cacheProperties, RedissonClient redissonClient) {
        return new RedissonLocalCacheEvictListener(cacheManager, cacheProperties, redissonClient);
    }

    @Bean
    @ConditionalOnClass(RedissonClient.class)
    public RedissonLocalCachePutListener redissonLocalCachePutListener(FluxCacheManager cacheManager,
        FluxCacheProperties cacheProperties, RedissonClient redissonClient) {
        return new RedissonLocalCachePutListener(cacheManager, cacheProperties, redissonClient);
    }

    @Bean
    public FluxCacheMonitor cacheMonitor(CacheThreadPoolExecutor threadPoolManager,
        FluxCacheProperties cacheProperties) {
        return new DefaultFluxCacheMonitor(threadPoolManager, cacheProperties);
    }

    @Bean
    @ConditionalOnMissingBean(CacheThreadPoolExecutor.class)
    public CacheThreadPoolExecutor cacheThreadPoolExecutor() {
        return new CacheThreadPoolExecutor(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAXIMUM_POOL_SIZE, DEFAULT_QUEUE_SIZE, ThreadPoolConstant.DEFAULT_KEEP_ALIVE_TIME
            , ThreadPoolConstant.DEFAULT_THREAD_NAME_PREFIX, new ThreadPoolExecutor.DiscardPolicy());
    }

    @Bean
    @ConditionalOnBean(FluxCacheDataRegistered.class)
    public FluxCacheCreatePostProcess createPostProcess(FluxCacheDataRegistered cacheDataRegistered,
        FluxCacheManager cacheManager, FluxCacheMonitor cacheMonitor, FluxCacheProperties cacheProperties) {
        return new FluxCacheCreatePostProcess(cacheDataRegistered, cacheManager, cacheProperties, cacheMonitor);
    }


    @Bean
    @ConditionalOnClass(RedissonClient.class)
    public FluxRefreshTaskRegistrar cacheRefreshTaskRegistrar(ApplicationContext context, TaskScheduler taskScheduler,
                                                              FluxCacheManager cacheManager, RedissonClient redissonClient) {
        return new FluxRefreshTaskRegistrar(context, taskScheduler, cacheManager, redissonClient, new FluxCacheRefreshExecutor());
    }

    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("flux-cache-refresh-");
        scheduler.setDaemon(true);
        return scheduler;
    }

}
