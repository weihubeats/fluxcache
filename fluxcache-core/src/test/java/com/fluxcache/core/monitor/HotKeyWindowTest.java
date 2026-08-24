package com.fluxcache.core.monitor;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 分片滑动窗口计数器：命中/未命中记录、窗口滚动、过期分片淘汰、惰性清理。
 *
 * @author : wh
 */
public class HotKeyWindowTest {

    private static final long SLOT_MS = 1000L;

    @Test
    public void record_singleSlot_countsHitAndMiss() {
        HotKeyWindow window = new HotKeyWindow(4, SLOT_MS);
        window.record(false, 3, 10_000L);
        window.record(true, 2, 10_000L);

        long[] counters = window.snapshot(10_000L);
        assertArrayEquals(new long[]{3L, 2L}, counters);
    }

    @Test
    public void snapshot_excludesSlotsOutsideWindow() {
        HotKeyWindow window = new HotKeyWindow(4, SLOT_MS);
        // 时间 0 旧事件
        window.record(false, 7, 0L);
        // 时间 9.5s（tick=9，同一槽）新事件，滚动窗口为 tick 6..9
        window.record(false, 3, 9_500L);

        long[] counters = window.snapshot(9_950L);
        // 旧事件被滚动淘汰，仅剩新事件
        assertArrayEquals(new long[]{3L, 0L}, counters);
    }

    @Test
    public void record_staleSlot_resetInPlace() {
        HotKeyWindow window = new HotKeyWindow(4, SLOT_MS);
        window.record(false, 5, 0L);
        // 同槽位在多个窗口后再次写入，应清空旧计数
        window.record(true, 9, 16_000L);

        long[] counters = window.snapshot(16_000L);
        assertArrayEquals(new long[]{0L, 9L}, counters);
    }

    @Test
    public void record_ordersInject_acrossMultipleSlots() {
        HotKeyWindow window = new HotKeyWindow(4, SLOT_MS);
        window.record(false, 1, 0L);
        window.record(true, 1, 1_500L);
        window.record(false, 2, 2_700L);
        // tick 4（4_000..4_999）落在 slot0，滚动窗口 [tick1..tick4] 排除 tick0
        long[] counters = window.snapshot(4_999L);
        assertArrayEquals(new long[]{2L, 1L}, counters);
    }

    @Test
    public void counter_negativeOrZeroParams_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new HotKeyWindow(0, SLOT_MS));
        assertThrows(IllegalArgumentException.class, () -> new HotKeyWindow(4, 0L));
    }

    @Test
    public void currentTick_alignedToSlot() {
        HotKeyWindow window = new HotKeyWindow(4, SLOT_MS);
        assertTrue(window.currentTick(9_999L) == 9L);
        assertTrue(window.currentTick(10_000L) == 10L);
    }

    @Test
    public void concurrentStaleSlotReset_countsNotLost() throws Exception {
        HotKeyWindow window = new HotKeyWindow(4, SLOT_MS);
        window.record(false, 5, 0L); // 使 slot0 处于过期 tick

        int n = 8;
        CyclicBarrier barrier = new CyclicBarrier(n);
        CountDownLatch done = new CountDownLatch(n);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < n; i++) {
            Thread thread = new Thread(() -> {
                try {
                    barrier.await(2, TimeUnit.SECONDS);
                    window.record(true, 1, 16_000L);
                } catch (Throwable t) {
                    failure.set(t);
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }
        done.await(5, TimeUnit.SECONDS);

        assertNull(failure.get());
        // 仅一次重置（CAS 竞争下其余线程追加而非丢失），计数不丢
        long[] counters = window.snapshot(16_000L);
        assertArrayEquals(new long[]{0L, n}, counters);
    }
}