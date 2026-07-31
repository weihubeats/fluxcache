package com.fluxcache.redis.spring.lock;

import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RedisTemplateDistributedLockTest {

    private StringRedisTemplate stringRedisTemplate;
    private RedisTemplateDistributedLock lock;

    @Before
    public void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        lock = new RedisTemplateDistributedLock(stringRedisTemplate);
    }

    @Test
    public void tryLock_success() {
        when(stringRedisTemplate.opsForValue())
                .thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(stringRedisTemplate.opsForValue().setIfAbsent(any(String.class), any(String.class), any(Duration.class)))
                .thenReturn(true);

        boolean result = lock.tryLock("test-key", 10, 30);
        assertTrue(result);
        verify(stringRedisTemplate.opsForValue()).setIfAbsent(eq("test-key"), any(String.class), any(Duration.class));
    }

    @Test
    public void tryLock_fails_afterRetries() {
        when(stringRedisTemplate.opsForValue())
                .thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(stringRedisTemplate.opsForValue().setIfAbsent(any(String.class), any(String.class), any(Duration.class)))
                .thenReturn(false);

        boolean result = lock.tryLock("test-key", 0, 30);
        assertFalse(result);
        verify(stringRedisTemplate.opsForValue(), atLeastOnce())
                .setIfAbsent(eq("test-key"), any(String.class), any(Duration.class));
    }

    @Test
    public void tryLock_zeroWait_returnsFalseImmediately() {
        when(stringRedisTemplate.opsForValue())
                .thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(stringRedisTemplate.opsForValue().setIfAbsent(any(String.class), any(String.class), any(Duration.class)))
                .thenReturn(false);

        boolean result = lock.tryLock("test-key", 0, 30);
        assertFalse(result);
    }

    @Test
    public void unlock_callsLuaScript() {
        when(stringRedisTemplate.opsForValue())
                .thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(stringRedisTemplate.opsForValue().setIfAbsent(any(String.class), any(String.class), any(Duration.class)))
                .thenReturn(true);

        lock.tryLock("test-key", 10, 30);

        lock.unlock("test-key");
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                eq(Collections.singletonList("test-key")),
                anyString());
    }

    @Test
    public void unlock_unknownKey_noOp() {
        lock.unlock("nonexistent-key");
        verifyNoInteractions(stringRedisTemplate);
    }
}
