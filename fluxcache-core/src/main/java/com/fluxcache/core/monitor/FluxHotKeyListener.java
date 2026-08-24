package com.fluxcache.core.monitor;

/**
 * 热 Key 探测次级扩展点。
 *
 * <p>核心探测链路（{@link FluxHotKeyDetector}）在判定热 Key / 恢复后通知所有实现类，
 * 用于对接日志、Micrometer / Prometheus、Dashboard 或自动续期预热等能力
 * （与 {@link FluxCacheMetricsListener} 同类模式，核心模块不依赖具体实现）。</p>
 *
 * @author : wh
 */
public interface FluxHotKeyListener {

    /**
     * 判定某 key 为热 key 时回调（受冷却间隔节流，并非每次请求都会触发）。
     *
     * @param snapshot 热 key 快照
     */
    default void onHotKeyDetected(FluxHotKeySnapshot snapshot) {
    }

    /**
     * 热 key 热度消退恢复时回调。
     *
     * @param snapshot 恢复前最后一次的快照
     */
    default void onHotKeyRecovered(FluxHotKeySnapshot snapshot) {
    }
}