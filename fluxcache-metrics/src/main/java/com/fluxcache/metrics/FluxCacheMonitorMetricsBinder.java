package com.fluxcache.metrics;

import com.fluxcache.core.monitor.DefaultFluxCacheMonitor;
import com.fluxcache.core.monitor.FluxCacheMetricsListener;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 将 {@link FluxCacheMetricsListener} 注入核心 {@link DefaultFluxCacheMonitor}。
 *
 * <p>通过 BeanPostProcessor 而非构造器覆盖，避免修改 core 装配、避免 Bean 覆盖冲突；
 * listener 懒解析（ ObjectProvider#getIfAvailable ），monitor 先于 listener 创建时也能正常注入。</p>
 *
 * @author : wh
 */
public class FluxCacheMonitorMetricsBinder implements BeanPostProcessor {

    private final ObjectProvider<FluxCacheMetricsListener> listenerProvider;

    public FluxCacheMonitorMetricsBinder(ObjectProvider<FluxCacheMetricsListener> listenerProvider) {
        this.listenerProvider = listenerProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DefaultFluxCacheMonitor) {
            // 此处解析会按需触发 listener 及 MeterRegistry 的创建（回归自旋依赖：listener 不依赖 monitor）
            FluxCacheMetricsListener listener = listenerProvider.getIfAvailable();
            if (listener != null) {
                ((DefaultFluxCacheMonitor) bean).addFluxCacheMetricsListener(listener);
            }
        }
        return bean;
    }
}