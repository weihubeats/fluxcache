package com.fluxcache.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * @author : wh
 * @date : 2024/9/11 22:37
 * @description:
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FluxCachePut {

    @AliasFor("cacheName")
    String value() default "";

    @AliasFor("value")
    String cacheName() default "";
    
    String key() default "";

}
