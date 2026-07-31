package com.fluxcache.core.monitor;

import lombok.Builder;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/10/6 13:55
 * @description:
 */
@Data
@Builder
public class FluxCacheMonitorEvent {

    private String cacheName;

    private MonitorEventEnum monitorEventEnum;

    /**
     * 仅对需要真实加载的场景赋值（PUT 场景），单位毫秒
     */
    private long loadTime;

    /**
     * 事件次数，默认为 1，允许批量上报
     */
    private long count = 1;

    /**
     * 事件发生时间（ms）
     */
    private Long timestamp;


    private String key;

    private boolean forceRefresh;

    /**
     * count 的安全取值，默认 1。
     */
    public long getCount() {
        return count;
    }

    /**
     * loadTime 的安全取值，默认 0。
     */
    public long getLoadTime() {
        return loadTime;
    }
}

