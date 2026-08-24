package com.fluxcache.core.monitor;

import com.fluxcache.core.properties.FluxCacheProperties.HotKeyConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 热 Key 探测器：滑动窗口判定、连续确认消抖、失败恢复、冷却节流、容量 LRU。
 *
 * @author : wh
 */
public class FluxHotKeyDetectorTest {

    /** 窗口 20s、分片 10s -> 2 槽 */
    private static final int WINDOW_SECONDS = 20;
    private static final int SLOT_SECONDS = 10;

    private final AtomicLong time = new AtomicLong(1_000_000_000L);

    private HotKeyConfig config;
    private FluxHotKeyDetector detector;
    private final List<FluxHotKeySnapshot> detected = new ArrayList<>();
    private final List<FluxHotKeySnapshot> recovered = new ArrayList<>();

    @Before
    public void setUp() {
        config = new HotKeyConfig();
        config.setEnabled(true);
        config.setWindowSeconds(WINDOW_SECONDS);
        config.setSlotSeconds(SLOT_SECONDS);
        config.setHotQpsThreshold(2.0);
        config.setHotMissThreshold(5L);
        config.setConfirmTicks(2);
        config.setNotifyIntervalMs(30_000L);
        config.setMaxHotKeyCapacity(1_000L);

        detector = new FluxHotKeyDetector(config, time::get);
        detector.addFluxHotKeyListener(new FluxHotKeyListener() {
            @Override
            public void onHotKeyDetected(FluxHotKeySnapshot snapshot) {
                detected.add(snapshot);
            }

            @Override
            public void onHotKeyRecovered(FluxHotKeySnapshot snapshot) {
                recovered.add(snapshot);
            }
        });
    }

    private void advance(long millis) {
        time.addAndGet(millis);
    }

    /** 在下一个分片推进后仍维持热度 60 命中 + 30 未命中（窗口 3 QPS / miss30） */
    private void keepHot() {
        detector.record("cache", "hotkey", false, 60);
        detector.record("cache", "hotkey", true, 30);
    }

    @Test
    public void detect_afterConsecutiveConfirm_notifiesOnce() {
        keepHot(); // 第一个分片：连续热=1，未确认
        assertTrue(detected.isEmpty());
        assertFalse(detector.isHotKey("cache", "hotkey"));

        advance(SLOT_SECONDS * 1000L + 1L); // 跨入第二个分片
        keepHot();
        assertEquals(1, detected.size());
        assertTrue(detector.isHotKey("cache", "hotkey"));
        FluxHotKeySnapshot snapshot = detected.get(0);
        assertEquals("cache", snapshot.getCacheName());
        assertEquals("hotkey", snapshot.getKey());
        assertTrue(snapshot.getQps() >= 2.0);
        assertTrue(snapshot.getMissCount() >= 5);
    }

    @Test
    public void recover_whenHeatSubsides() {
        keepHot();
        advance(SLOT_SECONDS * 1000L + 1L);
        keepHot();
        assertEquals(1, detected.size());
        assertTrue(detector.isHotKey("cache", "hotkey"));

        // 热度消退，窗口内只剩低频访问
        advance(2L * WINDOW_SECONDS * 1000L);
        detector.record("cache", "hotkey", false, 1);

        assertEquals(1, detected.size());
        assertEquals(1, recovered.size());
        assertFalse(detector.isHotKey("cache", "hotkey"));
        assertTrue(detector.getHotKeysSnapshot().isEmpty());
    }

    @Test
    public void coldStartSpike_notReported() {
        keepHot(); // 仅一个分片内高频，未形成连续热
        assertEquals(0, detected.size());
        assertEquals(0, recovered.size());
        assertFalse(detector.isHotKey("cache", "hotkey"));
    }

    @Test
    public void notify_throttledByInterval() {
        // 冷却间隔缩到 25s，便于在固定分片节奏下验证节流
        config.setNotifyIntervalMs(25_000L);
        keepHot();
        advance(SLOT_SECONDS * 1000L + 1L);
        keepHot(); // 第 1 次通知（最近一次通知时间 ≈ 10s）
        assertEquals(1, detected.size());

        // 冷却期内（距上次通知 < 25s）仍热，不重复通知
        advance(SLOT_SECONDS * 1000L + 1L);
        keepHot();
        assertEquals(1, detected.size());

        // 逐分片推进，跨过冷却期（距上次通知 ≥ 25s）后再次通知
        advance(SLOT_SECONDS * 1000L + 1L);
        keepHot();
        assertEquals(1, detected.size());

        advance(SLOT_SECONDS * 1000L + 1L);
        keepHot();
        assertEquals(2, detected.size());
    }

    @Test
    public void disabled_noOp() {
        config.setEnabled(false);
        detector.record("cache", "hotkey", false, 60);
        detector.record("cache", "hotkey", true, 30);
        assertTrue(detected.isEmpty());
        assertTrue(recovered.isEmpty());
        assertTrue(detector.getHotKeysSnapshot().isEmpty());
        assertFalse(detector.isHotKey("cache", "hotkey"));
    }

