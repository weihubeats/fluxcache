package com.fluxcache.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * @author : wh
 * @date : 2024/9/11 22:38
 * @description:
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface FluxCacheEvict {

    @AliasFor("cacheName")
    String value() default "";

    @AliasFor("value")
    String cacheName() default "";


    String key() default "";
}
