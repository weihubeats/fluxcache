package com.fluxcache.core.preheat;

import java.util.Collection;

/**
 * @author : wh
 * @date : 2025/8/11
 * @description:
 */
@FunctionalInterface
public interface FluxPreheatDataProvider<T> {

    /**
     * 获取用于预热的参数数据集合
     *
     * @return 一个包含预热所需参数的集合
     */
    Collection<T> getPreheatData();

    final class None<T> implements FluxPreheatDataProvider<T> {
        @Override
        public Collection<T> getPreheatData() {
            return null;
        }
    }

}
