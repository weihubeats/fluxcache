package com.fluxcache.core.enums;

/**
 * Cache backend type. Built-in creators cover these values;
 * to add a new backend, extend this enum and register a {@link com.fluxcache.core.spi.FluxCacheCreator}.
 * To replace an existing backend, register another creator for the same type (last wins).
 *
 * <p>{@link #REDIS} is the portable Redis key-value backend (Spring Data Redis or Redisson Bucket).
 * {@link #REDIS_MAP} is Redisson {@code RMapCache} only (per-entry TTL).
 *
 * @author : wh
 * @date : 2024/11/13 22:03
 */
public enum FluxCacheType {

    CAFFEINE,
    REDIS,
    REDIS_MAP,
    ;
}
