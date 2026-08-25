package com.fluxcache.core;

import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.caffeine.sync.NoOpCacheSyncStrategy;
import com.fluxcache.core.exception.FluxCacheMetaDataException;
import com.fluxcache.core.impl.FluxCacheFactory;
import com.fluxcache.core.interceptor.FluxCacheOperationSource;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.monitor.MonitorEventEnum;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存管理器：缓存创建/查询、旁路读写、批量失效、元数据与监控事件发布、Bean 后置扫描。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class DefaultFluxCacheManagerTest {

    private static final String CACHE = "manager-cache";

    private ObjectProvider<CacheSyncStrategy> syncProvider;
    private FluxCacheProperties properties;
    private FluxCacheOperationSource opSource;
    private FluxCacheMonitor monitor;
    private FluxCacheFactory factory;
    private FluxCache<Object, Object> cache;
    private DefaultFluxCacheManager manager;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        syncProvider = mock(ObjectProvider.class);
        when(syncProvider.getIfAvailable(any())).thenReturn(new NoOpCacheSyncStrategy());
        properties = new FluxCacheProperties();
        opSource = mock(FluxCacheOperationSource.class);
        monitor = mock(FluxCacheMonitor.class);
        factory = mock(FluxCacheFactory.class);
        cache = mock(FluxCache.class);
        when(factory.createFluxCache(any(), any(), any(), any())).thenAnswer(inv -> cache);
        when(cache.getName()).thenReturn(CACHE);
        manager = new DefaultFluxCacheManager(syncProvider, properties, opSource, monitor, factory);
    }

    @Test
    public void createCache_registersCacheAndMetadata() {
        FluxMultilevelCacheCacheable op = operation("manual-cache");
        manager.createCache(op);

        assertNotNull(manager.getCache("manual-cache"));
        assertSame(op, manager.getCacheMetaData("manual-cache"));
        assertEquals(1, manager.getAllCaches().size());
        assertEquals(1, manager.getAllCacheMetaData().size());
        verify(monitor).createNewCacheStatics("manual-cache");
    }

    @Test
    public void createCache_monitorDisabled_skipsStatics() {
        properties.setCacheMonitorEnable(false);
        manager.createCache(operation("no-monitor"));

        verify(monitor, never()).createNewCacheStatics("no-monitor");
    }

    @Test
    public void getCacheOrPut_hit_returnsWrapperValue() {
        manager.createCache(operation(CACHE));
        when(cache.get(any())).thenReturn(() -> "cached");

        String value = manager.getCacheOrPut(CACHE, "k1", () -> "loaded");

        assertEquals("cached", value);
        verify(monitor).publishMonitorEvent(argEvent(MonitorEventEnum.CACHE_HIT));
        verify(monitor, never()).publishMonitorEvent(argEvent(MonitorEventEnum.CACHE_MISSING));
    }

    @Test
    public void getCacheOrPut_miss_loadsPutsAndPublishes() throws Exception {
        manager.createCache(operation(CACHE));
        when(cache.get(any())).thenReturn(null);
        AtomicInteger calls = new AtomicInteger();

        String value = manager.getCacheOrPut(CACHE, "k2", () -> {
            calls.incrementAndGet();
            return "loaded";
        });

        assertEquals("loaded", value);
        assertEquals(1, calls.get());
        verify(cache).put("k2", "loaded");
        verify(monitor).publishMonitorEvent(argEvent(MonitorEventEnum.CACHE_MISSING));
        verify(monitor).publishMonitorEvent(argEvent(MonitorEventEnum.CACHE_PUT));
    }

    @Test
    public void getCacheOrPut_loaderThrows_wrappedInRuntime() {
        manager.createCache(operation(CACHE));
        when(cache.get(any())).thenReturn(null);

        try {
            manager.getCacheOrPut(CACHE, "k3", () -> {
                throw new IllegalStateException("load-fail");
            });
            fail("应抛出 RuntimeException");
        } catch (RuntimeException expected) {
            assertNotNull(expected.getCause());
        }
    }

    @Test
    public void getCacheOrPut_noCache_fallsBackToLoader() {
        String value = manager.getCacheOrPut("missing-cache", "k", () -> "fallback");
        assertEquals("fallback", value);
        verify(monitor, never()).publishMonitorEvent(any());
    }

    @Test
    public void evictAndClear_unknownCache_returnFalse() {
        assertFalse(manager.evictCache("missing-cache", java.util.Arrays.asList("a")));
        assertFalse(manager.clearCacheByName("missing-cache"));
    }

    @Test
    public void publish_monitorThrows_swallowedNotPropagated() {
        manager.createCache(operation(CACHE));
        org.mockito.Mockito.doThrow(new IllegalStateException("monitor-down"))
                .when(monitor).publishMonitorEvent(any());

        // 监控故障不得影响业务读写
        assertEquals("v", manager.getCacheOrPut(CACHE, "k-mon", () -> "v"));
    }

    @Test
    public void getCacheOrPut_cacheGetThrows_continuesToLoad() {
        manager.createCache(operation(CACHE));
        when(cache.get(any())).thenThrow(new IllegalStateException("redis-down"));

        String value = manager.getCacheOrPut(CACHE, "k4", () -> "recovered");

        assertEquals("recovered", value);
        verify(cache).put("k4", "recovered");
    }

    @Test
    public void getCacheOrPut_longKey_truncatedInEvent() {
        manager.createCache(operation(CACHE));
        when(cache.get(any())).thenReturn(() -> "v");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append('x');
        }
        manager.getCacheOrPut(CACHE, sb.toString(), () -> "v");
        verify(monitor).publishMonitorEvent(any());
    }

    @Test
    public void getCacheOrPut_nullKey_eventKeyIsNull() {
        manager.createCache(operation(CACHE));
        when(cache.get(any())).thenReturn(() -> "v");
        manager.getCacheOrPut(CACHE, null, () -> "v");
        verify(monitor).publishMonitorEvent(any());
    }

    @Test
    public void putCache_registersAndPublishesPut() {
        when(cache.getName()).thenReturn("manual-register");
        assertTrue(manager.putCache("manual-register", cache));
        assertNotNull(manager.getCache("manual-register"));
        verify(monitor).publishMonitorEvent(argEvent(MonitorEventEnum.CACHE_PUT));
    }

    @Test
    public void evictCache_missingCache_returnsFalse() {
        assertFalse(manager.evictCache("nope", List.of("a")));
        verify(monitor, never()).publishMonitorEvent(any());
    }

    @Test
    public void evictCache_existing_batchEvictsAndPublishes() {
        manager.createCache(operation(CACHE));

        assertTrue(manager.evictCache(CACHE, List.of("a", "b")));

        verify(cache).batchEvict(List.of("a", "b"));
        verify(monitor).publishMonitorEvent(argEvent(MonitorEventEnum.CACHE_EVICT));
    }

    @Test
    public void clearCacheByName_missing_returnsFalse() {
        assertFalse(manager.clearCacheByName("nope"));
    }

    @Test
    public void clearCacheByName_existing_clearsAndPublishes() {
        manager.createCache(operation(CACHE));

        assertTrue(manager.clearCacheByName(CACHE));

        verify(cache).clear();
        verify(monitor).publishMonitorEvent(argEvent(MonitorEventEnum.CACHE_EVICT));
    }

    @Test
    public void postProcessAfterInitialization_createsCacheForAnnotatedBean() throws Exception {
        Method load = SampleBean.class.getMethod("load", String.class);
        when(opSource.getCacheOperation(eq(load), any())).thenReturn(operation(CACHE));

        manager.postProcessAfterInitialization(new SampleBean(), "sample");

        assertNotNull(manager.getCache(CACHE));
        assertNotNull(manager.getCacheMetaData(CACHE));
        verify(monitor).createCacheStaticsMap(any());
    }

    @Test
    public void postProcessAfterInitialization_duplicateCacheName_throws() throws Exception {
        Method load = SampleBean.class.getMethod("load", String.class);
        when(opSource.getCacheOperation(eq(load), any())).thenReturn(operation(CACHE));
        manager.postProcessAfterInitialization(new SampleBean(), "sample");

        try {
            manager.postProcessAfterInitialization(new SampleBean(), "sample");
            fail("应抛出 FluxCacheMetaDataException");
        } catch (FluxCacheMetaDataException expected) {
            // duplicate cache name
        }
    }

    @Test
    public void postProcessAfterInitialization_noOperation_doesNothing() {
        when(opSource.getCacheOperation(any(), any())).thenReturn(null);
        Object bean = new SampleBean();

        assertSame(bean, manager.postProcessAfterInitialization(bean, "plain"));
        assertNull(manager.getCache(CACHE));
    }

    @Test
    public void getCacheStatics_delegatesToMonitor() {
        manager.getCacheStatics(CACHE);
        verify(monitor).getCacheStatics(CACHE);
    }

    private FluxMultilevelCacheCacheable operation(String cacheName) {
        return (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setCacheName(cacheName)
                .setMethodName("load")
                .setKey("#k")
                .build();
    }

    private com.fluxcache.core.monitor.FluxCacheMonitorEvent argEvent(MonitorEventEnum type) {
        return org.mockito.ArgumentMatchers.argThat(
                e -> e != null && e.getMonitorEventEnum() == type);
    }

    public static class SampleBean {

        public String load(String key) {
            return "value";
        }
    }
}