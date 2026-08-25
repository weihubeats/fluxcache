package com.fluxcache.core.monitor;

import com.fluxcache.core.properties.FluxCacheProperties.HotKeyConfig;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * 热 Key 探测器防御性边界：禁用/非法入参直接短路，不产生副作用。
 *
 * @author : wh
 */
public class FluxHotKeyDetectorEdgeTest {

    private HotKeyConfig config() {
        HotKeyConfig config = new HotKeyConfig();
        config.setEnabled(true);
        return config;
    }

    @Test
    public void disabled_shortCircuits() {
        HotKeyConfig disabled = new HotKeyConfig();
        disabled.setEnabled(false);
        FluxHotKeyDetector detector = new FluxHotKeyDetector(disabled);

        detector.record("c", "k", false, 1L);

        assertFalse(detector.isEnabled());
        org.junit.Assert.assertEquals(0, detector.getTrackedCount());
        assertFalse(detector.isTracked("c", "k"));
    }

    @Test
    public void invalidRecordArgs_ignored() {
        FluxHotKeyDetector detector = new FluxHotKeyDetector(config());

        detector.record(null, "k", false, 1L);
        detector.record("c", null, false, 1L);
        detector.record("c", "k", false, 0L);
        detector.record("c", "k", true, -5L);

        org.junit.Assert.assertEquals(0, detector.getTrackedCount());
    }

    @Test
    public void lookups_withNullArgs_false() {
        FluxHotKeyDetector detector = new FluxHotKeyDetector(config());

        assertFalse(detector.isTracked(null, "k"));
        assertFalse(detector.isTracked("c", null));
        assertFalse(detector.isHotKey(null, "k"));
        assertFalse(detector.isHotKey("c", null));
    }

    @Test
    public void cleanUp_emptyTable_noop() {
        FluxHotKeyDetector detector = new FluxHotKeyDetector(config());
        detector.cleanUp();
        org.junit.Assert.assertEquals(0, detector.getTrackedCount());
    }

    @Test
    public void nullConfig_defaultsStillWork() {
        FluxHotKeyDetector detector = new FluxHotKeyDetector((HotKeyConfig) null);

        assertFalse(detector.isEnabled());
        assertFalse(detector.isTracked("c", "k"));
    }

    @Test
    public void nonPositiveWindowConfig_fallsBackToDefaults() {
        HotKeyConfig bad = new HotKeyConfig();
        bad.setEnabled(true);
        bad.setWindowSeconds(0);
        bad.setSlotSeconds(-3);

        // 不抛异常，使用默认窗口参数
        FluxHotKeyDetector detector = new FluxHotKeyDetector(bad);
        detector.record("c", "k", true, 1L);

        org.junit.Assert.assertEquals(1, detector.getTrackedCount());
    }

    @Test
    public void capacityOverflow_fifoEvictsOldest() {
        HotKeyConfig tiny = config();
        tiny.setMaxHotKeyCapacity(1L);
        FluxHotKeyDetector detector = new FluxHotKeyDetector(tiny);

        detector.record("c", "first", false, 1L);
        detector.record("c", "second", false, 1L);
        detector.cleanUp();

        org.junit.Assert.assertEquals(1, detector.getTrackedCount());
        assertFalse(detector.isTracked("c", "first"));
        org.junit.Assert.assertTrue(detector.isTracked("c", "second"));
    }
}
