package com.fluxcache.redis.redisson.lock;

import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RedissonDistributedLockTest {

    private RedissonClient redissonClient;
    private RLock lock;
    private RedissonDistributedLock distributedLock;

    @Before
    public void setUp() {
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        try {
            when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        distributedLock = new RedissonDistributedLock(redissonClient);
    }

    @Test
    public void tryLock_success() {
        try {
            when(lock.tryLock(eq(10L), eq(30L), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean result = distributedLock.tryLock("test-key", 10, 30);
        assertTrue(result);
        try {
            verify(lock).tryLock(eq(10L), eq(30L), eq(TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void tryLock_failure() {
        try {
            when(lock.tryLock(eq(10L), eq(30L), eq(TimeUnit.SECONDS))).thenReturn(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean result = distributedLock.tryLock("test-key", 10, 30);
        assertFalse(result);
        try {
            verify(lock).tryLock(eq(10L), eq(30L), eq(TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void tryLock_interrupted() {
        try {
            when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenThrow(new InterruptedException());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean result = distributedLock.tryLock("test-key", 10, 30);
        assertFalse(result);
    }

    @Test
    public void unlock_releasesHeldLock() {
        try {
            when(lock.tryLock(eq(10L), eq(30L), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        distributedLock.tryLock("test-key", 10, 30);

        when(lock.isHeldByCurrentThread()).thenReturn(true);
        distributedLock.unlock("test-key");
        verify(lock).unlock();
    }

    @Test
    public void unlock_unknownKey_callsGetLockThenUnlock() {
        RLock freshLock = mock(RLock.class);
        when(redissonClient.getLock("nonexistent")).thenReturn(freshLock);
        when(freshLock.isHeldByCurrentThread()).thenReturn(true);

        distributedLock.unlock("nonexistent");
        verify(freshLock).unlock();
    }

    @Test
    public void unlock_notHeld_doesNotUnlock() {
        try {
            when(lock.tryLock(eq(10L), eq(30L), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        distributedLock.tryLock("test-key", 10, 30);

        when(lock.isHeldByCurrentThread()).thenReturn(false);
        distributedLock.unlock("test-key");
        verify(lock, never()).unlock();
    }
}
