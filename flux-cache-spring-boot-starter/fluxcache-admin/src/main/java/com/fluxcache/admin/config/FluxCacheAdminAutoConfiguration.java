package com.fluxcache.admin.config;

import com.fluxcache.admin.controller.FluxCacheController;
import com.fluxcache.core.FluxCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for FluxCache admin API ({@link FluxCacheController}).
 */
@Slf4j
@AutoConfiguration(after = com.fluxcache.core.config.FluxCacheManagerAutoConfiguration.class)
@ConditionalOnWebApplication
@ConditionalOnBean(FluxCacheManager.class)
@ConditionalOnProperty(prefix = "flux.cache.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FluxCacheAdminProperties.class)
@Import(FluxCacheController.class)
public class FluxCacheAdminAutoConfiguration {

    @Bean
    public FilterRegistrationBean<FluxCacheTokenFilter> fluxCacheTokenFilter(
            FluxCacheAdminProperties adminProperties,
            @Value("${flux.cache.prefix:/cache/manager/v1}") String pathPrefix) {
        if (adminProperties.isEnabled() && !StringUtils.hasText(adminProperties.getToken())) {
            log.warn("[FluxCache] admin endpoints are exposed without authentication at {}/*; "
                    + "set flux.cache.admin.token to protect evict/clear operations", pathPrefix);
        }
        FilterRegistrationBean<FluxCacheTokenFilter> registration =
                new FilterRegistrationBean<>(new FluxCacheTokenFilter(adminProperties.getToken()));
        registration.addUrlPatterns(pathPrefix.endsWith("/") ? pathPrefix + "*" : pathPrefix + "/*");
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }
}
