package com.fluxcache.core.lock;

/**
 * Distributed lock used by cache preheat / refresh.
 *
 * @author : wh
 * @date : 2026/7/30
 */
public interface FluxDistributedLock {

    /**
     * Try to acquire a lock.
     *
     * @param key          lock key
     * @param waitSeconds  max wait time in seconds
     * @param leaseSeconds lease time in seconds
     * @return true if acquired
     */
    boolean tryLock(String key, long waitSeconds, long leaseSeconds);

    /**
     * Release the lock if held by the current thread / token.
     */
    void unlock(String key);
}