    @Test
    public void invalidInput_ignored() {
        detector.record(null, "k", false, 1);
        detector.record("cache", null, false, 1);
        detector.record("cache", "k", false, 0);
        detector.record("cache", "k", false, -1);
        assertTrue(detected.isEmpty());
    }

    @Test
    public void snapshot_listsHotOnly() {
        keepHot();
        advance(SLOT_SECONDS * 1000L + 1L);
        keepHot();
        detector.record("other", "cold", false, 1);

        List<FluxHotKeySnapshot> snapshots = detector.getHotKeysSnapshot();
        assertEquals(1, snapshots.size());
        assertEquals("hotkey", snapshots.get(0).getKey());
        assertTrue(snapshots.get(0).getLastActiveTime() > 0L);
        assertTrue(snapshots.get(0).isHot());
    }

    @Test
    public void capacityExceeded_evictsOldestFifo() {
        HotKeyConfig tiny = new HotKeyConfig();
        tiny.setEnabled(true);
        tiny.setWindowSeconds(WINDOW_SECONDS);
        tiny.setSlotSeconds(SLOT_SECONDS);
        tiny.setHotQpsThreshold(Double.MAX_VALUE);
        tiny.setHotMissThreshold(Long.MAX_VALUE);
        tiny.setMaxHotKeyCapacity(3L);
        FluxHotKeyDetector capDetector = new FluxHotKeyDetector(tiny, time::get);

        for (int i = 0; i < 10; i++) {
            capDetector.record("cache", "k" + i, false, 100);
        }
        capDetector.cleanUp();
        // 容量上限收敛到 3
        assertEquals(3, capDetector.getTrackedCount());
        // FIFO：最早创建的 key 被淘汰，最近写入保留
        assertFalse(capDetector.isTracked("cache", "k0"));
        assertTrue(capDetector.isTracked("cache", "k7"));
        assertTrue(capDetector.isTracked("cache", "k9"));
    }

    @Test
    public void multipleHotKeys_isolated() {
        keepHot();
        advance(SLOT_SECONDS * 1000L + 1L);
        keepHot(); // hotkey 已热

        detector.record("other", "k2", false, 60);
        detector.record("other", "k2", true, 30);
        advance(SLOT_SECONDS * 1000L + 1L);
        detector.record("other", "k2", false, 60);

        assertTrue(detector.isHotKey("cache", "hotkey"));
        assertTrue(detector.isHotKey("other", "k2"));
        assertEquals(2, detector.getHotKeysSnapshot().size());
    }

    @Test
    public void nullConfig_detectorDisabled() {
        FluxHotKeyDetector nullCfg = new FluxHotKeyDetector(null, time::get);
        assertFalse(nullCfg.isEnabled());
        nullCfg.record("cache", "k", false, 60);
        assertTrue(nullCfg.getHotKeysSnapshot().isEmpty());
        assertFalse(nullCfg.isHotKey("cache", "k"));
        assertFalse(nullCfg.isTracked("cache", "k"));
        assertEquals(0, nullCfg.getTrackedCount());
    }

    @Test
    public void nullQueryInputs_returnFalse() {
        assertFalse(detector.isHotKey(null, "k"));
        assertFalse(detector.isHotKey("cache", null));
        assertFalse(detector.isTracked(null, "k"));
        assertFalse(detector.isTracked("cache", null));
    }

    @Test
    public void cleanUp_onEmptyTable_noOp() {
        detector.cleanUp();
        assertEquals(0, detector.getTrackedCount());
        assertTrue(detector.getHotKeysSnapshot().isEmpty());
    }

    @Test
    public void addNullListener_ignored() {
        detector.addFluxHotKeyListener(null);
        keepHot();
        advance(SLOT_SECONDS * 1000L + 1L);
        keepHot();
        assertFalse(detected.isEmpty());
    }

    @Test
    public void throwingListener_doesNotBreakDetection() {
        detector.addFluxHotKeyListener(new FluxHotKeyListener() {
            @Override
            public void onHotKeyDetected(FluxHotKeySnapshot snapshot) {
                throw new RuntimeException("boom");
            }

            @Override
            public void onHotKeyRecovered(FluxHotKeySnapshot snapshot) {
                throw new RuntimeException("boom");
            }
        });
        keepHot();
        advance(SLOT_SECONDS * 1000L + 1L);
        keepHot();
        advance(2L * WINDOW_SECONDS * 1000L);
        detector.record("cache", "hotkey", false, 1);
        assertTrue(true);
    }

    @Test
    public void concurrentFirstRecord_createsSingleEntry() throws Exception {
        int n = 8;
        CyclicBarrier barrier = new CyclicBarrier(n);
        CountDownLatch done = new CountDownLatch(n);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < n; i++) {
            Thread thread = new Thread(() -> {
                try {
                    barrier.await(2, TimeUnit.SECONDS);
                    detector.record("cache", "k", false, 1);
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
        // 并发首次写入：同一 key 只建一个统计项，且计数完整
        assertEquals(1, detector.getTrackedCount());
        List<FluxHotKeySnapshot> snapshots = detector.getHotKeysSnapshot();
        assertTrue(snapshots.isEmpty());
    }
}