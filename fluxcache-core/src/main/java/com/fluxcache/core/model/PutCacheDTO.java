package com.fluxcache.core.model;

import lombok.Data;

/**
 * @author : wh
 * @date : 2024/10/20 20:47
 * @description:
 */
@Data
public class PutCacheDTO extends AbstractLocalCacheDTO {

    public static final String CACHE_PUT_TOPIC_PREFIX = "CACHE_PUT_TOPIC";

    private String cacheName;

    private Object key;

    private Object cacheValue;

    @Override
    protected String getCacheRedisTopic() {
        return CACHE_PUT_TOPIC_PREFIX;
    }

    public PutCacheDTO(String cacheName, Object key, Object cacheValue) {
        this.cacheName = cacheName;
        this.key = key;
        this.cacheValue = cacheValue;
    }
}
