package com.fluxcache.core.monitor;

import lombok.Data;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author : wh
 * @date : 2024/9/22 16:25
 * @description:
 */
@Data
public class FluxCacheStatics {

    private static final int MAX_BUCKETS = 48;

    /**
     * 30 分钟窗口（毫秒）
     */
    private static final long HALF_HOUR = 30L * 60 * 1000;

    private final long startTime;

    /**
     * 滚动窗口列表（从旧到新）
     */
    private final ConcurrentLinkedDeque<FluxCacheInfo> window = new ConcurrentLinkedDeque<>();

    /**
     * 当前窗口引用
     */
    private final AtomicReference<FluxCacheInfo> currentBucket = new AtomicReference<>();

    public FluxCacheStatics() {
        this.startTime = System.currentTimeMillis();
        // 初始化首个窗口
        FluxCacheInfo first = FluxCacheInfo.startAt(startTime, HALF_HOUR);
        window.add(first);
        currentBucket.set(first);
    }

    public boolean isEmpty() {
        return window.isEmpty();
    }

    public void init() {
        if (currentBucket.get() == null) {
            synchronized (this) {
                if (currentBucket.get() == null) {
                    FluxCacheInfo first = FluxCacheInfo.startAt(System.currentTimeMillis(), HALF_HOUR);
                    window.add(first);
                    currentBucket.set(first);
                }
            }
        }
    }

    /**
     * 命中 + 请求累计；loadTime 通常为 0
     */
    public void incrementHit(long count, long loadTime) {
        rotateIfNeeded();
        FluxCacheInfo b = currentBucket.get();
        if (b != null) {
            b.getHit().add(count);
            b.getRequestCount().add(count);
            // 命中通常没有加载耗时，这里容错处理
            b.getMaxLoadTime().accumulateAndGet(loadTime, Math::max);
        }
    }

    /**
     * 未命中 + 请求累计；loadTime 通常为 0（真实加载耗时由 PUT 事件带入）
     */
    public void incrementMissing(long count, long loadTime) {
        rotateIfNeeded();
        FluxCacheInfo b = currentBucket.get();
        if (b != null) {
            b.getFail().add(count);
            b.getRequestCount().add(count);
            b.getMaxLoadTime().accumulateAndGet(loadTime, Math::max);
        }
    }

    public void incrementEvict(long count, long loadTime) {
        rotateIfNeeded();
        FluxCacheInfo b = currentBucket.get();
        if (b != null) {
            b.getEvictCount().add(count);
        }
    }

    /**
     * 更新/写入次数；此处应更新 maxLoadTime（真实加载耗时）
     */
    public void incrementPut(long count, long loadTime) {
        rotateIfNeeded();
        FluxCacheInfo b = currentBucket.get();
        if (b != null) {
            b.getPutCount().add(count);
            b.getMaxLoadTime().accumulateAndGet(loadTime, Math::max);
        }
    }

    /**
     * 当当前时间超过窗口结束时间时，滚动到下一个窗口。
     * 支持一次跨越多个窗口（逐步补齐）。
     */
    private void rotateIfNeeded() {
        long now = System.currentTimeMillis();
        FluxCacheInfo last = currentBucket.get();
        if (Objects.isNull(last)) {
            init();
            return;
        }
        // 快路径：仍在当前窗口内
        if (now < last.getEndTime().get()) {
            return;
        }
        // 需要旋转：加锁保护窗口队列与 currentBucket 原子更新
        synchronized (this) {
            // 双检，减少竞争
            last = currentBucket.get();
            while (last != null && now >= last.getEndTime().get()) {
                // 将 last 的 end 固定为 start + HALF_HOUR（保障窗口长度稳定）
                last.getEndTime().compareAndSet(last.getEndTime().get(), last.getStartTime().get() + HALF_HOUR);
                long nextStart = last.getStartTime().get() + HALF_HOUR;
                FluxCacheInfo next = FluxCacheInfo.startAt(nextStart, HALF_HOUR);
                window.addLast(next);
                currentBucket.set(next);
                // 控制窗口数量上限
                while (window.size() > MAX_BUCKETS) {
                    window.pollFirst();
                }
                last = next;
            }
        }
    }

}
