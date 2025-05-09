package com.fluxcache.admin.vo;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/9/1 18:36
 * @description:
 */
@Data
public class FluxCacheStaticsVO {

    /**
     * 命中次数
     */
    private LongAdder hit;

    /**
     * 失败次数
     */
    private LongAdder fail;

    /**
     * 删除次数
     */
    private LongAdder evictCount;

    /**
     * 缓存的put次数
     */
    private LongAdder putCount;

    /**
     * 请求次数
     */
    private LongAdder requestCount;

    /**
     * 最大加载时间
     */
    private AtomicLong maxLoadTime;

    /**
     * 命中率
     */
    private Double hitRate;

    /**
     * 开始时间
     */
    private Long startTime;

    /**
     * 结束时间
     */
    private Long endTime;
}
