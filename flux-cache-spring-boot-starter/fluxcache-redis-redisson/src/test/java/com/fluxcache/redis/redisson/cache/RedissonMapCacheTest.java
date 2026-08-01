package com.fluxcache.redis.redisson.cache;

import com.fluxcache.core.model.FluxCacheCacheable;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class RedissonMapCacheTest {

    private RedissonClient redissonClient;
    private RMapCache rMapCache;

    @Before
    public void setUp() {
        redissonClient = mock(RedissonClient.class);
        rMapCache = mock(RMapCache.class);
        when(redissonClient.getMapCache(anyString())).thenReturn(rMapCache);
    }

    @Test
    public void lookup_hit() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonMapCache<String, String> cache = new RedissonMapCache<>(
                true, redissonClient, cacheable);

        when(rMapCache.get("user:1")).thenReturn("Alice");

        String value = cache.lookup("user:1");
        assertEquals("Alice", value);
    }

    @Test
    public void lookup_miss() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonMapCache<String, String> cache = new RedissonMapCache<>(
                true, redissonClient, cacheable);

        when(rMapCache.get("user:1")).thenReturn(null);

        String value = cache.lookup("user:1");
        assertNull(value);
    }

    @Test
    public void putValue_setsTtl() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonMapCache<String, String> cache = new RedissonMapCache<>(
                true, redissonClient, cacheable);

        cache.putValue("user:1", "Alice");
        verify(rMapCache).put(eq("user:1"), eq("Alice"), anyLong(), eq(cacheable.getUnit()));
    }

    @Test
    public void evictValue_removes() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonMapCache<String, String> cache = new RedissonMapCache<>(
                true, redissonClient, cacheable);

        cache.evictValue("user:1");
        verify(rMapCache).remove("user:1");
    }

    @Test
    public void batchEvictValue_removesMultiple() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonMapCache<String, String> cache = new RedissonMapCache<>(
                true, redissonClient, cacheable);

        List<String> keys = List.of("user:1", "user:2");
        cache.batchEvictValue(keys);
        verify(rMapCache).remove("user:1");
        verify(rMapCache).remove("user:2");
    }

    @Test
    public void clear_clearsRMapCache() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonMapCache<String, String> cache = new RedissonMapCache<>(
                true, redissonClient, cacheable);

        cache.clear();
        verify(rMapCache).clear();
    }

    @Test
    public void getValues_batchGet() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonMapCache<String, String> cache = new RedissonMapCache<>(
                true, redissonClient, cacheable);

        Map<String, String> expected = new HashMap<>();
        expected.put("user:1", "Alice");
        when(rMapCache.getAll(any(HashSet.class))).thenReturn(expected);

        Map<String, String> result = cache.getValues(List.of("user:1", "user:2"));
        assertEquals(1, result.size());
        assertEquals("Alice", result.get("user:1"));
    }

    @Test
    public void getValuesAsync_batchGet() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonMapCache<String, String> cache = new RedissonMapCache<>(
                true, redissonClient, cacheable);

        Map<String, String> expected = new HashMap<>();
        expected.put("user:1", "Alice");
        RFuture<Map<String, String>> future = mock(RFuture.class);
        try {
            when(future.get()).thenReturn(expected);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(rMapCache.getAllAsync(any(HashSet.class))).thenReturn(future);

        Map<String, String> result = cache.getValuesAsync(List.of("user:1"));
        assertEquals(1, result.size());
        assertEquals("Alice", result.get("user:1"));
    }

    private FluxCacheCacheable buildCacheable() {
        return (FluxCacheCacheable) new FluxCacheCacheable.Builder()
                .setTtl(3600L)
                .setUnit(java.util.concurrent.TimeUnit.SECONDS)
                .setCacheName("testCache")
                .build();
    }
}
