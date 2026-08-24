package com.fluxcache.core.utils;

import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author : wh
 */
public class TtlUtilsTest {

    @Test
    public void nullArgs_rejected() {
        try {
            TtlUtils.randomizedTtl(null, TimeUnit.SECONDS);
            fail("expected NPE");
        } catch (NullPointerException expected) {
        }
        try {
            TtlUtils.randomizedTtl(1L, null);
            fail("expected NPE");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void nonPositiveTtl_passthrough() {
        assertEquals(Duration.ofMillis(0), TtlUtils.randomizedTtl(0L, TimeUnit.SECONDS));
        assertEquals(Duration.ofMillis(-5_000L), TtlUtils.randomizedTtl(-5L, TimeUnit.SECONDS));
    }

    @Test
    public void jitter_withinProportionalBound() {
        long base = TimeUnit.MINUTES.toMillis(30);
        for (int i = 0; i < 1000; i++) {
            Duration ttl = TtlUtils.randomizedTtl(30L, TimeUnit.MINUTES);
            assertTrue(ttl.toMillis() >= base);
            // 10% jitter bound
            assertTrue(ttl.toMillis() <= base + base / 10);
        }
    }

    @Test
    public void smallTtl_stillValid() {
        Duration ttl = TtlUtils.randomizedTtl(500L, TimeUnit.MILLISECONDS);
        assertTrue(ttl.toMillis() >= 500 && ttl.toMillis() <= 550);
    }

    @Test
    public void allUnits_produceMillis() {
        long hourBase = TimeUnit.HOURS.toMillis(1);
        long hourTtl = TtlUtils.randomizedTtl(1L, TimeUnit.HOURS).toMillis();
        assertTrue(hourTtl >= hourBase && hourTtl <= hourBase + hourBase / 10);
        long dayBase = TimeUnit.DAYS.toMillis(1);
        long dayTtl = TtlUtils.randomizedTtl(1L, TimeUnit.DAYS).toMillis();
        assertTrue(dayTtl >= dayBase && dayTtl <= dayBase + dayBase / 10);
    }
}
