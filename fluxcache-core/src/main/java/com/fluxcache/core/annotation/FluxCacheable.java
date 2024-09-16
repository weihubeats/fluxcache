package com.fluxcache.core.annotation;

import com.fluxcache.core.enums.FluxCacheLevel;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * @author : wh
 * @date : 2024/9/1 18:39
 * @description:
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface FluxCacheable {

    @AliasFor("cacheName")
    String value() default "";


    @AliasFor("value")
    String cacheName() default "";

    String key() default "";

    FirstCacheable firstCacheable() default @FirstCacheable();


    SecondaryCacheable secondaryCacheable() default @SecondaryCacheable();

    /**
     * 缓存级别 一级缓存 二级缓存
     * @return
     */
    FluxCacheLevel fluxCacheLevel() default FluxCacheLevel.NULL;

}
