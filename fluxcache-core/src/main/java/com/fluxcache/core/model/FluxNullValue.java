package com.fluxcache.core.model;

import java.io.Serializable;
import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/10/6 13:59
 * @description:
 */
public class FluxNullValue implements Serializable {

    public static final FluxNullValue INSTANCE = new FluxNullValue();

    @Override
    public boolean equals(@Nullable Object obj) {
        return (this == obj || obj == null);
    }

    @Override
    public int hashCode() {
        return FluxNullValue.class.hashCode();
    }

    @Override
    public String toString() {
        return "null";
    }
}

