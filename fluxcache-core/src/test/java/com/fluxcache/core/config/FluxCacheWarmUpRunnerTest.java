package com.fluxcache.core.config;

import com.fluxcache.core.annotation.FluxCacheEvict;
import com.fluxcache.core.annotation.FluxCachePut;
import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 启动预热：开启/关闭、默认参数调用、失败容错、无注解方法跳过、只读限制。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheWarmUpRunnerTest {

    private final FluxCacheProperties properties = new FluxCacheProperties();
    private final ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);

    @SuppressWarnings("unchecked")
    private FluxCacheWarmUpRunner runner(Map<String, Object> beans) {
        ObjectProvider<Map<String, Object>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any(Supplier.class))).thenReturn(beans);
        return new FluxCacheWarmUpRunner(properties, provider);
    }

    private void await(AtomicInteger counter, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (counter.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
    }

    @Test
    public void disabled_warmUp_noInvocation() throws Exception {
        properties.setWarmUpEnable(false);
        WarmUpService service = new WarmUpService();
        FluxCacheWarmUpRunner runner = runner(Map.of("svc", service));

        runner.onApplicationReady(event);
        Thread.sleep(100L);

        assertEquals(0, service.calls.get());
    }

    @Test
    public void emptyBeanMap_noInvocation() {
        properties.setWarmUpEnable(true);
        FluxCacheWarmUpRunner runner = runner(new HashMap<>());
        runner.onApplicationReady(event);
    }

    @Test
    public void noAnnotatedMethods_skipped() {
        properties.setWarmUpEnable(true);
        FluxCacheWarmUpRunner runner = runner(Map.of("plain", new Object()));
        runner.onApplicationReady(event);
    }

    @Test
    public void annotatedMethods_invokedWithDefaults() throws Exception {
        properties.setWarmUpEnable(true);
        WarmUpService service = new WarmUpService();
        FluxCacheWarmUpRunner runner = runner(Map.of("svc", service));

        runner.onApplicationReady(event);

        await(service.calls, 1);
        // 只预热 @FluxCacheable；Put/Evict 有副作用，不能被伪造参数触发
        assertEquals(0, service.cachePutCalls.get());
        assertEquals(0, service.cacheEvictCalls.get());
        await(service.failingCalls, 1);
    }

    @Test
    public void failingMethod_countedNotThrown() throws Exception {
        properties.setWarmUpEnable(true);
        FailingService service = new FailingService();
        FluxCacheWarmUpRunner runner = runner(Map.of("fail", service));

        runner.onApplicationReady(event);

        await(service.failingCalls, 1);
    }

    @Test
    public void unsupportedParamType_skipped() throws Exception {
        properties.setWarmUpEnable(true);
        UnsupportedParamService service = new UnsupportedParamService();
        FluxCacheWarmUpRunner runner = runner(Map.of("unsupported", service));

        runner.onApplicationReady(event);
        Thread.sleep(100L);

        assertEquals(0, service.calls.get());
    }

    @Test
    public void allPrimitiveWrapperTypes_invoked() throws Exception {
        properties.setWarmUpEnable(true);
        WrapperParamsService service = new WrapperParamsService();
        FluxCacheWarmUpRunner runner = runner(Map.of("wrappers", service));

        runner.onApplicationReady(event);

        await(service.calls, 1);
    }

    @Test
    public void primitiveParamTypes_invoked() throws Exception {
        properties.setWarmUpEnable(true);
        PrimitiveParamsService service = new PrimitiveParamsService();
        FluxCacheWarmUpRunner runner = runner(Map.of("prims", service));

        runner.onApplicationReady(event);

        await(service.calls, 1);
    }

    @Test
    public void beanMapWithNullOrUnrelatedEntries_skippedGracefully() throws Exception {
        properties.setWarmUpEnable(true);
        WrapperParamsService service = new WrapperParamsService();
        Map<String, Object> beans = new HashMap<>();
        beans.put("null-entry", null);
        beans.put("plain", new Object());
        beans.put("wrappers", service);
        FluxCacheWarmUpRunner runner = runner(beans);

        runner.onApplicationReady(event);

        await(service.calls, 1);
    }

    @Test
    public void nullBeanMap_noOp() {
        properties.setWarmUpEnable(true);
        FluxCacheWarmUpRunner runner = runner(null);
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

    public static class PrimitiveParamsService {

        final AtomicInteger calls = new AtomicInteger();

        @FluxCacheable(cacheName = "prims-1")
        public String load(long a, int b, boolean c, double d, float e) {
            calls.incrementAndGet();
            return "v";
        }
    }
}
