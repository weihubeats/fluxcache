package com.fluxcache.admin.vo;

import com.fluxcache.core.monitor.FluxCacheInfo;
import com.fluxcache.core.monitor.FluxCacheStatics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author : wh
 * @date : 2025/9/16 14:02
 * @description:
 */
public class FluxCacheStatsAssembler {

    /**
     * 从 FluxCacheStatics 生成 VO，支持截取最近 N 个窗口
     */
    public static void fill(FluxCacheAllStaticsVO out,
                            String cacheName,
                            FluxCacheStatics statics,
                            int lastNWindows) {
        if (out == null || statics == null)
            return;

        out.setCacheName(cacheName);
        out.setStartTime(statics.getStartTime());

        // 做一份窗口快照，避免遍历过程中窗口滚动导致的不一致
        List<FluxCacheInfo> snapshot = snapshot(statics);
        // 只保留最后 N 个窗口
        if (lastNWindows > 0 && snapshot.size() > lastNWindows) {
            snapshot = snapshot.subList(snapshot.size() - lastNWindows, snapshot.size());
        }
        fill(out, cacheName, statics.getStartTime(), snapshot);
    }

    public static void fill(FluxCacheAllStaticsVO out,
                            String cacheName,
                            long startTime,
                            List<FluxCacheInfo> buckets) {
        if (out == null || buckets == null)
            return;

        out.setCacheName(cacheName);
        out.setStartTime(startTime);

        List<FluxCacheStaticsVO> windows = new ArrayList<>(buckets.size());

        long totalHit = 0L;
        long totalMiss = 0L;
        long totalPut = 0L;
        long totalEvict = 0L;
        long totalReq = 0L;
        long maxLoadTimeOverall = 0L;

        long now = System.currentTimeMillis();

        for (FluxCacheInfo info : buckets) {
            long hit = info.getHit().sum();
            long miss = info.getFail().sum();
            long put = info.getPutCount().sum();
            long evict = info.getEvictCount().sum();
            long req = info.getRequestCount().sum();
            long maxLoad = info.getMaxLoadTime().get();

            long start = info.getStartTime().get();
            long end = info.getEndTime().get();
            // 展示用的 endTime 不要超过当前时间（最后一个窗口的 end 可能在未来）
            long displayEnd = Math.min(end, now);

            double hitRate = calcRate(hit, req);

            FluxCacheStaticsVO vo = new FluxCacheStaticsVO();
            vo.setStartTime(start);
            vo.setEndTime(displayEnd);
            vo.setHit(hit);
            vo.setMiss(miss);
            vo.setPutCount(put);
            vo.setEvictCount(evict);
            vo.setRequestCount(req);
            vo.setMaxLoadTime(Math.max(0L, maxLoad));
            vo.setHitRate(hitRate);

            windows.add(vo);

            totalHit += hit;
            totalMiss += miss;
            totalPut += put;
            totalEvict += evict;
            totalReq += req;
            maxLoadTimeOverall = Math.max(maxLoadTimeOverall, Math.max(0L, maxLoad));
        }

        out.setWindows(windows);
        out.setTotalHit(totalHit);
        out.setTotalMiss(totalMiss);
        out.setTotalPut(totalPut);
        out.setTotalEvict(totalEvict);
        out.setTotalRequest(totalReq);
        out.setMaxLoadTimeOverall(maxLoadTimeOverall);
        out.setOverallHitRate(calcRate(totalHit, totalReq));
    }

    private static double calcRate(long numerator, long denominator) {
        if (denominator <= 0)
            return 0.0;
        // 保留 4 位小数，四舍五入
        return BigDecimal.valueOf((double) numerator / (double) denominator)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 复制一个安全快照（从旧到新）
     */
    private static List<FluxCacheInfo> snapshot(FluxCacheStatics statics) {
        // ConcurrentLinkedDeque 支持弱一致迭代，这里简单拷贝一份
        List<FluxCacheInfo> list = new ArrayList<>(statics.getWindow().size());
        Iterator<FluxCacheInfo> it = statics.getWindow().iterator();
        while (it.hasNext()) {
            list.add(it.next());
        }
        return list;
    }
}
