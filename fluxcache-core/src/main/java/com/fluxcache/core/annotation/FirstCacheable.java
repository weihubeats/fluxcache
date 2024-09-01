package com.fluxcache.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author : wh
 * @date : 2024/9/1 18:41
 * @description:
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface FirstCacheable {

    long ttl() default 30L;

    int initSize() default 16;

    int maxSize() default 10000;

    TimeUnit unit() default TimeUnit.MINUTES;

    FluxCacheType fluxCacheType() default FluxCacheType.CAFFEINE;

}
