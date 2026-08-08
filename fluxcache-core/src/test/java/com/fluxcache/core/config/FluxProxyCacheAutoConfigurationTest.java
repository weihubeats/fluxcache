package com.fluxcache.core.config;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.interceptor.FluxAnnotationCacheOperationSource;
import com.fluxcache.core.interceptor.FluxCacheOperationSource;
import com.fluxcache.core.lock.FluxDistributedLock;
import com.fluxcache.core.manual.FluxCacheCreatePostProcess;
import com.fluxcache.core.manual.FluxCacheDataRegistered;
import com.fluxcache.core.monitor.DefaultFluxCacheMonitor;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.monitor.FluxHotKeyDetector;
import com.fluxcache.core.monitor.FluxHotKeyListener;
import com.fluxcache.core.preheat.FluxRefreshTaskRegistrar;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Test;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 自动装配：全部 @Bean 方法的直接调用路径（覆盖条件分支与构造逻辑）。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxProxyCacheAutoConfigurationTest {

    private final FluxProxyCacheAutoConfiguration config = new FluxProxyCacheAutoConfiguration();
    private final FluxCacheProperties properties = new FluxCacheProperties();

    private final FluxCacheOperationSource opSource = mock(FluxCacheOperationSource.class);
    private final FluxCacheManager cacheManager = mock(FluxCacheManager.class);
    private final FluxCacheMonitor cacheMonitor = mock(FluxCacheMonitor.class);

    @Test
    public void advisors_createdForAllAnnotationTypes() {
        Advisor put = config.FluxCacheAnnotationAdvisor(properties, opSource, cacheManager, cacheMonitor);
        Advisor cacheable = config.FluxCacheableAnnotationAdvisor(properties, opSource, cacheManager, cacheMonitor);
        Advisor evict = config.FluxCacheEvictAnnotationAdvisor(properties, opSource, cacheManager, cacheMonitor);
        assertNotNull(put);
        assertNotNull(cacheable);
        assertNotNull(evict);
    }

    @Test
    public void operationSource_andParser_wired() {
        com.fluxcache.core.annotation.FluxCacheAnnotationParser parser =
                config.FluxSpringCacheAnnotationParser(properties);
        FluxAnnotationCacheOperationSource source =
                (FluxAnnotationCacheOperationSource) config.FluxCacheOperationSource(parser);
        assertNotNull(source);
    }

    @Test
    public void cacheMonitorBean_createsDefaultMonitor() {
        CacheThreadPoolExecutor pool = config.cacheThreadPoolExecutor(properties);
        ObjectProvider<FluxHotKeyDetector> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);
        FluxCacheMonitor monitor = config.cacheMonitor(pool, properties, empty);
        assertTrue(monitor instanceof DefaultFluxCacheMonitor);
    }

    @Test
    public void hotKeyDetector_wiredWhenEnabled() {
        FluxCacheProperties.HotKeyConfig hotKey = properties.getHotKey();
        hotKey.setEnabled(true);
        ObjectProvider<List<FluxHotKeyListener>> listeners = mock(ObjectProvider.class);
        when(listeners.getIfAvailable()).thenReturn(Collections.emptyList());
        FluxHotKeyDetector detector = config.cacheHotKeyDetector(properties, listeners);
        assertNotNull(detector);
        assertTrue(detector.isEnabled());
    }

    @Test
    public void defaultThreadPool_usesStatisticsFromProperties() {
        properties.getMonitoring().setMonitorCorePoolSize(2);
        properties.getMonitoring().setMonitorMaxPoolSize(5);
        properties.getMonitoring().setMonitorQueueSize(777);
        CacheThreadPoolExecutor pool = config.cacheThreadPoolExecutor(properties);
        assertNotNull(pool);
        assertEquals(2, pool.getCorePoolSize());
        assertEquals(5, pool.getMaxPoolSize());
    }

    @Test
    public void createPostProcess_built() {
        FluxCacheDataRegistered registered = mock(FluxCacheDataRegistered.class);
        FluxCacheCreatePostProcess postProcess =
                config.createPostProcess(registered, cacheManager, cacheMonitor, properties);
        assertNotNull(postProcess);
    }

    @Test
    public void taskScheduler_default() {
        TaskScheduler scheduler = config.taskScheduler();
        assertTrue(scheduler instanceof ThreadPoolTaskScheduler);
        ThreadPoolTaskScheduler tpts = (ThreadPoolTaskScheduler) scheduler;
        assertEquals(5, tpts.getPoolSize());
    }

    @Test
    public void refreshRegistrar_withLock() {
        ApplicationContext context = mock(ApplicationContext.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ObjectProvider<FluxDistributedLock> lockProvider = mock(ObjectProvider.class);
        when(lockProvider.getIfAvailable()).thenReturn(mock(FluxDistributedLock.class));

        FluxRefreshTaskRegistrar registrar =
                config.cacheRefreshTaskRegistrar(context, scheduler, cacheManager, lockProvider);
        assertNotNull(registrar);
    }

    @Test
    public void refreshRegistrar_withoutLock() {
        ApplicationContext context = mock(ApplicationContext.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ObjectProvider<FluxDistributedLock> lockProvider = mock(ObjectProvider.class);
        when(lockProvider.getIfAvailable()).thenReturn(null);

        FluxRefreshTaskRegistrar registrar =
                config.cacheRefreshTaskRegistrar(context, scheduler, cacheManager, lockProvider);
        assertNotNull(registrar);
    }

    @Test
    public void warmUpRunner_builtWithBeanMap() {
        Map<String, Object> beans = new HashMap<>();
        beans.put("svc", new Object());
        FluxCacheWarmUpRunner runner = config.fluxCacheWarmUpRunner(properties, beans);
        assertNotNull(runner);
    }
}