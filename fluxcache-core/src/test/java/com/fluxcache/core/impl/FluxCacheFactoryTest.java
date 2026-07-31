package com.fluxcache.core.impl;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.exception.FluxCacheNotSupperException;
import com.fluxcache.core.impl.creator.CaffeineFluxCacheCreator;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.spi.FluxCacheCreateContext;
import com.fluxcache.core.spi.FluxCacheCreator;
import com.fluxcache.core.spi.FluxCacheCreatorRegistry;
import org.junit.Test;


import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * @author : wh
 * @date : 2026/7/29
 */
public class FluxCacheFactoryTest {

    
    private final CacheSyncStrategy cacheSyncStrategy = mock(CacheSyncStrategy.class);
    private final FluxCacheMonitor cacheMonitor = mock(FluxCacheMonitor.class);
    private final FluxCacheProperties cacheProperties = new FluxCacheProperties();
    private final FluxCacheFactory factory = FluxCacheFactory.withDefaults();

    @Test
    public void createFirstLevelCache_usesFirstConfigType() {
        FluxMultilevelCacheCacheable ca = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
                .setCacheName("firstOnly")
                .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
                .setAllowNullValues(true)
                .setFirstCacheConfig(caffeineConfig(5L, 100))
                .build();

        FluxCache<?, ?> cache = factory.createFluxCache(
                ca, cacheProperties, cacheSyncStrategy, cacheMonitor);

        assertTrue(cache instanceof FluxCaffeineCache);
        assertEquals("firstOnly", cache.getName());
    }

    @Test
    public void createSecondaryCache_bindsL1AndL2ConfigsCorrectly() {
        FluxMultilevelCacheCacheable ca = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
                .setCacheName("twoLevel")
                .setFluxCacheLevel(FluxCacheLevel.SecondaryCacheable)
                .setAllowNullValues(true)
                .setFirstCacheConfig(caffeineConfig(1L, 10))
                .setSecondaryCacheConfig(caffeineConfig(3L, 20))
                .build();

        FluxCache<?, ?> cache = factory.createFluxCache(
                ca, cacheProperties, cacheSyncStrategy, cacheMonitor);

        assertTrue(cache instanceof FluxMultiLevelCache);
        FluxMultiLevelCache<?, ?> multilevel = (FluxMultiLevelCache<?, ?>) cache;
        assertEquals("twoLevel", multilevel.getName());
        assertTrue(multilevel.getFluxFirstCache() instanceof FluxCaffeineCache);
        assertTrue(multilevel.getFluxSecondaryCache() instanceof FluxCaffeineCache);
    }

