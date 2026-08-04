package com.fluxcache.core.manual;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 手动注册后处理：注册的缓存配置逐一创建缓存并初始化监控统计。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheCreatePostProcessTest {

    private final FluxCacheManager cacheManager = mock(FluxCacheManager.class);
    private final FluxCacheMonitor cacheMonitor = mock(FluxCacheMonitor.class);
    private final FluxCacheProperties properties = new FluxCacheProperties();

    private FluxMultilevelCacheCacheable operation(String cacheName) {
        return (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setCacheName(cacheName)
                .setMethodName("load")
                .setKey("#k")
                .build();
    }

    @Test
    public void afterSingletonsInstantiated_createsAllCaches() {
        FluxCacheDataRegistered registered = () -> List.of(operation("a"), operation("b"));
        FluxCacheCreatePostProcess postProcess =
                new FluxCacheCreatePostProcess(registered, cacheManager, properties, cacheMonitor);

        postProcess.afterSingletonsInstantiated();

        verify(cacheManager, org.mockito.Mockito.times(2)).createCache(any());
        verify(cacheMonitor).createNewCacheStatics("a");
        verify(cacheMonitor).createNewCacheStatics("b");
    }

    @Test
    public void afterSingletonsInstantiated_emptyList_noCreation() {
        FluxCacheDataRegistered registered = Collections::emptyList;
        FluxCacheCreatePostProcess postProcess =
                new FluxCacheCreatePostProcess(registered, cacheManager, properties, cacheMonitor);

        postProcess.afterSingletonsInstantiated();

        verify(cacheManager, never()).createCache(any());
    }

    @Test
    public void afterSingletonsInstantiated_nullRegistered_noop() {
        FluxCacheCreatePostProcess postProcess =
                new FluxCacheCreatePostProcess(null, cacheManager, properties, cacheMonitor);

        postProcess.afterSingletonsInstantiated();

        verify(cacheManager, never()).createCache(any());
    }

    @Test
    public void afterSingletonsInstantiated_monitorDisabled_noStatics() {
        properties.setCacheMonitorEnable(false);
        FluxCacheDataRegistered registered = () -> List.of(operation("a"));
        FluxCacheCreatePostProcess postProcess =
                new FluxCacheCreatePostProcess(registered, cacheManager, properties, cacheMonitor);

        postProcess.afterSingletonsInstantiated();

        verify(cacheManager).createCache(any());
        verify(cacheMonitor, never()).createNewCacheStatics(any());
    }
}