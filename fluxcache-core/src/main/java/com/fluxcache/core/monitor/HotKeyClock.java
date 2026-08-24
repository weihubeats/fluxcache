package com.fluxcache.core.monitor;

/**
 * 热 Key 探测时钟抽象，方便单元测试注入可控时间源。
 *
 * @author : wh
 */
@FunctionalInterface
public interface HotKeyClock {

    /**
     * 当前毫秒时间
     *
     * @return 毫秒时间戳
     */
    long millis();
}