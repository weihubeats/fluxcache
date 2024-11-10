package com.fluxcache.core.monitor;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/11/10 15:42
 * @description:
 */
@Data
public class FluxCacheInfo {

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
     * 更新次数
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
     * 开始时间
     */
    private AtomicLong startTime;

    /**
     * 结束时间
     */
    private AtomicLong endTime;

    public FluxCacheInfo() {
        this.hit = new LongAdder();
        this.fail = new LongAdder();
        this.evictCount = new LongAdder();
        this.putCount = new LongAdder();
        this.requestCount = new LongAdder();
        this.maxLoadTime = new AtomicLong(Long.MIN_VALUE);
    }
}
