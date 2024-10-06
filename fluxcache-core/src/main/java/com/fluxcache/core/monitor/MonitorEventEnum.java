package com.fluxcache.core.monitor;

/**
 * @author : wh
 * @date : 2024/10/6 13:54
 * @description:
 */
public enum MonitorEventEnum {

    /**
     * 缓存命中
     */
    CACHE_HIT,

    /**
     * 缓存丢失
     */
    CACHE_MISSING,

    /**
     * 缓存过期
     */
    CACHE_EVICT,

    /**
     * 缓存更新
     */
    CACHE_PUT,

    /**
     * 缓存过期
     */
    CACHE_EXPIRE;
}
