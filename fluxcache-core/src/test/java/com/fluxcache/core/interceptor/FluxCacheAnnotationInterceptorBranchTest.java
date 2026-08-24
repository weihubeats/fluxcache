package com.fluxcache.core.interceptor;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.FluxCacheEvictOperation;
import com.fluxcache.core.model.FluxCachePutOperation;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.preheat.FluxForceRefreshContext;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 拦截器分支补齐：命中/未命中、强制刷新、null/Optional 策略、Evict/Put 操作、SpEL 容错。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheAnnotationInterceptorBranchTest {

    private FluxCacheProperties properties;
    private FluxCache<Object, Object> cache;
    private FluxCacheManager cacheManager;
    private FluxCacheMonitor monitor;
    private FluxCacheAnnotationInterceptor interceptor;
    private FluxCacheOperationSource opSource;
    private Method loadMethod;
    private Object target;

    @Before
    public void setUp() throws Exception {
        properties = new FluxCacheProperties();
        cache = mock(FluxCache.class);
        doReturn("branch-cache").when(cache).getName();
        cacheManager = mock(FluxCacheManager.class);
        doReturn(cache).when(cacheManager).getCache("branch-cache");
        monitor = mock(FluxCacheMonitor.class);
        opSource = mock(FluxCacheOperationSource.class);
        loadMethod = Target.class.getMethod("load", String.class);
        target = new Target();
        interceptor = new FluxCacheAnnotationInterceptor(properties, opSource, cacheManager, monitor);
    }

    private com.fluxcache.core.model.FluxCacheOperation cacheableOp(String key) {
        return new com.fluxcache.core.model.FluxMultilevelCacheCacheable.Builder()
                .setCacheName("branch-cache")
                .setMethodName("load")
                .setKey(key)
                .build();
    }

    private MethodInvocation invocation(FluxCacheOperationInvoker invoker, Object... args) {
        return new MethodInvocation() {
            @Override
            public Method getMethod() {
                return loadMethod;
            }

            @Override
            public Object[] getArguments() {
                return args;
            }

            @Override
            public Object getThis() {
                return target;
            }

            @Override
            public Object proceed() throws Throwable {
                return invoker.invoke();
            }

            @Override
            public java.lang.reflect.AccessibleObject getStaticPart() {
                return loadMethod;
            }
        };
    }

    @Test
    public void hit_returnsCachedValue() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        doReturn((FluxCache.ValueWrapper<Object>) () -> "cached").when(cache).get("k1");

        Object result = interceptor.invoke(invocation(() -> "loaded", "k1"));

        assertEquals("cached", result);
        verify(monitor).publishMonitorEvent(argEvent(com.fluxcache.core.monitor.MonitorEventEnum.CACHE_HIT));
    }

    @Test
    public void hitNull_notAllowed_reloads() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        properties.setAllowCacheNull(false);
        AtomicInteger loads = new AtomicInteger();
        doReturn((FluxCache.ValueWrapper<Object>) () -> null).when(cache).get("k1");

        Object result = interceptor.invoke(invocation(() -> {
            loads.incrementAndGet();
            return "loaded";
        }, "k1"));

        assertEquals("loaded", result);
        assertEquals(1, loads.get());
    }

    @Test
    public void hitOptionalEmpty_notAllowed_reloads() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        properties.setAllowCacheEmptyOptional(false);
        doReturn((FluxCache.ValueWrapper<Object>) Optional::empty).when(cache).get("k1");

        Object result = interceptor.invoke(invocation(() -> "fresh", "k1"));

        assertEquals("fresh", result);
    }

    @Test
    public void forceRefresh_skipsReadAndPuts() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        AtomicInteger loads = new AtomicInteger();

        Object result = FluxForceRefreshContext.callWithForce(() -> {
            try {
                return interceptor.invoke(invocation(() -> {
                    loads.incrementAndGet();
                    return "forced";
                }, "k1"));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("forced", result);
        assertEquals(1, loads.get());
        verify(cache).put("k1", "forced");
    }

    @Test
    public void safeGetThrows_fallsBackToLoad() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        when(cache.get(any())).thenThrow(new IllegalStateException("cache-down"));

        Object result = interceptor.invoke(invocation(() -> "recovered", "k1"));

        assertEquals("recovered", result);
    }

    @Test
    public void loaderException_rethrownThroughWrapper() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));

        try {
            interceptor.invoke(invocation(() -> {
                throw new IllegalStateException("loader-boom");
            }, "k1"));
            fail("应抛出原异常");
        } catch (IllegalStateException expected) {
            assertEquals("loader-boom", expected.getMessage());
        }
    }

    @Test
    public void nullOperationSource_passesThrough() throws Throwable {
        FluxCacheAnnotationInterceptor plain = new FluxCacheAnnotationInterceptor(properties, null, cacheManager, monitor);
        assertEquals("direct", plain.invoke(invocation(() -> "direct", "k1")));
    }

    @Test
    public void unknownOperation_passesThrough() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(null);
        assertEquals("direct", interceptor.invoke(invocation(() -> "direct", "k1")));
    }

    @Test
    public void missingCache_passesThrough() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        doReturn(null).when(cacheManager).getCache("branch-cache");
        assertEquals("direct", interceptor.invoke(invocation(() -> "direct", "k1")));
    }

    @Test
    public void evictOperation_withKey_evicts() throws Throwable {
        FluxCacheEvictOperation evictOp = (FluxCacheEvictOperation) new FluxCacheEvictOperation.Builder()
                .setCacheName("branch-cache")
                .setMethodName("load")
                .setKey("#name")
                .build();
        when(opSource.getCacheOperation(any(), any())).thenReturn(evictOp);

        Object result = interceptor.invoke(invocation(() -> "done", "k1"));

        assertEquals("done", result);
        verify(cache).evict("k1");
    }

    @Test
    public void evictOperation_emptyKey_clearsAll() throws Throwable {
        FluxCacheEvictOperation evictOp = (FluxCacheEvictOperation) new FluxCacheEvictOperation.Builder()
                .setCacheName("branch-cache")
                .setMethodName("load")
                .setKey("")
                .build();
        when(opSource.getCacheOperation(any(), any())).thenReturn(evictOp);

        interceptor.invoke(invocation(() -> "done"));

        verify(cache).clear();
    }

    @Test
    public void putOperation_putsAndReturns() throws Throwable {
        FluxCachePutOperation putOp = (FluxCachePutOperation) new FluxCachePutOperation.Builder()
                .setCacheName("branch-cache")
                .setMethodName("load")
                .setKey("#name")
                .build();
        when(opSource.getCacheOperation(any(), any())).thenReturn(putOp);

        Object result = interceptor.invoke(invocation(() -> "value", "k1"));

        assertEquals("value", result);
        verify(cache).put("k1", "value");
    }

    @Test
    public void putOperation_nullNotAllowed_skipsPut() throws Throwable {
        FluxCachePutOperation putOp = (FluxCachePutOperation) new FluxCachePutOperation.Builder()
                .setCacheName("branch-cache")
                .setMethodName("load")
                .setKey("#name")
                .build();
        when(opSource.getCacheOperation(any(), any())).thenReturn(putOp);
        properties.setAllowCacheNull(false);

        interceptor.invoke(invocation(() -> null, "k1"));

        verify(cache, org.mockito.Mockito.never()).put(any(), any());
    }

    @Test
    public void cacheable_nullResult_notAllowed_skipsPut() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        properties.setAllowCacheNull(false);

        Object result = interceptor.invoke(invocation(() -> null, "k1"));

        assertNull(result);
        verify(cache, org.mockito.Mockito.never()).put(any(), any());
    }

    @Test
    public void optionalReturn_adaptedOnCacheHit() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        doReturn((FluxCache.ValueWrapper<Object>) () -> "raw").when(cache).get("k1");
        loadMethod = Target.class.getMethod("optionalLoad", String.class);
        // 重绑定
        when(opSource.getCacheOperation(loadMethod, Target.class)).thenReturn(cacheableOp("#name"));

        Object result = interceptor.invoke(invocation(() -> Optional.of("raw"), "k1"));

        assertTrue(result instanceof Optional);
        assertEquals("raw", ((Optional<?>) result).get());
    }

    @Test
    public void spelExpressionFailure_failsFast() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#bogus[0]"));
        AtomicInteger loads = new AtomicInteger();

        try {
            interceptor.invoke(invocation(() -> {
                loads.incrementAndGet();
                return "v";
            }, "k1"));
            org.junit.Assert.fail("expected IllegalStateException for broken SpEL key");
        } catch (IllegalStateException expected) {
            // fail fast: a shared fallback key would poison data across callers
        }

        assertEquals(0, loads.get());
    }

    @Test
    public void emptyKeyExpression_failsFast() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp(""));
        AtomicInteger loads = new AtomicInteger();

        try {
            interceptor.invoke(invocation(() -> {
                loads.incrementAndGet();
                return "v";
            }, "k1"));
            org.junit.Assert.fail("expected IllegalStateException for blank cache key");
        } catch (IllegalStateException expected) {
            // fail fast before the method executes
        }

        assertEquals(0, loads.get());
    }

    @Test
    public void singleFlightDisabled_directLoad() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        properties.setSingleFlightEnable(false);
        AtomicInteger loads = new AtomicInteger();

        Object result = interceptor.invoke(invocation(() -> {
            loads.incrementAndGet();
            return "direct";
        }, "k1"));

        assertEquals("direct", result);
        assertEquals(1, loads.get());
        verify(cache).put("k1", "direct");
    }

    @Test
    public void singleFlight_waiterReusesLeaderResult() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        CompletableFuture<Object> leader = new CompletableFuture<>();
        leader.complete("leader-value");
        seedSingleFlight(flightKey(), leader);
        AtomicInteger loads = new AtomicInteger();

        Object result = interceptor.invoke(invocation(() -> {
            loads.incrementAndGet();
            return "own-value";
        }, "k1"));

        assertEquals("leader-value", result);
        assertEquals(0, loads.get());
    }

    @Test
    public void singleFlight_waiterFailure_fallsBackToOwnLoad() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        CompletableFuture<Object> leader = new CompletableFuture<>();
        leader.completeExceptionally(new IllegalStateException("leader-down"));
        seedSingleFlight(flightKey(), leader);

        Object result = interceptor.invoke(invocation(() -> "own-value", "k1"));

        assertEquals("own-value", result);
        verify(cache).put("k1", "own-value");
    }

    @Test
    public void singleFlight_timeout_fallsBackToOwnLoad() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        CompletableFuture<Object> leader = new CompletableFuture<>();
        seedSingleFlight(flightKey(), leader);
        properties.setSingleFlightTimeoutMillis(1);

        Object result = interceptor.invoke(invocation(() -> "own-value", "k1"));

        assertEquals("own-value", result);
        verify(cache).put("k1", "own-value");
    }

    @Test
    public void hitOptionalPresent_notEmpty_allowed() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        doReturn((FluxCache.ValueWrapper<Object>) () -> Optional.of("inner")).when(cache).get("k1");

        Object result = interceptor.invoke(invocation(() -> "loaded", "k1"));

        assertEquals(Optional.of("inner"), result);
    }

    @Test
    public void hitOptionalEmpty_allowed_returnsEmpty() throws Throwable {
        when(opSource.getCacheOperation(any(), any())).thenReturn(cacheableOp("#name"));
        properties.setAllowCacheEmptyOptional(true);
        doReturn((FluxCache.ValueWrapper<Object>) Optional::empty).when(cache).get("k1");

        Object result = interceptor.invoke(invocation(() -> "loaded", "k1"));

        assertEquals(Optional.empty(), result);
        verify(monitor).publishMonitorEvent(argEvent(com.fluxcache.core.monitor.MonitorEventEnum.CACHE_HIT));
    }

    private com.fluxcache.core.monitor.FluxCacheMonitorEvent argEvent(com.fluxcache.core.monitor.MonitorEventEnum type) {
        return org.mockito.ArgumentMatchers.argThat(
                e -> e != null && e.getMonitorEventEnum() == type);
    }

    private void seedSingleFlight(String key, CompletableFuture<Object> future) throws Exception {
        java.lang.reflect.Field f = FluxCacheAnnotationInterceptor.class.getDeclaredField("singleFlightMap");
        f.setAccessible(true);
        ((java.util.Map<String, CompletableFuture<Object>>) f.get(interceptor)).put(key, future);
    }

    private String flightKey() throws Exception {
        // flightKey = cacheName + "::" + method.toGenericString() + "::" + key
        java.lang.reflect.Method m = Target.class.getMethod("load", String.class);
        return "branch-cache::" + m.toGenericString() + "::k1";
    }

    public static class Target {

        public String load(String name) {
            return "loaded";
        }

        public Optional<String> optionalLoad(String name) {
            return Optional.of(name);
        }
    }
}