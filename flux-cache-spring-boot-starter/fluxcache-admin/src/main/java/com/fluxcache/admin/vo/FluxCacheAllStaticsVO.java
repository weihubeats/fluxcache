package com.fluxcache.admin.vo;

import com.fluxcache.core.monitor.FluxCacheInfo;
import com.fluxcache.core.monitor.FluxCacheStatics;
import lombok.Data;

import java.util.List;

/**
 * @author : wh
 * @date : 2024/9/1 18:37
 * @description:
 */
@Data
public class FluxCacheAllStaticsVO {


    private Long startTime;

    private String cacheName;


    /**
     * 窗口明细（从旧到新）
     */
    private List<FluxCacheStaticsVO> windows;

    private long totalHit;

    private long totalMiss;

    private long totalPut;

    private long totalEvict;

    private long totalRequest;

    // 所有窗口的最大加载耗时
    private long maxLoadTimeOverall;

    // 命中率（0~1）
    private double overallHitRate;

    public FluxCacheAllStaticsVO(String cacheName, FluxCacheStatics statics) {
        FluxCacheStatsAssembler.fill(this, cacheName, statics, Integer.MAX_VALUE);
    }

    /**
     * 支持只取最近 N 个窗口（例如最近 24 个窗口=最近 12 小时）
     */
    public FluxCacheAllStaticsVO(String cacheName, FluxCacheStatics statics, int lastNWindows) {
        FluxCacheStatsAssembler.fill(this, cacheName, statics, lastNWindows);
    }

    /**
     * 也支持从外部传入已截取的窗口，按需组装
     */
    public FluxCacheAllStaticsVO(String cacheName, long startTime, List<FluxCacheInfo> buckets) {
        FluxCacheStatsAssembler.fill(this, cacheName, startTime, buckets);
    }
}
