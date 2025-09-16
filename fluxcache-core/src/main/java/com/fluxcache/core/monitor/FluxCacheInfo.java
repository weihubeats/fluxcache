package com.fluxcache.core.monitor;

import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

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
    private final LongAdder hit = new LongAdder();

    /**
     * 未命中次数
     */
    private final LongAdder fail = new LongAdder();

    /**
     * 删除次数
     */
    private final LongAdder evictCount = new LongAdder();

    /**
     * 更新次数
     */
    private final LongAdder putCount = new LongAdder();

    /**
     * 请求次数（命中 + 未命中）
     */
    private final LongAdder requestCount = new LongAdder();

    /**
     * 当前窗口内的最大加载耗时（毫秒）
     */
    private final AtomicLong maxLoadTime = new AtomicLong(0L);

    /**
     * 窗口开始时间（毫秒时间戳）
     */
    private final AtomicLong startTime = new AtomicLong(0L);

    /**
     * 窗口结束时间（毫秒时间戳）
     */
    private final AtomicLong endTime = new AtomicLong(0L);

    public static FluxCacheInfo startAt(long startMillis, long windowMillis) {
        FluxCacheInfo info = new FluxCacheInfo();
        info.getStartTime().set(startMillis);
        info.getEndTime().set(startMillis + windowMillis);
        return info;
    }
}
