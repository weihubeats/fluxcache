package com.fluxcache.redis.redisson.lock;

import com.fluxcache.core.lock.FluxDistributedLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Distributed lock backed by Redisson {@link RLock}.
 */
@RequiredArgsConstructor
public class RedissonDistributedLock implements FluxDistributedLock {

    private final RedissonClient redissonClient;
    private final Map<String, RLock> heldLocks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, long waitSeconds, long leaseSeconds) {
        RLock lock = redissonClient.getLock(key);
        try {
            // leaseTime <= 0 would silently enable the Redisson watchdog (lock held until process death);
            // clamp to >= 1s for parity with the spring-data-redis implementation
            boolean locked = lock.tryLock(waitSeconds, Math.max(leaseSeconds, 1), TimeUnit.SECONDS);
            if (locked) {
                heldLocks.put(key, lock);
            }
            return locked;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        RLock lock = heldLocks.remove(key);
        if (lock == null) {
            lock = redissonClient.getLock(key);
        }
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
