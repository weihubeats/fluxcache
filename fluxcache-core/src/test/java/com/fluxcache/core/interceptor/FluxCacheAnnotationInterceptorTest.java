package com.fluxcache.core.interceptor;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxCacheFactory;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 单飞(single-flight)防缓存击穿测试。
 *
 * @author : wh
 * @date : 2026/7/31
 */
public class FluxCacheAnnotationInterceptorTest {

    private static final String CACHE_NAME = "single-flight-test";

    private FluxCacheProperties cacheProperties;
    private FluxCache<String, String> cache;
    private FluxCacheAnnotationInterceptor interceptor;
    private Method loadMethod;
    private Object target;

    @Before
    public void setUp() throws Exception {
        cacheProperties = new FluxCacheProperties();
        CacheSyncStrategy syncStrategy = mock(CacheSyncStrategy.class);
        FluxCacheMonitor monitor = mock(FluxCacheMonitor.class);

        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setFirstCacheConfig(caffeineConfig(5L, 100))
                .setAllowNullValues(true)
                .setCacheName(CACHE_NAME)
                .setMethodName("getByName")
                .setKey("#name")
                .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
                .build();

        @SuppressWarnings("unchecked")
        FluxCache<String, String> created = (FluxCache<String, String>) FluxCacheFactory.withDefaults()
                .createFluxCache(op, cacheProperties, syncStrategy, monitor);
        cache = created;

        FluxCacheManager cacheManager = mock(FluxCacheManager.class);
        doReturn(cache).when(cacheManager).getCache(CACHE_NAME);

        FluxCacheOperationSource operationSource = mock(FluxCacheOperationSource.class);
        target = new Target();
        loadMethod = Target.class.getMethod("getByName", String.class);
        when(operationSource.getCacheOperation(loadMethod, Target.class)).thenReturn(op);

        interceptor = new FluxCacheAnnotationInterceptor(cacheProperties, operationSource, cacheManager, monitor);
    }

    @Test
    public void concurrentMiss_sameKey_onlyOneLoaderInvoked() throws Exception {
        AtomicInteger loaderCalls = new AtomicInteger();
        FluxCacheOperationInvoker slowLoader = () -> {
            loaderCalls.incrementAndGet();
            sleepQuietly(300);
            return "value-1";
        };

        List<Object> results = runConcurrently(8, slowLoader, "k1");

        assertEquals(1, loaderCalls.get());
        results.forEach(r -> assertEquals("value-1", r));
        assertEquals("value-1", cache.get("k1", String.class));
    }

    @Test
    public void concurrentMiss_differentKeys_loadIndependently() throws Exception {
        AtomicInteger loaderCalls = new AtomicInteger();
        FluxCacheOperationInvoker loader = () -> {
            loaderCalls.incrementAndGet();
            sleepQuietly(200);
            return "value";
        };

        List<Object> results = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String key = "k" + (i % 4);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return interceptor.invoke(buildMethodInvocation(loader, key));
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            for (Future<Object> f : futures) {
                results.add(f.get(5, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        // 4 个不同的 key 各加载一次
        assertEquals(4, loaderCalls.get());
        assertEquals(8, results.size());
    }

    @Test
    public void leaderFailure_waitersAlsoFail_nothingCached() throws Exception {
        FluxCacheOperationInvoker failingLoader = () -> {
            throw new IllegalStateException("boom");
        };

        List<Object> results = runConcurrently(2, failingLoader, "k1");

        for (Object r : results) {
            assertNotNull(r);
            assertTrue(r instanceof IllegalStateException);
            assertEquals("boom", ((IllegalStateException) r).getMessage());
        }
        // 失败不落缓存
        assertEquals(null, cache.get("k1", String.class));
    }

    @Test
    public void waitTimeout_fallbackToSelfLoad() throws Exception {
        cacheProperties.setSingleFlightTimeoutMillis(50L);
        AtomicInteger loaderCalls = new AtomicInteger();
        FluxCacheOperationInvoker slowLoader = () -> {
            loaderCalls.incrementAndGet();
            sleepQuietly(500);
            return "slow-" + loaderCalls.get();
        };

        List<Object> results = runConcurrently(2, slowLoader, "k1");

        // leader 加载 + 等待线程超时后自行加载
        assertEquals(2, loaderCalls.get());
        results.forEach(r -> assertNotNull(r));
    }

    @Test
    public void disabledByProperty_eachThreadLoadsItself() throws Exception {
        cacheProperties.setSingleFlightEnable(false);
        AtomicInteger loaderCalls = new AtomicInteger();
        FluxCacheOperationInvoker loader = () -> {
            loaderCalls.incrementAndGet();
            sleepQuietly(200);
            return "value";
        };

        runConcurrently(4, loader, "k1");

        assertEquals(4, loaderCalls.get());
    }

    private List<Object> runConcurrently(int threads,
                                         FluxCacheOperationInvoker invoker,
                                         String key) throws Exception {
        List<Object> results = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return interceptor.invoke(buildMethodInvocation(invoker, key));
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            for (Future<Object> f : futures) {
                results.add(f.get(5, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        return results;
    }

    private MethodInvocation buildMethodInvocation(FluxCacheOperationInvoker invoker, String key) {
        return new MethodInvocation() {
            @Override
            public Method getMethod() {
                return loadMethod;
            }

            @Override
            public Object[] getArguments() {
                return new Object[]{key};
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

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    public static class Target {

        public String getByName(String name) {
            fail("target 方法不应被直接调用，加载逻辑在 invoker 中");
            return null;
        }
    }
}
