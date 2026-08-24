package com.fluxcache.admin.vo;

import com.fluxcache.core.monitor.FluxHotKeySnapshot;
import lombok.Data;

/**
 * 热 Key 信息（Dashboard 展示）。
 *
 * @author : wh
 */
@Data
public class FluxHotKeyVO {

    private String cacheName;

    private String key;

    private double qps;

    private double hitRate;

    private long hitCount;

    private long missCount;

    private long hotSince;

    private long lastActiveTime;

    public FluxHotKeyVO(FluxHotKeySnapshot snapshot) {
        this.cacheName = snapshot.getCacheName();
        this.key = snapshot.getKey();
        this.qps = snapshot.getQps();
        this.hitRate = snapshot.getHitRate();
        this.hitCount = snapshot.getHitCount();
        this.missCount = snapshot.getMissCount();
        this.hotSince = snapshot.getHotSince();
        this.lastActiveTime = snapshot.getLastActiveTime();
    }
}