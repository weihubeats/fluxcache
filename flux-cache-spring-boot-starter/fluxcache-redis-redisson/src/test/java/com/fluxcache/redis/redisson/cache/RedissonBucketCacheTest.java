package com.fluxcache.redis.redisson.cache;

import com.fluxcache.core.model.FluxCacheCacheable;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RBuckets;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class RedissonBucketCacheTest {

    private RedissonClient redissonClient;
    private RBuckets buckets;

    @Before
    public void setUp() {
        redissonClient = mock(RedissonClient.class);
        buckets = mock(RBuckets.class);
        when(redissonClient.getBuckets()).thenReturn(buckets);
    }

    @Test
    public void lookup_hit() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonBucketCache<String, String> cache = new RedissonBucketCache<>(
                true, redissonClient, cacheable);

        String key = "user:1";
        RBucket bucket = mock(RBucket.class);
        when(bucket.get()).thenReturn("Alice");
        when(redissonClient.getBucket(buildKey(key))).thenReturn(bucket);

        String value = cache.lookup(key);
        assertEquals("Alice", value);
    }

    @Test
    public void lookup_miss() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonBucketCache<String, String> cache = new RedissonBucketCache<>(
                true, redissonClient, cacheable);

        String key = "user:1";
        RBucket bucket = mock(RBucket.class);
        when(bucket.get()).thenReturn(null);
        when(redissonClient.getBucket(buildKey(key))).thenReturn(bucket);

        String value = cache.lookup(key);
        assertNull(value);
    }

    @Test
    public void putValue_setsTtl() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonBucketCache<String, String> cache = new RedissonBucketCache<>(
                true, redissonClient, cacheable);

        String key = "user:1";
        RBucket bucket = mock(RBucket.class);
        when(redissonClient.getBucket(buildKey(key))).thenReturn(bucket);

        cache.putValue(key, "Alice");
        // proportional jitter: ttl within [base, base + 10%]
        verify(bucket).set(eq("Alice"), argThat((java.time.Duration d) ->
                d != null
                        && d.toSeconds() >= cacheable.getTtl()
                        && d.toSeconds() <= cacheable.getTtl() + cacheable.getTtl() / 10));
    }

    @Test
    public void evictValue_deletes() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonBucketCache<String, String> cache = new RedissonBucketCache<>(
                true, redissonClient, cacheable);

        String key = "user:1";
        RBucket bucket = mock(RBucket.class);
        when(redissonClient.getBucket(buildKey(key))).thenReturn(bucket);

        cache.evictValue(key);
        verify(bucket).delete();
    }

    @Test
    public void getValues_batchGet() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonBucketCache<String, String> cache = new RedissonBucketCache<>(
                true, redissonClient, cacheable);

        List<String> keys = List.of("user:1", "user:2");
        Map returned = new HashMap();
        returned.put(buildKey("user:1"), "Alice");
        when(buckets.get(any())).thenReturn(returned);

        Map<String, String> result = cache.getValues(keys);
        assertEquals(1, result.size());
        assertEquals("Alice", result.get("user:1"));
    }

    @Test
    public void clear_scansAndDeletesByPrefix() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonBucketCache<String, String> cache = new RedissonBucketCache<>(
                true, redissonClient, cacheable);

        org.redisson.api.RKeys keys = mock(org.redisson.api.RKeys.class);
        when(redissonClient.getKeys()).thenReturn(keys);
        when(keys.getKeysByPattern("FluxCache:testCache:*", 500))
                .thenReturn(List.of(buildKey("user:1"), buildKey("user:2")));

        cache.clear();

        // single batched delete of all scanned keys
        verify(keys).delete(buildKey("user:1"), buildKey("user:2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void lookup_nullKey_throws() {
        FluxCacheCacheable cacheable = buildCacheable();
        RedissonBucketCache<String, String> cache = new RedissonBucketCache<>(
                true, redissonClient, cacheable);
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
