package com.fluxcache.core;

import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/10/6 13:56
 * @description:
 */
public class FluxSimpleValueWrapper<V> implements FluxCache.ValueWrapper<V> {

    @Nullable
    private final V value;

    /**
     * Create a new SimpleValueWrapper instance for exposing the given value.
     *
     * @param value the value to expose (may be {@code null})
     */
    public FluxSimpleValueWrapper(@Nullable V value) {
        this.value = value;
    }

    /**
     * Simply returns the value as given at construction time.
     */
    @Override
    @Nullable
    public V get() {
        return this.value;
    }

}
