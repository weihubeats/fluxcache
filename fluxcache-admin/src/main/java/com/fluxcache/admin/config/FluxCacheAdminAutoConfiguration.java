package com.fluxcache.admin.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for FluxCache admin dashboard support (CORS, etc.).
 */
@Configuration
@ConditionalOnWebApplication
@EnableConfigurationProperties(FluxCacheAdminProperties.class)
public class FluxCacheAdminAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "flux.cache.admin.cors", name = "enabled", havingValue = "true")
    public WebMvcConfigurer fluxCacheCorsConfigurer(FluxCacheAdminProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                FluxCacheAdminProperties.Cors cors = properties.getCors();
                var registration = registry.addMapping("/**")
                        .allowCredentials(cors.isAllowCredentials())
                        .maxAge(cors.getMaxAge());

                if (!CollectionUtils.isEmpty(cors.getAllowedOrigins())) {
                    registration.allowedOrigins(cors.getAllowedOrigins().toArray(new String[0]));
                }
                if (!CollectionUtils.isEmpty(cors.getAllowedOriginPatterns())) {
                    registration.allowedOriginPatterns(cors.getAllowedOriginPatterns().toArray(new String[0]));
                }
                if (CollectionUtils.isEmpty(cors.getAllowedOrigins())
                        && CollectionUtils.isEmpty(cors.getAllowedOriginPatterns())) {
                    // Safe default for local dashboard when enabled without explicit origins
                    registration.allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
                }
                if (!CollectionUtils.isEmpty(cors.getAllowedMethods())) {
                    registration.allowedMethods(cors.getAllowedMethods().toArray(new String[0]));
                }
                if (!CollectionUtils.isEmpty(cors.getAllowedHeaders())) {
                    registration.allowedHeaders(cors.getAllowedHeaders().toArray(new String[0]));
                }
            }
        };
    }
}