    @Test
    public void createCache_missingSecondaryConfig_throws() {
        FluxMultilevelCacheCacheable ca = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
                .setCacheName("missingSecondary")
                .setFluxCacheLevel(FluxCacheLevel.SecondaryCacheable)
                .setAllowNullValues(true)
                .setFirstCacheConfig(caffeineConfig(1L, 10))
                .build();

        try {
            factory.createFluxCache(ca, cacheProperties, cacheSyncStrategy, cacheMonitor);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("secondary"));
        }
    }

    @Test
    public void multilevelGetValue_hitsSecondaryBeforeLoader() {
        FluxMultilevelCacheCacheable ca = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
                .setCacheName("loaderPath")
                .setFluxCacheLevel(FluxCacheLevel.SecondaryCacheable)
                .setAllowNullValues(true)
                .setFirstCacheConfig(caffeineConfig(1L, 10))
                .setSecondaryCacheConfig(caffeineConfig(3L, 20))
                .build();

        @SuppressWarnings("unchecked")
        FluxMultiLevelCache<String, String> cache =
                (FluxMultiLevelCache<String, String>) factory.createFluxCache(
                        ca, cacheProperties, cacheSyncStrategy, cacheMonitor);

        cache.getFluxSecondaryCache().put("k1", "from-l2");

        AtomicInteger loaderCalls = new AtomicInteger();
        String value = cache.get("k1", () -> {
            loaderCalls.incrementAndGet();
            return "from-loader";
        });

        assertEquals("from-l2", value);
        assertEquals(0, loaderCalls.get());
        assertEquals("from-l2", cache.getFluxFirstCache().get("k1", String.class));
    }

    @Test
    public void multilevelGetValue_loadsAndBackfillsBothLevelsOnMiss() {
        FluxMultilevelCacheCacheable ca = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
                .setCacheName("fullMiss")
                .setFluxCacheLevel(FluxCacheLevel.SecondaryCacheable)
                .setAllowNullValues(true)
                .setFirstCacheConfig(caffeineConfig(1L, 10))
                .setSecondaryCacheConfig(caffeineConfig(3L, 20))
                .build();

        @SuppressWarnings("unchecked")
        FluxMultiLevelCache<String, String> cache =
                (FluxMultiLevelCache<String, String>) factory.createFluxCache(
                        ca, cacheProperties, cacheSyncStrategy, cacheMonitor);

        AtomicInteger loaderCalls = new AtomicInteger();
        String value = cache.get("k2", () -> {
            loaderCalls.incrementAndGet();
            return "loaded";
        });

        assertEquals("loaded", value);
        assertEquals(1, loaderCalls.get());
        assertEquals("loaded", cache.getFluxFirstCache().get("k2", String.class));
        assertEquals("loaded", cache.getFluxSecondaryCache().get("k2", String.class));
    }

    @Test
    public void unsupportedCacheType_throws() {
        FluxCacheConfig bad = new FluxCacheConfig.Builder()
                .setTtl(1L)
                .setInitSize(1)
                .setMaxSize(10)
                .setUnit(TimeUnit.MINUTES)
                .build();
        FluxMultilevelCacheCacheable ca = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
                .setCacheName("badType")
                .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
                .setAllowNullValues(true)
                .setFirstCacheConfig(bad)
                .build();

        try {
            factory.createFluxCache(ca, cacheProperties, cacheSyncStrategy, cacheMonitor);
            fail("expected FluxCacheNotSupperException");
        } catch (FluxCacheNotSupperException ex) {
            assertTrue(ex.getMessage().contains("Unsupported cache type"));
        }
    }

    @Test
    public void customCreator_overridesBuiltinForSameType() {
        AtomicReference<FluxCacheCacheable> seen = new AtomicReference<>();
        FluxAbstractValueAdaptingCache<?, ?> stub = mock(FluxAbstractValueAdaptingCache.class);

        FluxCacheCreator custom = new FluxCacheCreator() {
            @Override
            public FluxCacheType supportType() {
                return FluxCacheType.CAFFEINE;
            }

            @Override
            public FluxAbstractValueAdaptingCache<?, ?> create(FluxCacheCacheable cacheable, FluxCacheCreateContext context) {
                seen.set(cacheable);
                return stub;
            }
        };

        FluxCacheCreatorRegistry registry = new FluxCacheCreatorRegistry(Arrays.asList(
                new CaffeineFluxCacheCreator(),
                custom
        ));
        FluxCacheFactory customFactory = new FluxCacheFactory(registry);

        FluxMultilevelCacheCacheable ca = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
                .setCacheName("override")
                .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
                .setAllowNullValues(true)
                .setFirstCacheConfig(caffeineConfig(1L, 10))
                .build();

        FluxCache<?, ?> cache = customFactory.createFluxCache(
                ca, cacheProperties, cacheSyncStrategy, cacheMonitor);

        assertSame(stub, cache);
        assertEquals("override", seen.get().getCacheName());
    }

    private static FluxCacheConfig caffeineConfig(long ttl, int maxSize) {
        return new FluxCacheConfig.Builder()
                .setTtl(ttl)
                .setInitSize(16)
                .setMaxSize(maxSize)
                .setUnit(TimeUnit.MINUTES)
                .setCacheType(FluxCacheType.CAFFEINE)
                .build();
    }
}
