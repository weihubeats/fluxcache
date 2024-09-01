package com.fluxcache.core.annotation;

import java.util.Objects;

/**
 * @author : wh
 * @date : 2024/9/1 18:39
 * @description:
 */
public enum FluxCacheLevel {

    NULL,

    FirstCacheable,

    SecondaryCacheable;

    /**
     * 是否为二级缓存
     *
     * @param fluxCacheLevel
     * @return
     */
    public static boolean isSecondaryCacheable(FluxCacheLevel fluxCacheLevel) {
        return Objects.equals(fluxCacheLevel, FluxCacheLevel.SecondaryCacheable);
    }

}
