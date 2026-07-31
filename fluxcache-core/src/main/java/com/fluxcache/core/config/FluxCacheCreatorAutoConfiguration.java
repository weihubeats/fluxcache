package com.fluxcache.core.config;

import com.fluxcache.core.impl.FluxCacheFactory;
import com.fluxcache.core.impl.creator.CaffeineFluxCacheCreator;
import com.fluxcache.core.spi.FluxCacheCreator;
import com.fluxcache.core.spi.FluxCacheCreatorRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Registers built-in {@link FluxCacheCreator} plugins and the creator registry.
 * Redis creators come from optional modules ({@code fluxcache-redis-spring} / {@code fluxcache-redis-redisson}).
 *
 * @author : wh
 * @date : 2026/7/29
 */
@Configuration(proxyBeanMethods = false)
public class FluxCacheCreatorAutoConfiguration {

    public static final int BUILTIN_CREATOR_ORDER = 0;

    @Bean
    @Order(BUILTIN_CREATOR_ORDER)
    @ConditionalOnMissingBean(name = "caffeineFluxCacheCreator")
    public FluxCacheCreator caffeineFluxCacheCreator() {
        return new CaffeineFluxCacheCreator();
    }

    @Bean
    @ConditionalOnMissingBean
    public FluxCacheCreatorRegistry fluxCacheCreatorRegistry(ObjectProvider<FluxCacheCreator> creators) {
        return new FluxCacheCreatorRegistry(creators);
    }

    @Bean
    @ConditionalOnMissingBean
    public FluxCacheFactory fluxCacheFactory(FluxCacheCreatorRegistry registry) {
        return new FluxCacheFactory(registry);
    }
}
