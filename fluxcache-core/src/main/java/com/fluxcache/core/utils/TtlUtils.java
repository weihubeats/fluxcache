package com.fluxcache.core.utils;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * @author : wh
 */
public final class TtlUtils {

    private TtlUtils() {
    }

    /**
     * Adds proportional 0~10% jitter to a TTL in every time unit, so keys written in
     * bulk do not all expire at the same instant (cache avalanche protection).
     */
    public static Duration randomizedTtl(Long ttl, TimeUnit unit) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        long ttlMillis = unit.toMillis(ttl);
        if (ttlMillis <= 0) {
            return Duration.ofMillis(ttlMillis);
        }
        long maxJitter = ttlMillis / 10;
        long extra = maxJitter > 0 ? ThreadLocalRandom.current().nextLong(maxJitter + 1) : 0L;
        return Duration.ofMillis(ttlMillis + extra);
    }
}
