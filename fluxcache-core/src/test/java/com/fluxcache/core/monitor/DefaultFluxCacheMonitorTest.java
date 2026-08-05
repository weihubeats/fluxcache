package com.fluxcache.core.monitor;

import com.fluxcache.core.config.CacheThreadPoolExecutor;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 缓存监控统计：事件路由、异步/同步上报、滚动窗口、未知事件容错。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class DefaultFluxCacheMonitorTest {

    private static final String CACHE = "monitor-test";

    private FluxCacheProperties properties;
    private CacheThreadPoolExecutor pool;
    private DefaultFluxCacheMonitor monitor;

    @Before
    public void setUp() {
        properties = new FluxCacheProperties();
        pool = new CacheThreadPoolExecutor(1, 2, 4, 60,
                "monitor-test-", new ThreadPoolExecutor.DiscardOldestPolicy());
        monitor = new DefaultFluxCacheMonitor(pool, properties);
    }

    private FluxCacheMonitorEvent event(MonitorEventEnum type, long count, long loadTime) {
        return FluxCacheMonitorEvent.builder()
                .cacheName(CACHE)
                .monitorEventEnum(type)
                .count(count)
                .loadTime(loadTime)
                .timestamp(System.currentTimeMillis())
                .key("k")
                .forceRefresh(false)
                .build();
    }

    @Test
    public void synchronousPublish_updatesStats() {
        properties.setAsyncMonitorEnable(false);

        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_HIT, 1, 0));
        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_MISSING, 1, 0));
        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_PUT, 1, 42));
        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_EVICT, 1, 0));

        FluxCacheStatics stats = monitor.getCacheStatics(CACHE);
        // 命中/未命中各 1，PUT 的 maxLoadTime 应反映真实加载耗时
        assertEquals(1, stats.getWindow().peekLast().getHit().sum());
        assertEquals(1, stats.getWindow().peekLast().getMiss().sum());
        assertEquals(1, stats.getWindow().peekLast().getEvictCount().sum());
        assertEquals(1, stats.getWindow().peekLast().getPutCount().sum());
        assertEquals(42, stats.getWindow().peekLast().getMaxLoadTime().get());
        assertEquals(2, stats.getWindow().peekLast().getRequestCount().sum());
    }

    @Test
    public void countIsAggregated() {
        properties.setAsyncMonitorEnable(false);
        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_HIT, 5, 0));
        assertEquals(5, monitor.getCacheStatics(CACHE).getWindow().peekLast().getHit().sum());
    }

    @Test
    public void nullEvent_orMissingField_isIgnored() {
        properties.setAsyncMonitorEnable(false);
        monitor.publishMonitorEvent(null);
        monitor.publishMonitorEvent(event(null, 1, 0));
        monitor.publishMonitorEvent(FluxCacheMonitorEvent.builder().monitorEventEnum(MonitorEventEnum.CACHE_HIT).build());
        monitor.publishMonitorEvent(FluxCacheMonitorEvent.builder().cacheName(CACHE).build());

        FluxCacheStatics stats = monitor.getCacheStatics(CACHE);
        assertEquals(0, stats.getWindow().peekLast().getHit().sum());
        assertEquals(0, stats.getWindow().peekLast().getRequestCount().sum());
    }

    @Test
    public void unknownEventType_logsAndSkips() {
        properties.setAsyncMonitorEnable(false);
        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_EXPIRE, 1, 0));
        assertTrue(monitor.getCacheStatics(CACHE).getWindow().peekLast().getRequestCount().sum() == 0);
    }

    @Test
    public void asyncPublish_usesThreadPool() throws Exception {
        pool.initialize();
        properties.setAsyncMonitorEnable(true);

        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_HIT, 3, 0));

        // 等异步任务执行完
        ThreadPoolExecutor executor = pool.getThreadPoolExecutor();
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(3, monitor.getCacheStatics(CACHE).getWindow().peekLast().getHit().sum());
    }

    @Test
    public void createNewAndExistingStatics() {
        monitor.createNewCacheStatics("a");
        monitor.createNewCacheStatics("a");
        assertNotNull(monitor.getCacheStatics("a"));
        assertNotNull(monitor.getCacheStatics("never-created"));
    }

    @Test
    public void createCacheStaticsMap_ignoresEmpty_initForKeys() {
        monitor.createCacheStaticsMap(new ConcurrentHashMap<>());

        Map<String, FluxCacheOperation> data = new HashMap<>();
        data.put("c1", mockOperation("c1"));
        monitor.createCacheStaticsMap(new ConcurrentHashMap<>(data));
        assertNotNull(monitor.getCacheStatics("c1"));
    }

    @Test
    public void metricsListener_receivesMonitorEvents() {
        properties.setAsyncMonitorEnable(false);
        List<FluxCacheMonitorEvent> received = new ArrayList<>();
        monitor.addFluxCacheMetricsListener(new FluxCacheMetricsListener() {
            @Override
            public void onMonitorEvent(FluxCacheMonitorEvent event) {
                received.add(event);
            }
        });

        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_HIT, 2, 0));
        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_MISSING, 1, 30));

        assertEquals(2, received.size());
        assertEquals(MonitorEventEnum.CACHE_HIT, received.get(0).getMonitorEventEnum());
        assertEquals(2, received.get(0).getCount());
        assertEquals(MonitorEventEnum.CACHE_MISSING, received.get(1).getMonitorEventEnum());
        assertEquals(30, received.get(1).getLoadTime());
    }

    @Test
    public void metricsListener_receivesCacheRegistered() {
        List<String> registered = new ArrayList<>();
        monitor.addFluxCacheMetricsListener(new FluxCacheMetricsListener() {
            @Override
            public void onCacheRegistered(String cacheName) {
                registered.add(cacheName);
            }
        });

        monitor.createNewCacheStatics("a");
        monitor.createNewCacheStatics("a");
        Map<String, FluxCacheOperation> data = new HashMap<>();
        data.put("b", mockOperation("b"));
        monitor.createCacheStaticsMap(new ConcurrentHashMap<>(data));
        // 已存在的缓存再次初始化不应重复通知
        monitor.createCacheStaticsMap(new ConcurrentHashMap<>(data));

        assertTrue(registered.contains("a"));
        assertTrue(registered.contains("b"));
        // 重复注册同 key 应只通知一次
        assertEquals(2, registered.size());
    }

    @Test
    public void metricsListener_nullAndEdgeCases_areSafe() {
        List<FluxCacheMonitorEvent> received = new ArrayList<>();
        monitor.addFluxCacheMetricsListener(null);
        monitor.addFluxCacheMetricsListener(new FluxCacheMetricsListener() {
            @Override
            public void onMonitorEvent(FluxCacheMonitorEvent event) {
                received.add(event);
            }
        });

        properties.setAsyncMonitorEnable(false);
        monitor.publishMonitorEvent(event(MonitorEventEnum.CACHE_HIT, 1, 0));

        assertEquals(1, received.size());
    }

    private FluxCacheOperation mockOperation(String cacheName) {
        return new FluxCacheOperation(cacheName, com.fluxcache.core.enums.FluxCacheLevel.FirstCacheable) {
        };
    }
}