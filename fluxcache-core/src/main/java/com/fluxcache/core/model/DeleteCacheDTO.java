package com.fluxcache.core.model;

import java.util.List;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/10/20 20:46
 * @description:
 */
@Data
public class DeleteCacheDTO extends AbstractLocalCacheDTO{

    public static final  String CACHE_EVICT_TOPIC_PREFIX = "CACHE_DELETE_TOPIC";

    /**
     * cache name
     */
    private List<Object> keys;

    /**
     * 是否删除所有缓存
     */
    private boolean all = false;



    public DeleteCacheDTO(String cacheName, List<Object> keys) {
        super(cacheName);
        this.keys = keys;
        this.all = false;
    }

    public DeleteCacheDTO(String cacheName) {
        super(cacheName);
        this.all = true;
    }

    @Override
    protected String getCacheRedisTopic() {
        return CACHE_EVICT_TOPIC_PREFIX;
    }
}
