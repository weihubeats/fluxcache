package com.fluxcache.core.annotation;

import com.fluxcache.core.enums.FluxCacheType;
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

    /**
     * 过期时间，0 表示未设置，回落到全局配置 {@code flux.cache.first-cache.ttl}
     */
    long ttl() default 0L;

    /**
     * 初始容量，-1 表示未设置，回落到全局配置
     */
    int initSize() default -1;

    /**
     * 最大容量，-1 表示未设置，回落到全局配置
     */
    int maxSize() default -1;

    TimeUnit unit() default TimeUnit.MINUTES;

    /**
     * 缓存类型，{@link com.fluxcache.core.enums.FluxCacheType#NULL} 表示未设置，回落到全局配置
     */
    FluxCacheType fluxCacheType() default FluxCacheType.NULL;

}
