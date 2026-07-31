package com.fluxcache.core.preheat;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxCacheFactory;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

/**
 * 刷新执行器测试：单 key 失败隔离、失败保留旧值、ThreadLocal 清理、null 写入策略。
 *
 * @author : wh
 * @date : 2026/7/31
 */
public class FluxCacheRefreshExecutorTest {

    private final CacheSyncStrategy syncStrategy = mock(CacheSyncStrategy.class);
    private final FluxCacheMonitor monitor = mock(FluxCacheMonitor.class);
    private final FluxCacheProperties cacheProperties = new FluxCacheProperties();
    private final FluxCacheRefreshExecutor executor = new FluxCacheRefreshExecutor();

    @Test
    public void refreshSuccess_putsNewValue_andClearsForceContext() throws Exception {
        RefreshTarget target = new RefreshTarget();
        FluxCache<String, String> cache = createCache(true);
        cache.put("k1", "old1");
        cache.put("k2", "old2");

        executor.refresh(buildContext(target, cache, "load", List.of("k1", "k2")));

        assertEquals("v-k1-1", cache.get("k1", String.class));
        assertEquals("v-k2-2", cache.get("k2", String.class));
        assertFalse("刷新线程不应残留强制刷新上下文", FluxForceRefreshContext.isForceRefresh());
    }

    @Test
    public void refreshFailure_preservesOldValue_andContinuesOtherKeys() throws Exception {
        RefreshTarget target = new RefreshTarget();
        target.failKeys.add("k1");
        FluxCache<String, String> cache = createCache(true);
        cache.put("k1", "old1");
        cache.put("k2", "old2");

        executor.refresh(buildContext(target, cache, "load", List.of("k1", "k2")));

        // 失败的 key 保留旧值，其余 key 正常刷新
        assertEquals("old1", cache.get("k1", String.class));
        assertEquals("v-k2-2", cache.get("k2", String.class));
        assertFalse("刷新线程不应残留强制刷新上下文", FluxForceRefreshContext.isForceRefresh());
    }

    @Test
    public void refreshNullResult_skipsWrite_whenNullNotAllowed() throws Exception {
        RefreshTarget target = new RefreshTarget();
        target.returnNull = true;
        FluxCache<String, String> cache = createCache(false);
        cache.put("k1", "old1");

        executor.refresh(buildContext(target, cache, "load", List.of("k1")));

        // 不允许缓存 null：跳过写入，保留旧值
        assertEquals("old1", cache.get("k1", String.class));
    }

    @Test
    public void refreshNullResult_cached_whenNullAllowed() throws Exception {
        RefreshTarget target = new RefreshTarget();
        target.returnNull = true;
        FluxCache<String, String> cache = createCache(true);
        cache.put("k1", "old1");

        executor.refresh(buildContext(target, cache, "load", List.of("k1")));

        // 允许缓存 null：刷新后的值为 null（穿透保护）
        assertNull(cache.get("k1", String.class));
    }

    @Test
    public void refreshMultiArgMethod_skipsKeyWithoutAborting() throws Exception {
        RefreshTarget target = new RefreshTarget();
        FluxCache<String, String> cache = createCache(true);
        cache.put("k1", "old1");

        executor.refresh(buildContext(target, cache, "multi", List.of("k1")));

        // 不支持的签名仅记录日志，不影响其他逻辑
        assertEquals("old1", cache.get("k1", String.class));
        assertFalse(FluxForceRefreshContext.isForceRefresh());
    }

    private FluxCacheRefreshContext buildContext(RefreshTarget target, FluxCache cache, String methodName, List<String> keys)
            throws NoSuchMethodException {
        Method method = "multi".equals(methodName)
                ? target.getClass().getMethod("multi", String.class, String.class)
                : target.getClass().getMethod("load", String.class);
        return FluxCacheRefreshContext.builder()
                .bean(target)
                .method(method)
                .cacheName("refresh-test")
                .cache(cache)
                .keys(keys)
                .build();
    }

    private FluxCache<String, String> createCache(boolean allowNull) {
        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setFirstCacheConfig(caffeineConfig())
                .setAllowNullValues(allowNull)
                .setCacheName("refresh-test")
                .setMethodName("load")
                .setKey("#key")
                .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
                .build();
        @SuppressWarnings("unchecked")
        FluxCache<String, String> cache = (FluxCache<String, String>) FluxCacheFactory.withDefaults()
                .createFluxCache(op, cacheProperties, syncStrategy, monitor);
        return cache;
    }

    private static FluxCacheConfig caffeineConfig() {
        return new FluxCacheConfig.Builder()
                .setTtl(5L)
                .setInitSize(16)
                .setMaxSize(100)
                .setUnit(TimeUnit.MINUTES)
                .setCacheType(FluxCacheType.CAFFEINE)
                .build();
    }

    public static class RefreshTarget {

        private final AtomicInteger calls = new AtomicInteger();

        private final java.util.Set<String> failKeys = new java.util.HashSet<>();

        private boolean returnNull;

        public String load(String key) {
            calls.incrementAndGet();
            if (failKeys.contains(key)) {
                throw new IllegalStateException("boom-" + key);
            }
            if (returnNull) {
                return null;
            }
            return "v-" + key + "-" + calls.get();
        }

        public String multi(String a, String b) {
            return "x";
        }
    }
}
