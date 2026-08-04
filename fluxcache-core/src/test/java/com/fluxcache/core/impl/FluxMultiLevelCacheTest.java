package com.fluxcache.core.impl;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.exception.FluxCacheNotSupperException;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.spi.FluxCacheCreateContext;
import com.fluxcache.core.spi.FluxCacheCreator;
import com.fluxcache.core.spi.FluxCacheCreatorRegistry;
import com.fluxcache.core.impl.creator.CaffeineFluxCacheCreator;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 多级缓存：L1 命中直达、L2 命中回填 L1、批量读取合并、写入/清理级联、loader 加载链路。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxMultiLevelCacheTest {

    private static final String CACHE_NAME = "multi-cache";

    private final CacheSyncStrategy syncStrategy = mock(CacheSyncStrategy.class);
    private final FluxCacheMonitor monitor = mock(FluxCacheMonitor.class);
    private final FluxCacheProperties properties = new FluxCacheProperties();

    private MapRemote l2;
    private FluxMultiLevelCache<String, String> cache;

    @Before
    public void setUp() {
        l2 = new MapRemote();
        FluxCacheCreatorRegistry registry = new FluxCacheCreatorRegistry(Arrays.asList(
                new CaffeineFluxCacheCreator(),
                new MapRemoteCreator(l2)));
        FluxCacheFactory factory = new FluxCacheFactory(registry);

        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setFirstCacheConfig(config(FluxCacheType.CAFFEINE))
                .setSecondaryCacheable(config(FluxCacheType.REDIS))
                .setAllowNullValues(true)
                .setCacheName(CACHE_NAME)
                .setMethodName("load")
                .setKey("#k")
                .setFluxCacheLevel(FluxCacheLevel.SecondaryCacheable)
                .build();
        @SuppressWarnings("unchecked")
        FluxCache<String, String> created = (FluxCache<String, String>)
                factory.createFluxCache(op, properties, syncStrategy, monitor);
        cache = (FluxMultiLevelCache<String, String>) created;
    }

    @Test
    public void l1Hit_returnsValue_withoutL2Dependency() {
        // 仅 L1 有值（模拟 L2 数据源不可用），命中路径不访问 L2
        cache.getFluxFirstCache().put("a", "v1");
        assertEquals("v1", cache.get("a", String.class));
        assertNull(l2.store.get("a"));
    }

    @Test
    public void l2Hit_fillsL1_onlyOnMissedKey() {
        l2.store.put("k1", "redis-1");
        l2.store.put("k2", "redis-2");
        cache.put("k1", "local-1");

        Map<String, String> all = cache.getAll(Arrays.asList("k1", "k2"), String.class);

        assertEquals("local-1", all.get("k1"));
        assertEquals("redis-2", all.get("k2"));
        // L2 命中的 key 被回填到 L1
        assertNotNull(cache.getFluxFirstCache().get("k2", String.class));
    }

    @Test
    public void getAll_overloadedL2_putBackToL1() {
        l2.store.put("x", "v");
        cache.getAll(Arrays.asList("x"), String.class);
        assertEquals("v", cache.getFluxFirstCache().get("x", String.class));
    }

    @Test
    public void getAllAsync_sameAsGetAll() {
        l2.store.put("y", "v");
        Map<String, String> all = cache.getAllAsync(List.of("y"), String.class);
        assertEquals("v", all.get("y"));
    }

    @Test
    public void get_withLoader_onMiss_loadsAndCaches_bothLevels() {
        AtomicInteger calls = new AtomicInteger();
        String value = cache.get("m1", (Callable<String>) () -> {
            calls.incrementAndGet();
            return "loaded";
        });
        assertEquals("loaded", value);
        assertEquals("loaded", cache.get("m1", String.class));
        assertEquals("loaded", l2.store.get("m1"));
        assertEquals(1, calls.get());
    }

    @Test
    public void get_withLoaderMissAndFailingLoader_propagates() {
        AtomicInteger calls = new AtomicInteger();
        try {
            cache.get("m2", (Callable<String>) () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("boom");
            });
            org.junit.Assert.fail("应抛出异常");
        } catch (FluxCache.ValueRetrievalException expected) {
            assertEquals("m2", expected.getKey());
        }
    }

    @Test
    public void lookup_l2Hit_fillsL1() {
        l2.store.put("z", "lv");
        assertEquals("lv", cache.get("z", String.class));
        // 二级命中且 L1 已回填
        assertEquals("lv", cache.getFluxFirstCache().get("z", String.class));
    }

    @Test
    public void putWritesBothLevels() {
        cache.put("both", "value");
        assertEquals("value", l2.store.get("both"));
        assertEquals("value", cache.getFluxFirstCache().get("both", String.class));
    }

    @Test
    public void evict_clearsBothLevels() {
        cache.put("both", "value");
        cache.evict("both");
        assertNull(cache.get("both", String.class));
        assertNull(l2.store.get("both"));
        assertNull(cache.getFluxFirstCache().get("both", String.class));
    }

    @Test
    public void batchEvict_clearsBothLevels() {
        cache.put("b1", "v");
        cache.put("b2", "v");
        l2.store.put("b3", "v");
        cache.batchEvict(Arrays.asList("b1", "b2", "b3"));
        assertNull(l2.store.get("b1"));
        assertNull(l2.store.get("b2"));
        assertNull(l2.store.get("b3"));
    }

    @Test
    public void clear_clearsBothLevels() {
        cache.put("c", "v");
        cache.clear();
        assertNull(cache.getFluxFirstCache().get("c", String.class));
        assertNull(l2.store.get("c"));
    }

    @Test
    public void putValues_async_writesBoth() {
        Map<String, String> data = new HashMap<>();
        data.put("p1", "v");
        cache.putAllAsync(data);
        assertEquals("v", l2.store.get("p1"));
        assertEquals("v", cache.getFluxFirstCache().get("p1", String.class));
    }

    @Test
    public void getValues_emptyOrNull_returnsEmptyMap() {
        assertEquals(0, cache.getValues(null).size());
        assertEquals(0, cache.getValues(List.of()).size());
    }

    @Test
    public void nullValue_allowed_roundTrips() {
        cache.put("nullable", null);
        assertNull(cache.get("nullable", String.class));
        // L1 命中返回 null 且不再回源；远程缓存不缓存 null
        assertNull(cache.getFluxFirstCache().get("nullable", String.class));
        assertNull(l2.store.get("nullable"));
    }

    @Test
    public void putIfAbsent_defaultFlow() {
        assertNull(cache.putIfAbsent("pa", "v1"));
        assertEquals("v1", cache.putIfAbsent("pa", "v2").get());
        assertEquals("v1", cache.get("pa", String.class));
    }

    @Test
    public void evictIfPresent_and_invalidate_defaults() {
        cache.put("d", "v");
        cache.evictIfPresent("d");
        assertNull(cache.get("d", String.class));
        cache.put("d2", "v");
        cache.invalidate();
        assertNull(cache.get("d2", String.class));
    }

    @Test
    public void factory_nullLevel_fallsBackToGlobalDefault() {
        FluxCacheCreatorRegistry registry = new FluxCacheCreatorRegistry(Arrays.asList(
                new CaffeineFluxCacheCreator(),
                new MapRemoteCreator(l2)));
        FluxCacheFactory factory = new FluxCacheFactory(registry);
        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setFirstCacheConfig(config(FluxCacheType.CAFFEINE))
                .setSecondaryCacheable(config(FluxCacheType.REDIS))
                .setCacheName("fallback-cache")
                .setMethodName("load")
                .setKey("#k")
                .setFluxCacheLevel(FluxCacheLevel.NULL)
                .build();
        FluxCacheProperties global = new FluxCacheProperties();
        global.setDefaultCacheLevel(FluxCacheLevel.SecondaryCacheable);

        Object created = factory.createFluxCache(op, global, syncStrategy, monitor);

        assertTrue(created instanceof FluxMultiLevelCache);
    }

    @Test
    public void factory_firstCacheable_singleLevel() {
        FluxCacheFactory factory = new FluxCacheFactory(FluxCacheCreatorRegistry.withDefaults());
        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setFirstCacheConfig(config(FluxCacheType.CAFFEINE))
                .setCacheName("single-cache")
                .setMethodName("load")
                .setKey("#k")
                .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
                .build();

        Object created = factory.createFluxCache(op, properties, syncStrategy, monitor);

        assertTrue(created instanceof FluxCaffeineCache);
    }

    @Test
    public void factory_missingFirstConfig_throws() {
        FluxCacheFactory factory = new FluxCacheFactory(FluxCacheCreatorRegistry.withDefaults());
        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setSecondaryCacheable(config(FluxCacheType.REDIS))
                .setCacheName("no-first")
                .setMethodName("load")
                .setKey("#k")
                .setFluxCacheLevel(FluxCacheLevel.SecondaryCacheable)
                .build();

        try {
            factory.createFluxCache(op, properties, syncStrategy, monitor);
            org.junit.Assert.fail("应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // first cache config must not be null
        }
    }

    @Test
    public void factory_unsupportedLevel_throws() {
        FluxCacheFactory factory = new FluxCacheFactory(FluxCacheCreatorRegistry.withDefaults());
        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setFirstCacheConfig(config(FluxCacheType.CAFFEINE))
                .setCacheName("bad-level")
                .setMethodName("load")
                .setKey("#k")
                .setFluxCacheLevel(FluxCacheLevel.NULL)
                .build();
        FluxCacheProperties global = new FluxCacheProperties();
        global.setDefaultCacheLevel(FluxCacheLevel.NULL);

        try {
            factory.createFluxCache(op, global, syncStrategy, monitor);
            org.junit.Assert.fail("应抛出 FluxCacheNotSupperException");
        } catch (FluxCacheNotSupperException expected) {
            // unsupported cache level
        }
    }

    private static FluxCacheConfig config(FluxCacheType type) {
        return new FluxCacheConfig.Builder()
                .setTtl(5L)
                .setInitSize(16)
                .setMaxSize(100)
                .setUnit(TimeUnit.MINUTES)
                .setCacheType(type)
                .build();
    }

    /**
     * 无延迟的本地 KV，充当内存版二级缓存。
     */
    static class MapRemote extends FluxAbstractValueAdaptingCache<String, String> {

        final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

        MapRemote() {
            super(true, "map-remote");
        }

        @Override
        protected Map<String, String> getValues(List<String> keys) {
            Map<String, String> found = new HashMap<>();
            for (String k : keys) {
                String v = store.get(k);
                if (v != null) {
                    found.put(k, v);
                }
            }
            return found;
        }

        @Override
        protected Map<String, String> getValuesAsync(List<String> keys) {
            return getValues(keys);
        }

        @Override
        protected void putValues(Map<String, String> map) {
            store.putAll(map);
        }

        @Override
        protected void putValuesAsync(Map<String, String> map) {
            store.putAll(map);
        }

        @Override
        protected String getValue(String key, Callable<String> valueLoader) {
            String cached = lookup(key);
            if (cached != null) {
                return cached;
            }
            try {
                String value = valueLoader.call();
                putValue(key, value);
                return value;
            } catch (Exception e) {
                throw new FluxCache.ValueRetrievalException(key, valueLoader, e);
            }
        }

        @Override
        protected void putValue(String key, Object value) {
            if (value instanceof com.fluxcache.core.model.FluxNullValue) {
                return;
            }
            store.put(key, (String) value);
        }

        @Override
        protected void evictValue(String key) {
            store.remove(key);
        }

        @Override
        protected void batchEvictValue(List<String> keys) {
            keys.forEach(store::remove);
        }

        @Override
        protected String lookup(String key) {
            return store.get(key);
        }

        @Override
        public void clear() {
            store.clear();
        }
    }

    static class MapRemoteCreator implements FluxCacheCreator {

        private final MapRemote remote;

        MapRemoteCreator(MapRemote remote) {
            this.remote = remote;
        }

        @Override
        public FluxCacheType supportType() {
            return FluxCacheType.REDIS;
        }

        @Override
        public FluxAbstractValueAdaptingCache<?, ?> create(com.fluxcache.core.model.FluxCacheCacheable cacheable,
                                                           FluxCacheCreateContext context) {
            return remote;
        }
    }
}