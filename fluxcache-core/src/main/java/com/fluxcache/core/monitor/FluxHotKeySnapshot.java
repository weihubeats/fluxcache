package com.fluxcache.core.monitor;

import lombok.Builder;
import lombok.Data;

/**
 * 热 Key 快照信息。
 *
 * @author : wh
 */
@Data
@Builder
public class FluxHotKeySnapshot {

    /**
     * 缓存名
     */
    private String cacheName;

    /**
     * Key
     */
    private String key;

    /**
     * 窗口内命中次数
     */
    private long hitCount;

    /**
     * 窗口内未命中次数
     */
    private long missCount;

    /**
     * 窗口内读 QPS（命中+未命中）
     */
    private double qps;

    /**
     * 窗口内命中率
     */
    private double hitRate;

    /**
     * 当前是否处于热状态
     */
    private boolean hot;

    /**
     * 进入热状态的时间戳（ms），恢复后清零
     */
    private long hotSince;

    /**
     * 最近一次活跃时间戳（ms），用于 Dashboard 排序排查
     */
    private long lastActiveTime;
}