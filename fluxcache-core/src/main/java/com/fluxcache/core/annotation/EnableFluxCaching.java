package com.fluxcache.core.annotation;

import com.fluxcache.core.config.FluxProxyCacheAutoConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * @author : wh
 * @date : 2024/11/13 22:12
 * @description:
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FluxProxyCacheAutoConfiguration.class)
public @interface EnableFluxCaching {
}
