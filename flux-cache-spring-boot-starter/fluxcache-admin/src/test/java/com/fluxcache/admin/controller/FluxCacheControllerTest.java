package com.fluxcache.admin.controller;

import com.fluxcache.admin.vo.FluxCacheAllStaticsVO;
import com.fluxcache.admin.vo.FluxCacheOperationVO;
import com.fluxcache.admin.vo.FluxCacheStaticsSummaryVO;
import com.fluxcache.admin.vo.FluxCacheValueVO;
import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.monitor.FluxCacheStatics;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FluxCacheControllerTest {

    private FluxCacheController controller;
    private FluxCacheManager cacheManager;
    private FluxCacheProperties cacheProperties;

    @Before
    public void setUp() {
        cacheManager = mock(FluxCacheManager.class);
        cacheProperties = mock(FluxCacheProperties.class);
        controller = new FluxCacheController(cacheManager, cacheProperties);
    }

    @Test
    public void getAllStatics_returnsVO() {
        FluxCacheStatics statics = mock(FluxCacheStatics.class);
        when(statics.getWindow()).thenReturn(new java.util.concurrent.ConcurrentLinkedDeque<>());
        when(cacheManager.getCacheStatics("testCache")).thenReturn(statics);

        FluxCacheAllStaticsVO result = controller.getAllStatics("testCache");
        assertNotNull(result);
        assertEquals("testCache", result.getCacheName());
    }

    @Test
    public void staticsSummary_returnsEmptyItems() {
        when(cacheProperties.namespace()).thenReturn("test-ns");
        when(cacheManager.getAllCacheMetaData()).thenReturn(Collections.emptyList());

        FluxCacheStaticsSummaryVO result = controller.staticsSummary();
        assertNotNull(result);
        assertEquals("test-ns", result.getNamespace());
        assertNotNull(result.getItems());
    }

    @Test
    public void staticsSummary_withCacheOps_returnsItems() {
        when(cacheProperties.namespace()).thenReturn("test-ns");
        FluxCacheOperation op = mock(FluxCacheOperation.class);
        when(op.getCacheName()).thenReturn("myCache");
        FluxCacheStatics statics = mock(FluxCacheStatics.class);
        when(statics.getWindow()).thenReturn(new java.util.concurrent.ConcurrentLinkedDeque<>());
        when(cacheManager.getAllCacheMetaData()).thenReturn(Arrays.asList(op));
        when(cacheManager.getCacheStatics("myCache")).thenReturn(statics);

        FluxCacheStaticsSummaryVO result = controller.staticsSummary();
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals("myCache", result.getItems().get(0).getCacheName());
    }

    @Test
    public void getValue_hit_returnsValue() {
        FluxCache cache = mock(FluxCache.class);
        FluxCache.ValueWrapper wrapper = mock(FluxCache.ValueWrapper.class);
        when(wrapper.get()).thenReturn("test-value");
        when(cache.get("test-key")).thenReturn(wrapper);
        when(cacheManager.getCache("testCache")).thenReturn(cache);

        FluxCacheValueVO result = controller.getValue("testCache", "test-key");
        assertNotNull(result);
        assertTrue(result.getFlag());
        assertEquals("test-value", result.getValue());
    }

    @Test
    public void getValue_miss_returnsFalse() {
        when(cacheManager.getCache("nonexistent")).thenReturn(null);

        FluxCacheValueVO result = controller.getValue("nonexistent", "test-key");
        assertNotNull(result);
        assertFalse(result.getFlag());
        assertNull(result.getValue());
    }

    @Test
    public void allCaches_returnsOperationVO() {
        when(cacheProperties.namespace()).thenReturn("test-ns");
        when(cacheManager.getAllCacheMetaData()).thenReturn(Collections.emptyList());

        FluxCacheOperationVO result = controller.evictCache();
        assertNotNull(result);
        assertEquals("test-ns", result.getNamespace());
    }

    @Test
    public void evictCache_delegatesToManager() {
        when(cacheManager.evictCache("testCache", Arrays.asList("key1"))).thenReturn(true);

        boolean result = controller.evictCache("testCache", Arrays.asList("key1"));
        assertTrue(result);
    }

    @Test
    public void clearCache_delegatesToManager() {
        when(cacheManager.clearCacheByName("testCache")).thenReturn(true);

        boolean result = controller.clearCache("testCache");
        assertTrue(result);
    }
}
