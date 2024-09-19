package com.fluxcache.core.enums;

/**
 * @author : wh
 * @date : 2024/9/19 21:50
 * @description:
 */
public enum CacheOrder {

    Caffeine(0),

    REDIS(1),;

    final int ORDERED;

    CacheOrder(int order) {
        this.ORDERED = order;
    }
}
