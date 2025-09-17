package com.fluxcache.core.annotation;

import com.fluxcache.core.preheat.FluxPreheatDataProvider;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author : wh
 * @date : 2025/9/16 14:10
 * @description:
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FluxRefresh {

    /**
     * 是否开启缓存定时刷新
     * @return
     */
    boolean enabled() default false;


    /**
     * 用于获取刷新所需数据的提供者。
     * 复用 PreheatDataProvider 接口。
     */
    Class<? extends FluxPreheatDataProvider> provider() default FluxPreheatDataProvider.None.class;

    /**
     * CRON表达式，用于定义刷新周期。
     * cron 优先级高于 fixedRate/fixedDelay。
     * @return
     */
    String cron() default "";

    /**
     * 固定频率执行的间隔时间。
     * 单位由 unit() 定义。
     */
    long fixedRate() default -1L;

    /**
     * 固定延迟执行的间隔时间。
     * 单位由 unit() 定义。
     */
    long fixedDelay() default -1L;

    /**
     * 首次执行前的初始延迟。
     * 单位由 unit() 定义。
     */
    long initialDelay() default 0L;

    /**
     * 时间单位，默认为秒。
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 是否在应用启动时预热缓存
     *
     * @return
     */
    boolean preheatOnStartup() default false;


    boolean distributedLock() default true;

    /**
     * 抖动时间，单位为毫秒。
     * @return
     */
    long jitterMillis() default 0;

    /**
     * 分布式锁等待时间，单位为秒。
     * @return
     */
    long lockWaitSeconds() default 0;

    /**
     * 分布式锁租约时间，单位为秒。
     * @return
     */
    long lockLeaseSeconds() default 30;

}
