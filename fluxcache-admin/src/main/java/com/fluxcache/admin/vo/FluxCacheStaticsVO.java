package com.fluxcache.admin.vo;

import lombok.Data;

/**
 * @author : wh
 * @date : 2024/9/1 18:36
 * @description:
 */
@Data
public class FluxCacheStaticsVO {

    private long startTime;

    private long endTime;

    private long hit;

    private long miss;

    private long putCount;

    private long evictCount;

    private long requestCount;

    /**
     *  ms，窗口内最大加载耗时
     */
    private long maxLoadTime;
    
    private double hitRate;
}
