package com.fluxcache.core.monitor;

/**
 * 监控事件次级扩展点。
 *
 * <p>核心监控链路（{@link DefaultFluxCacheMonitor}）在统计窗口更新之外，
 * 会将缓存注册与监控事件广播给所有实现类，用于对接外部可观测性体系
 * （如 Micrometer / Prometheus）。核心模块不依赖任何具体指标实现。</p>
 *
 * @author : wh
 */
public interface FluxCacheMetricsListener {

    /**
     * 缓存注册/创建时回调，用于注册按缓存维度的指标。
     *
     * @param cacheName 缓存名
     */
    default void onCacheRegistered(String cacheName) {
    }

    /**
     * 缓存监控事件回调。
     *
     * @param event 监控事件（含 cacheName / 事件类型 / count / loadTime）
     */
    default void onMonitorEvent(FluxCacheMonitorEvent event) {
    }
}
