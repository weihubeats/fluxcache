package com.fluxcache.redis.spring.cache;

import com.fluxcache.core.model.FluxCacheCacheable;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SessionCallback;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SpringDataRedisCacheTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOps;
    private RedisConnection redisConnection;
    private RedisConnectionFactory connectionFactory;

    @Before
    public void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        redisConnection = mock(RedisConnection.class);
        connectionFactory = mock(RedisConnectionFactory.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.isPipelined()).thenReturn(false);
    }

    @Test
    public void getValue_hit() {
        FluxCacheCacheable cacheable = buildCacheable();
        SpringDataRedisCache<String, String> cache = new SpringDataRedisCache<>(
                true, redisTemplate, cacheable);

        String key = "user:1";
        when(valueOps.get(buildKey(key))).thenReturn("Alice");

        String value = cache.lookup(key);
        assertEquals("Alice", value);
        verify(valueOps).get(buildKey(key));
    }

    @Test
    public void getValue_miss() {
        FluxCacheCacheable cacheable = buildCacheable();
        SpringDataRedisCache<String, String> cache = new SpringDataRedisCache<>(
                true, redisTemplate, cacheable);

        String key = "user:1";
        when(valueOps.get(buildKey(key))).thenReturn(null);

        String value = cache.lookup(key);
        assertNull(value);
    }

    @Test
    public void getValues_batchMget() {
        FluxCacheCacheable cacheable = buildCacheable();
        SpringDataRedisCache<String, String> cache = new SpringDataRedisCache<>(
                true, redisTemplate, cacheable);

        List<String> keys = Arrays.asList("user:1", "user:2", "user:3");
        List<String> values = Arrays.asList("Alice", null, "Charlie");
        when(valueOps.multiGet(any(List.class))).thenReturn(values);

        Map<String, String> result = cache.getValues(keys);

        assertEquals(2, result.size());
        assertEquals("Alice", result.get("user:1"));
        assertEquals("Charlie", result.get("user:3"));
        verify(valueOps).multiGet(any(List.class));
    }

    @Test
    public void putValue_setsWithTtl() {
        FluxCacheCacheable cacheable = buildCacheable();
        SpringDataRedisCache<String, String> cache = new SpringDataRedisCache<>(
                true, redisTemplate, cacheable);

        String key = "user:1";
        String value = "Alice";

        cache.putValue(key, value);
        verify(valueOps).set(eq(buildKey(key)), eq(value), any(Duration.class));
    }

    @Test
    public void putValues_pipeline() {
        FluxCacheCacheable cacheable = buildCacheable();
        SpringDataRedisCache<String, String> cache = new SpringDataRedisCache<>(
                true, redisTemplate, cacheable);

        Map<String, String> values = new HashMap<>();
        values.put("user:1", "Alice");
        values.put("user:2", "Bob");
        when(redisTemplate.executePipelined(any(SessionCallback.class))).thenReturn(null);

        cache.putValues(values);
        verify(redisTemplate).executePipelined(any(SessionCallback.class));
    }

    @Test
    public void evictValue_deletesKey() {
        FluxCacheCacheable cacheable = buildCacheable();
        SpringDataRedisCache<String, String> cache = new SpringDataRedisCache<>(
                true, redisTemplate, cacheable);

        String key = "user:1";

        cache.evictValue(key);
        verify(redisTemplate).delete(buildKey(key));
    }

    @Test
    public void batchEvictValue_deletesMultipleKeys() {
        FluxCacheCacheable cacheable = buildCacheable();
        SpringDataRedisCache<String, String> cache = new SpringDataRedisCache<>(
                true, redisTemplate, cacheable);

        List<String> keys = Arrays.asList("user:1", "user:2");

        cache.batchEvictValue(keys);
        verify(redisTemplate).delete((Collection<String>) any());
    }

    public void clear_scansAndDeletesByPrefix() throws Exception {
        FluxCacheCacheable cacheable = buildCacheable();
        SpringDataRedisCache<String, String> cache = new SpringDataRedisCache<>(
                true, redisTemplate, cacheable);

        java.util.Iterator<byte[]> found = java.util.Arrays.asList(
                "FluxCache:testCache:a".getBytes(),
                "FluxCache:testCache:b".getBytes()).iterator();
        org.springframework.data.redis.core.Cursor<byte[]> cursor =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.Cursor.class);
        org.mockito.Mockito.when(cursor.hasNext()).thenReturn(true, true, false);
        org.mockito.Mockito.when(cursor.next())
                .thenReturn(found.next(), found.next());

        org.springframework.data.redis.connection.RedisConnection connection =
                org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnection.class);
        org.mockito.Mockito.when(connection.scan(org.mockito.ArgumentMatchers.any(
                        org.springframework.data.redis.core.ScanOptions.class))).thenReturn(cursor);

        org.mockito.Mockito.when(redisTemplate.execute(org.mockito.ArgumentMatchers.any(
                        org.springframework.data.redis.core.RedisCallback.class)))
                .thenAnswer(inv -> inv.getArgument(0,
                        org.springframework.data.redis.core.RedisCallback.class).doInRedis(connection));

        cache.clear();

        org.mockito.Mockito.verify(redisTemplate).delete(
                org.mockito.ArgumentMatchers.argThat((java.util.Collection<String> list) ->
                        list != null && list.size() == 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildKey_empty_throws() {
        FluxCacheCacheable cacheable = buildCacheable();
        SpringDataRedisCache<String, String> cache = new SpringDataRedisCache<>(
                true, redisTemplate, cacheable);

        cache.lookup(null);
    }

    private FluxCacheCacheable buildCacheable() {
        return (FluxCacheCacheable) new FluxCacheCacheable.Builder()
                .setTtl(3600L)
                .setUnit(java.util.concurrent.TimeUnit.SECONDS)
                .setCacheName("testCache")
                .build();
    }

    private String buildKey(String key) {
        return "FluxCache:testCache:" + key;
    }
}
