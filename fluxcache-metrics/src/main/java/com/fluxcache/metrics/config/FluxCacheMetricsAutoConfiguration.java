package com.fluxcache.metrics.config;

import com.fluxcache.core.monitor.FluxCacheMetricsListener;
import com.fluxcache.core.monitor.FluxHotKeyListener;
import com.fluxcache.metrics.FluxCacheHotKeyMicrometerListener;
import com.fluxcache.metrics.FluxCacheMicrometerListener;
import com.fluxcache.metrics.FluxCacheMonitorMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * FluxCache Micrometer 指标自动装配。
 *
 * <p>仅在应用装配了 {@link MeterRegistry}（例如引入 actuator + prometheus registry）时生效，
 * 对未使用指标体系的应用零影响。</p>
 *
 * @author : wh
 */
@Configuration(proxyBeanMethods = false)
// real Boot autoconfiguration classes; the previous "metrics.registry.MeterRegistryAutoConfiguration"
// name never existed, so ordering was silently unguaranteed and binding could run too early
@AutoConfigureAfter(name = {
        "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration"
})
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
public class FluxCacheMetricsAutoConfiguration {

    @Bean
    @Order
    @ConditionalOnMissingBean(FluxCacheMetricsListener.class)
    public FluxCacheMetricsListener fluxCacheMetricsListener(ObjectProvider<MeterRegistry> meterRegistry) {
        return new FluxCacheMicrometerListener(meterRegistry.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(FluxHotKeyListener.class)
    public FluxHotKeyListener fluxCacheHotKeyMetricsListener(ObjectProvider<MeterRegistry> meterRegistry) {
        return new FluxCacheHotKeyMicrometerListener(meterRegistry.getIfAvailable());
    }

    @Bean
    public static FluxCacheMonitorMetricsBinder fluxCacheMonitorMetricsBinder(
        ObjectProvider<FluxCacheMetricsListener> listenerProvider) {
        return new FluxCacheMonitorMetricsBinder(listenerProvider);
    }
}