package com.fluxcache.metrics;

import com.fluxcache.core.config.CacheThreadPoolExecutor;
import com.fluxcache.core.monitor.DefaultFluxCacheMonitor;
import com.fluxcache.core.monitor.FluxCacheMetricsListener;
import com.fluxcache.core.monitor.FluxCacheMonitorEvent;
import com.fluxcache.core.monitor.MonitorEventEnum;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.metrics.config.FluxCacheMetricsAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * BeanPostProcessor 注入验证：监听器绑定到 DefaultFluxCacheMonitor 且事件流转正常。
 *
 * @author : wh
 */
public class FluxCacheMonitorMetricsBinderTest {

    private static final String CACHE = "binder-test";

    private RecordingListener listener;

    private DefaultFluxCacheMonitor monitor;

    @Before
    public void setUp() {
        FluxCacheProperties properties = new FluxCacheProperties();
        properties.setAsyncMonitorEnable(false);
        CacheThreadPoolExecutor pool = new CacheThreadPoolExecutor(1, 2, 4, 60,
                "binder-test-", new ThreadPoolExecutor.DiscardOldestPolicy());
        monitor = new DefaultFluxCacheMonitor(pool, properties);
        listener = new RecordingListener();
    }

    private ObjectProvider<FluxCacheMetricsListener> providerWith(Object... listeners) {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        for (Object l : listeners) {
            bf.registerSingleton(FluxCacheMetricsListener.class.getSimpleName() + System.identityHashCode(l), l);
        }
        return bf.getBeanProvider(FluxCacheMetricsListener.class);
    }

    @Test
    public void binder_injectsListenerIntoMonitor() {
        FluxCacheMonitorMetricsBinder binder = new FluxCacheMonitorMetricsBinder(providerWith(listener));

        Object processed = binder.postProcessAfterInitialization(monitor, "cacheMonitor");

        assertSame(monitor, processed);
        monitor.publishMonitorEvent(FluxCacheMonitorEvent.builder()
                .cacheName(CACHE)
                .monitorEventEnum(MonitorEventEnum.CACHE_HIT)
                .count(2)
                .build());
        assertEquals(1, listener.eventCount);
        assertEquals(MonitorEventEnum.CACHE_HIT, listener.lastType);
        assertEquals(2, listener.lastCount);
    }

    @Test
    public void binder_ignoresNonMonitorBeans() {
        FluxCacheMonitorMetricsBinder binder = new FluxCacheMonitorMetricsBinder(providerWith(listener));

        Object unrelated = new Object();
        assertSame(unrelated, binder.postProcessAfterInitialization(unrelated, "someBean"));
        assertEquals(0, listener.eventCount);
    }

    @Test
    public void binder_skipsWhenNoListenerAvailable() {
        FluxCacheMonitorMetricsBinder binder = new FluxCacheMonitorMetricsBinder(providerWith());

        binder.postProcessAfterInitialization(monitor, "cacheMonitor");
        monitor.publishMonitorEvent(FluxCacheMonitorEvent.builder()
                .cacheName(CACHE)
                .monitorEventEnum(MonitorEventEnum.CACHE_HIT)
                .count(1)
                .build());
        assertEquals(0, listener.eventCount);
    }

    @Test
    public void autoconfiguration_registersListenerAndBinder() {
        new ApplicationContextRunner()
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withConfiguration(AutoConfigurations.of(FluxCacheMetricsAutoConfiguration.class))
                .run(context -> {
                    assertNotNull(context.getBean(FluxCacheMetricsListener.class));
                    assertNotNull(context.getBean(FluxCacheMonitorMetricsBinder.class));
                });
    }

    private static final class RecordingListener implements FluxCacheMetricsListener {

        int eventCount = 0;

        MonitorEventEnum lastType;

        long lastCount;

        @Override
        public void onMonitorEvent(FluxCacheMonitorEvent event) {
            eventCount++;
            lastType = event.getMonitorEventEnum();
            lastCount = event.getCount();
        }
    }
}