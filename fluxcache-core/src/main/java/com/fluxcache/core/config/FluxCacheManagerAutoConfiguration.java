package com.fluxcache.core.config;

import com.fluxcache.core.DefaultFluxCacheManager;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.impl.FluxCacheFactory;
import com.fluxcache.core.interceptor.FluxCacheOperationSource;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/**
 * Registers the default {@link FluxCacheManager} unless the user provides one.
 *
 * @author : wh
 */
@Configuration(proxyBeanMethods = false)
public class FluxCacheManagerAutoConfiguration {

    /**
     * static bean method so the BeanPostProcessor is registered without instantiating
     * this configuration class first (avoids early-instantiation warnings and ordering issues).
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    @ConditionalOnMissingBean(FluxCacheManager.class)
    public static DefaultFluxCacheManager defaultFluxCacheManager(
            ObjectProvider<CacheSyncStrategy> cacheSyncStrategyProvider,
            FluxCacheProperties cacheProperties,
            FluxCacheOperationSource fluxCacheOperationSource,
            FluxCacheMonitor fluxCacheMonitor,
            FluxCacheFactory fluxCacheFactory) {
        return new DefaultFluxCacheManager(cacheSyncStrategyProvider, cacheProperties,
                fluxCacheOperationSource, fluxCacheMonitor, fluxCacheFactory);
    }
}
