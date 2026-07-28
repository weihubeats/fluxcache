package com.fluxcache.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Admin / Dashboard related properties under {@code flux.cache.admin}.
 */
@Data
@ConfigurationProperties(prefix = "flux.cache.admin")
public class FluxCacheAdminProperties {

    /**
     * CORS settings for standalone dashboard access.
     */
    private Cors cors = new Cors();

    @Data
    public static class Cors {

        /**
         * Enable CORS. Default false for safety; enable for standalone SPA.
         */
        private boolean enabled = false;

        /**
         * Allowed origins, e.g. http://localhost:5173
         */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * Allowed origin patterns (Spring Boot 2.4+), e.g. http://localhost:*
         */
        private List<String> allowedOriginPatterns = new ArrayList<>();

        private List<String> allowedMethods = new ArrayList<>(Arrays.asList("GET", "POST", "OPTIONS"));

        private List<String> allowedHeaders = new ArrayList<>(Arrays.asList("*"));

        private boolean allowCredentials = true;

        private long maxAge = 3600L;
    }
}
