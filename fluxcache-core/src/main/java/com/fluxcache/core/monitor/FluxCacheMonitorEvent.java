package com.fluxcache.core.monitor;

import lombok.Builder;
import lombok.Data;

import java.util.Objects;

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
     * 仅对需要真实加载的场景赋值（PUT 场景），单位毫秒 毫秒
     */
    private Long loadTime;

    /**
     * 事件次数，一般为 1，允许批量上报
     */
    private Long count;

    /**
     * 事件发生时间（ms）
     */
    private Long timestamp;


    private String key;

    private boolean forceRefresh;

    public Long count() {
        return Objects.isNull(count) ? 1L : count;
    }

    public Long loadTime() {
        return Objects.isNull(loadTime) ? 0L : loadTime;
    }


}
