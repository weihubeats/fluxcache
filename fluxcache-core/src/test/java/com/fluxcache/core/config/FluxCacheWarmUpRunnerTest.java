package com.fluxcache.core.config;

import com.fluxcache.core.annotation.FluxCacheEvict;
import com.fluxcache.core.annotation.FluxCachePut;
import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 启动预热：开启/关闭、默认参数调用、失败容错、无注解方法跳过。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheWarmUpRunnerTest {

    private final FluxCacheProperties properties = new FluxCacheProperties();
    private final ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);

    @Test
    public void disabled_warmUp_noInvocation() {
        properties.setWarmUpEnable(false);
        WarmUpService service = new WarmUpService();
        FluxCacheWarmUpRunner runner = new FluxCacheWarmUpRunner(properties, Map.of("svc", service));

        runner.onApplicationReady(event);

        assertEquals(0, service.calls.get());
    }

    @Test
    public void emptyBeanMap_noInvocation() {
        properties.setWarmUpEnable(true);
        FluxCacheWarmUpRunner runner = new FluxCacheWarmUpRunner(properties, new HashMap<>());
        runner.onApplicationReady(event);
    }

    @Test
    public void noAnnotatedMethods_skipped() {
        properties.setWarmUpEnable(true);
        FluxCacheWarmUpRunner runner = new FluxCacheWarmUpRunner(properties, Map.of("plain", new Object()));
        runner.onApplicationReady(event);
    }

    @Test
    public void annotatedMethods_invokedWithDefaults() {
        properties.setWarmUpEnable(true);
        WarmUpService service = new WarmUpService();
        FluxCacheWarmUpRunner runner = new FluxCacheWarmUpRunner(properties, Map.of("svc", service));

        runner.onApplicationReady(event);

        assertEquals(1, service.calls.get());
        assertEquals(1, service.cachePutCalls.get());
        assertEquals(1, service.cacheEvictCalls.get());
        assertEquals(1, service.failingCalls.get());
        assertEquals(1, service.lastMultiArgs[0]);
        assertEquals(1L, service.lastMultiArgs[1]);
        assertEquals(false, service.lastMultiArgs[2]);
    }

    @Test
    public void failingMethod_countedNotThrown() {
        properties.setWarmUpEnable(true);
        FailingService service = new FailingService();
        FluxCacheWarmUpRunner runner = new FluxCacheWarmUpRunner(properties, Map.of("fail", service));

        runner.onApplicationReady(event);

        assertEquals(1, service.failingCalls.get());
    }

    @Test
    public void unsupportedParamType_skipped() {
        properties.setWarmUpEnable(true);
        UnsupportedParamService service = new UnsupportedParamService();
        FluxCacheWarmUpRunner runner = new FluxCacheWarmUpRunner(properties, Map.of("unsupported", service));

        runner.onApplicationReady(event);

        assertEquals(0, service.calls.get());
    }

    @Test
    public void allPrimitiveWrapperTypes_invoked() {
        properties.setWarmUpEnable(true);
        WrapperParamsService service = new WrapperParamsService();
        FluxCacheWarmUpRunner runner = new FluxCacheWarmUpRunner(properties, Map.of("wrappers", service));

        runner.onApplicationReady(event);

        assertEquals(1, service.calls.get());
    }

    @Test
    public void nullBeanMap_noOp() {
        properties.setWarmUpEnable(true);
        FluxCacheWarmUpRunner runner = new FluxCacheWarmUpRunner(properties, null);
        runner.onApplicationReady(event);
    }

    // ---------- fixtures ----------

    public static class WarmUpService {

        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger cachePutCalls = new AtomicInteger();
        final AtomicInteger cacheEvictCalls = new AtomicInteger();
        final AtomicInteger failingCalls = new AtomicInteger();
        final Object[] lastMultiArgs = new Object[3];

        @FluxCacheable(cacheName = "warm-1", key = "#key")
        public String load(String key) {
            calls.incrementAndGet();
            return "v";
        }

        @FluxCachePut(cacheName = "warm-2")
        public String put(int a, long b, boolean c) {
            cachePutCalls.incrementAndGet();
            lastMultiArgs[0] = a;
            lastMultiArgs[1] = b;
            lastMultiArgs[2] = c;
            return "v";
        }

        @FluxCacheEvict(cacheName = "warm-3")
        public void evict() {
            cacheEvictCalls.incrementAndGet();
        }

        @FluxCacheable(cacheName = "warm-4")
        public String failing() {
            failingCalls.incrementAndGet();
            throw new IllegalStateException("boom");
        }
    }

    public static class FailingService {

        final AtomicInteger failingCalls = new AtomicInteger();

        @FluxCacheable(cacheName = "fail-1")
        public String load(String key) {
            failingCalls.incrementAndGet();
            throw new IllegalStateException("boom");
        }
    }

    public static class UnsupportedParamService {

        final AtomicInteger calls = new AtomicInteger();

        @FluxCacheable(cacheName = "unsupported-1")
        public String load(Object weird) {
            calls.incrementAndGet();
            return "v";
        }
    }

    public static class WrapperParamsService {

        final AtomicInteger calls = new AtomicInteger();

        @FluxCacheable(cacheName = "wrappers-1")
        public String load(Long a, Integer b, Boolean c, Double d, Float e) {
            calls.incrementAndGet();
            return "v";
        }
    }
}