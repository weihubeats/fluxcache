package com.fluxcache.core.monitor;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 定长分片滑动窗口计数器（单个 key 维度）。
 *
 * <p>将时间划分为 slotCount 个等长分片（Slot），请求按当前 tick 映射到对应分片；
 * 分片沿用经典 LeapArray 延迟清理思路：仅在落到该分片时惰性重置过期计数，
 * 因此读路径无锁、无阻塞、无对象分配（每个事件仅一次 {@link LongAdder#add}）。</p>
 *
 * <p>快照时仅累加落在最近 slotCount 个 tick 内的分片计数，天然得到精确滚动窗口。</p>
 *
 * @author : wh
 */
public final class HotKeyWindow {

    private final int slotCount;

    private final long slotLengthMillis;

    private final Slot[] slots;

    public HotKeyWindow(int slotCount, long slotLengthMillis) {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
        if (slotLengthMillis <= 0) {
            throw new IllegalArgumentException("slotLengthMillis must be positive");
        }
        this.slotCount = slotCount;
        this.slotLengthMillis = slotLengthMillis;
        this.slots = new Slot[slotCount];
        for (int i = 0; i < slotCount; i++) {
            slots[i] = new Slot();
        }
    }

    public int getSlotCount() {
        return slotCount;
    }

    public long getSlotLengthMillis() {
        return slotLengthMillis;
    }

    /**
     * 记录一次请求。
     *
     * @param miss  是否未命中（true 计入未命中，false 计入命中）
     * @param count 事件次数，通常为 1
     * @param now   当前毫秒时间
     */
    public void record(boolean miss, long count, long now) {
        long tick = now / slotLengthMillis;
        int idx = (int) (tick % slotCount);
        Slot slot = slots[idx];
        long start = slot.startTick.get();
        if (start != tick) {
            // 该分片还停留在过期 tick（距上次写入已超过一个完整窗口），清理后复用
            if (slot.startTick.compareAndSet(start, tick)) {
                slot.hit.reset();
                slot.miss.reset();
            }
        }
        if (miss) {
            slot.miss.add(count);
        } else {
            slot.hit.add(count);
        }
    }

    /**
     * 当前滚动窗口内命中/未命中计数快照。
     *
     * <p>只累加 startTick 落在 {@code [tick - slotCount + 1, tick]} 的分片，
     * 过期分片的计数自然被排除（其 startTick 更旧）。</p>
     *
     * @param now 当前毫秒时间
     * @return 长度为 2 的数组，[0]=命中，[1]=未命中
     */
    public long[] snapshot(long now) {
        long tick = now / slotLengthMillis;
        long minStart = tick - slotCount + 1;
        long hit = 0L;
        long miss = 0L;
        for (Slot slot : slots) {
            if (slot.startTick.get() >= minStart) {
                hit += slot.hit.sum();
                miss += slot.miss.sum();
            }
        }
        return new long[]{hit, miss};
    }

    /**
     * 当前请求所在 tick（按分片长度对齐）。
     *
     * @param now 当前毫秒时间
     * @return tick 序号
     */
    public long currentTick(long now) {
        return now / slotLengthMillis;
    }

    /**
     * 单个分片：惰性起始 tick + 命中/未命中计数。
     */
    private static final class Slot {

        final AtomicLong startTick = new AtomicLong(Long.MIN_VALUE);

        final LongAdder hit = new LongAdder();

        final LongAdder miss = new LongAdder();
    }
}