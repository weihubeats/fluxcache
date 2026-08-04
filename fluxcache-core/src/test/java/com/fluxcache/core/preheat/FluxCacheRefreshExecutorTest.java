package com.fluxcache.core.preheat;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.annotation.FluxRefresh;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 刷新执行器：逐 key 加载、跳过空值、异常保留旧值、强制刷新上下文清理。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheRefreshExecutorTest {

    private FluxCacheRefreshExecutor executor;
    private LoadService service;
    private Method loadMethod;
    private FluxCache<Object, Object> cache;

    @Before
    public void setUp() throws Exception {
        executor = new FluxCacheRefreshExecutor();
        service = new LoadService();
        loadMethod = LoadService.class.getMethod("load", String.class);
        cache = mock(FluxCache.class);
    }

    private FluxCacheRefreshContext context(FluxCache<Object, Object> c, Object... keys) {
        return FluxCacheRefreshContext.builder()
                .bean(service)
                .method(loadMethod)
                .cacheName("refresh-executor")
                .refreshConfig(mock(FluxRefresh.class))
                .cache(c)
                .keys(Arrays.asList(keys))
                .build();
    }

    @Test
    public void refresh_loadsAndPutsEachKey() {
        when(cache.allowCacheNull()).thenReturn(true);
        executor.refresh(context(cache, "a", "b"));

        assertEquals(List.of("a", "b"), service.seen);
        verify(cache, times(2)).put(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertFalse(FluxForceRefreshContext.isForceRefresh()); // 线程上下文被清理
    }

    @Test
    public void refresh_nullCache_skips() {
        executor.refresh(context(null, "a"));
        verify(cache, never()).put(any(), any());
    }

    @Test
    public void refresh_nullResult_skippedWhenNotAllowed() {
        when(cache.allowCacheNull()).thenReturn(false);
        service.nullForKey = true;
        executor.refresh(context(cache, "a"));
        verify(cache, never()).put(any(), any());
    }

    @Test
    public void refresh_nullResult_keptWhenAllowed() {
        when(cache.allowCacheNull()).thenReturn(true);
        service.nullForKey = true;
        executor.refresh(context(cache, "a"));
        verify(cache).put("a", null);
    }

    @Test
    public void refresh_multiParamMethod_throwsIllegalState() throws Exception {
        Method multi = LoadService.class.getMethod("load", String.class, String.class);
        FluxCacheRefreshContext ctx = FluxCacheRefreshContext.builder()
                .bean(service)
                .method(multi)
                .cacheName("multi")
                .refreshConfig(mock(FluxRefresh.class))
                .cache(cache)
                .keys(List.of("a"))
                .build();
        when(cache.allowCacheNull()).thenReturn(true);

        executor.refresh(ctx);
        // 多参数方法调用抛 InvalidStateException，被捕获记录保留旧值
        verify(cache, never()).put(any(), any());
    }

    @Test
    public void refresh_zeroArgMethod_works() throws Exception {
        Method zero = LoadService.class.getMethod("noArg");
        FluxCacheRefreshContext ctx = FluxCacheRefreshContext.builder()
                .bean(service)
                .method(zero)
                .cacheName("zero")
                .refreshConfig(mock(FluxRefresh.class))
                .cache(cache)
                .keys(List.of("k"))
                .build();
        when(cache.allowCacheNull()).thenReturn(true);

        executor.refresh(ctx);

        assertEquals(1, service.noArgCalls);
        verify(cache).put("k", "noarg");
    }

    @Test
    public void forceContext_runAndCallHelpers() {
        AtomicBoolean inside = new AtomicBoolean(false);
        FluxForceRefreshContext.runWithForce(() -> inside.set(FluxForceRefreshContext.isForceRefresh()));
        assertTrue(inside.get());
        assertFalse(FluxForceRefreshContext.isForceRefresh());

        String result = FluxForceRefreshContext.callWithForce(
                () -> FluxForceRefreshContext.isForceRefresh() ? "forced" : "not");
        assertEquals("forced", result);
        assertFalse(FluxForceRefreshContext.isForceRefresh());
    }

    @Test
    public void forceContext_callWithRuntime_keepsOriginal() {
        try {
            FluxForceRefreshContext.callWithForce(() -> {
                throw new IllegalStateException("inner");
            });
            fail("应抛出原异常");
        } catch (IllegalStateException expected) {
            assertEquals("inner", expected.getMessage());
        }
    }

    @Test
    public void forceContext_callWithChecked_wrapped() {
        try {
            FluxForceRefreshContext.callWithForce(() -> {
                throw new Exception("checked");
            });
            fail("应抛出 RuntimeException");
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause() != null);
        }
    }

    @Test
    public void preheatDataProviderNone_returnsNull() {
        assertNull(new FluxPreheatDataProvider.None<Object>().getPreheatData());
    }

    public static class LoadService {

        final List<String> seen = new java.util.ArrayList<>();
        boolean nullForKey;
        int noArgCalls;

        public String load(String key) {
            seen.add(key);
            if (nullForKey) {
                return null;
            }
            return "value";
        }

        public String load(String k1, String k2) {
            return "v";
        }

        public String noArg() {
            noArgCalls++;
            return "noarg";
        }
    }
}