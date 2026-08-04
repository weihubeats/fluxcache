package com.fluxcache.core.caffeine;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.model.PutCacheDTO;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Caffeine 一级缓存：空值策略、Reader/Writer 同步事件、直写直删、key 级高级操作。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCaffeineCacheTest {

    private com.fluxcache.core.caffeine.sync.CacheSyncStrategy syncStrategy;
    private FluxCacheProperties properties;
    private Cache<Object, Object> caffeine;
    private FluxCaffeineCache<Object, Object> cache;

    @Before
    public void setUp() {
        syncStrategy = mock(com.fluxcache.core.caffeine.sync.CacheSyncStrategy.class);
        properties = new FluxCacheProperties();
        caffeine = Caffeine.newBuilder().maximumSize(100).build();
        cache = new FluxCaffeineCache<>("caffeine-test", caffeine, true, syncStrategy, properties);
    }

    @Test
    public void put_get_roundTrip_withSyncEvent() {
        cache.put("k", "v");
        assertEquals("v", cache.get("k", Object.class));
        verify(syncStrategy).sendPutEvent(any(PutCacheDTO.class));
    }

    @Test
    public void get_withLoader_onMiss_populates() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertEquals("loaded", cache.get("miss", (Callable<Object>) () -> {
            calls.incrementAndGet();
            return "loaded";
        }));
        assertEquals(1, calls.get());
        assertEquals("loaded", cache.get("miss", Object.class));
    }

    @Test
    public void get_withFailingLoader_wrapsValueRetrieval() {
        try {
            cache.get("boom", (Callable<Object>) () -> {
                throw new IllegalStateException("load-fail");
            });
            org.junit.Assert.fail("应抛出 ValueRetrievalException");
        } catch (FluxCache.ValueRetrievalException e) {
            assertNotNull(e.getKey());
        }
    }

    @Test
    public void put_nullWhenDisallowed_skippedEntirely() {
        FluxCaffeineCache<Object, Object> strict =
                new FluxCaffeineCache<>("strict", caffeine, false, syncStrategy, properties);
        strict.put("n", null);

        assertNull(strict.get("n", Object.class));
        assertNull(caffeine.getIfPresent("n"));
    }

    @Test
    public void nullValue_allowed_storesPlaceholder() {
        cache.put("n", null);
        assertNotNull(caffeine.getIfPresent("n"));
        assertNull(cache.get("n", Object.class));
    }

    @Test
    public void get_wrongType_throws() {
        cache.put("typed", "text");
        try {
            ((FluxCaffeineCache) cache).get("typed", Integer.class);
            org.junit.Assert.fail("应抛出 IllegalStateException");
        } catch (IllegalStateException expected) {
            // type mismatch
        }
    }

    @Test
    public void evict_invalidatesAndPublishesDelete() {
        cache.put("e", "v");
        cache.evict("e");

        assertNull(caffeine.getIfPresent("e"));
        verify(syncStrategy).postEvict(any(DeleteCacheDTO.class));
    }

    @Test
    public void evictDirectly_noSyncEvent() {
        cache.put("d", "v");
        cache.evictDirectly("d");
        assertNull(caffeine.getIfPresent("d"));
        verify(syncStrategy, never()).postEvict(any());
    }

    @Test
    public void batchEvict_publishesOnce() {
        cache.put("b1", "v");
        cache.put("b2", "v");
        cache.batchEvict(List.of("b1", "b2"));

        assertNull(caffeine.getIfPresent("b1"));
        assertNull(caffeine.getIfPresent("b2"));
        verify(syncStrategy).postEvict(any(DeleteCacheDTO.class));
    }

    @Test
    public void batchEvictDirectly_noSync() {
        cache.put("c1", "v");
        cache.batchEvictDirectly(List.of("c1"));
        assertNull(caffeine.getIfPresent("c1"));
    }

    @Test
    public void clear_invalidatesAllAndPublishes() {
        cache.put("x", "v");
        cache.clear();

        assertTrue(caffeine.asMap().isEmpty());
        verify(syncStrategy).postClear(any(DeleteCacheDTO.class));
    }

    @Test
    public void clearDirectly_noSync() {
        cache.put("y", "v");
        assertTrue(cache.clearDirectly());
        assertTrue(caffeine.asMap().isEmpty());
        verify(syncStrategy, never()).postClear(any());
    }

    @Test
    public void putDirectly_noSync() {
        cache.putDirectly("z", "v");
        assertEquals("v", caffeine.getIfPresent("z"));
        verify(syncStrategy, never()).sendPutEvent(any());
    }

    @Test
    public void evictIfPresent_trueOnlyWhenPresent() {
        assertTrue(!cache.evictIfPresent("zzz"));
        cache.put("present", "v");
        assertTrue(cache.evictIfPresent("present"));
        assertTrue(!cache.evictIfPresent("present"));
    }

    @Test
    public void invalidate_nonEmpty_clearsAndReturnsTrue() {
        cache.put("i", "v");
        assertTrue(cache.invalidate());
        assertTrue(cache.getNativeCache().asMap().isEmpty());
    }

    @Test
    public void putIfAbsent_present_returnsExistingAndDoesNotOverwrite() {
        cache.put("pa", "old");
        FluxCache.ValueWrapper<Object> existing = cache.putIfAbsent("pa", "new");
        assertNotNull(existing);
        assertEquals("old", existing.get());
        assertEquals("old", cache.get("pa", Object.class));
    }

    @Test
    public void putIfAbsent_absent_putAndReturnNull() {
        assertNull(cache.putIfAbsent("pb", "fresh"));
        assertEquals("fresh", cache.get("pb", Object.class));
    }

    @Test
    public void getAll_bothLoadAndPlain() {
        AtomicInteger loaderCalls = new AtomicInteger();
        LoadingCache<Object, Object> loading = Caffeine.newBuilder()
                .build(k -> {
                    loaderCalls.incrementAndGet();
                    return "lv-" + k;
                });
        FluxCaffeineCache<Object, Object> loadingCache =
                new FluxCaffeineCache<>("loading", loading, true, syncStrategy, properties);

        Map<Object, Object> values = loadingCache.getAll(List.of("a", "b"), Object.class);
        assertEquals(2, values.size());
        assertEquals("lv-a", values.get("a"));
        assertEquals(2, loaderCalls.get()); // LoadingCache.getAll 会为缺失 key 调用 loader

        Map<Object, Object> present = cache.getAll(List.of("missing"), Object.class);
        assertTrue(present.isEmpty());
        assertEquals(0, cache.getAllAsync(List.of(), Object.class).size());
    }

    @Test
    public void getNativeCache_returnsCaffeine() {
        cache.put("z", "v");
        com.github.benmanes.caffeine.cache.Cache nativeCache = cache.getNativeCache();
        assertEquals("v", nativeCache.getIfPresent("z"));
    }

    @Test
    public void constructor_rejectsNull() {
        try {
            new FluxCaffeineCache<>(null, caffeine, true, syncStrategy, properties);
            org.junit.Assert.fail("应抛出异常");
        } catch (IllegalArgumentException expected) {
            // null name
        }
        try {
            new FluxCaffeineCache<>("name", null, true, syncStrategy, properties);
            org.junit.Assert.fail("应抛出异常");
        } catch (IllegalArgumentException expected) {
            // null cache
        }
    }
}