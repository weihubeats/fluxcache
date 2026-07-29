package com.fluxcache.admin.config;

import com.fluxcache.admin.controller.FluxCacheController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for FluxCache admin API ({@link FluxCacheController}).
 */
@AutoConfiguration
@ConditionalOnWebApplication
@Import(FluxCacheController.class)
public class FluxCacheAdminAutoConfiguration {
}
