package com.fluxcache.core.enums;

/**
 * Cache backend type. Built-in creators cover these values;
 * to add a new backend, extend this enum and register a {@link com.fluxcache.core.spi.FluxCacheCreator}.
 * To replace an existing backend, register another creator for the same type (last wins).
 *
 * @author : wh
 * @date : 2024/11/13 22:03
 * @description:
 */
public enum FluxCacheType {

    CAFFEINE,
    REDIS_R_MAP,
    REDIS_BUCKET,
    ;
}
