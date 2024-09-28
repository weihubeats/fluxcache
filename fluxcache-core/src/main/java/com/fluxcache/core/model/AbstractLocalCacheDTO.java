package com.fluxcache.core.model;

import java.io.Serializable;

/**
 * @author : wh
 * @date : 2024/9/28 13:57
 * @description:
 */
public abstract class AbstractLocalCacheDTO implements Serializable {

    /**
     * cacheName
     */
    private String cacheName;

    /**
     * topicName
     */
    private String topicName;

    public AbstractLocalCacheDTO(String cacheName) {
        this.cacheName = cacheName;
    }

    public AbstractLocalCacheDTO() {
    }

    public static String topicName(String applicationName, String cacheRedisTopic) {
        return String.join(":", applicationName, cacheRedisTopic);
    }

    /**
     * cache listener topic
     * @param applicationName applicationName
     * @return
     */
    public String topicName(String applicationName) {
        return topicName(applicationName, getCacheRedisTopic());
    }

    protected abstract String getCacheRedisTopic();
}

