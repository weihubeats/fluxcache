package com.fluxcache.admin.vo;

import com.fluxcache.core.monitor.FluxCacheInfo;
import com.fluxcache.core.monitor.FluxCacheStatics;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/9/1 18:37
 * @description:
 */
@Data
public class FluxCacheAllStaticsVO {

    private LinkedList<FluxCacheStaticsVO> fluxCacheStaticsVOS;

    private Long startTime;

    private String cacheName;

    public FluxCacheAllStaticsVO(String cacheName, FluxCacheStatics fluxCacheStatics) {
        this.startTime = fluxCacheStatics.getStartTime();
        this.cacheName = cacheName;
        this.fluxCacheStaticsVOS = getFluxCacheStaticsVOS(fluxCacheStatics);
    }

    private LinkedList<FluxCacheStaticsVO> getFluxCacheStaticsVOS(FluxCacheStatics fluxCacheStatics) {
        LinkedList<FluxCacheStaticsVO> list = new LinkedList<>();
        fluxCacheStatics.getFluxCacheInfos().forEach(fluxCacheInfo -> {
            FluxCacheStaticsVO vo = new FluxCacheStaticsVO();
            vo.setEvictCount(fluxCacheInfo.getEvictCount());
            vo.setHit(fluxCacheInfo.getHit());
            vo.setFail(fluxCacheInfo.getFail());
            vo.setPutCount(fluxCacheInfo.getPutCount());
            vo.setMaxLoadTime(fluxCacheInfo.getMaxLoadTime().get() == Long.MIN_VALUE ? new AtomicLong(0) : fluxCacheInfo.getMaxLoadTime());
            vo.setRequestCount(fluxCacheInfo.getRequestCount());
            vo.setHitRate(getHitRate(fluxCacheInfo));
            vo.setStartTime(fluxCacheInfo.getStartTime().get());
            vo.setEndTime(Objects.nonNull(fluxCacheInfo.getEndTime()) ? fluxCacheInfo.getEndTime().get() : System.currentTimeMillis());
            list.add(vo);
        });
        return list;
    }

    private Double getHitRate(FluxCacheInfo info) {
        if (info.getHit().longValue() > 0) {
            return info.getHit().doubleValue() / info.getRequestCount().doubleValue();
        } else {
            return 0.0;
        }
    }
}
