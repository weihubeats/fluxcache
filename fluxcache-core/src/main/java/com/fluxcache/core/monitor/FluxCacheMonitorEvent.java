package com.fluxcache.core.monitor;

import lombok.Data;

/**
 * @author : wh
 * @date : 2024/10/6 13:55
 * @description:
 */
@Data
public class FluxCacheMonitorEvent {

    private String cacheName;

    private MonitorEventEnum monitorEventEnum;

    private Long loadTime;

    private Long count;
}
