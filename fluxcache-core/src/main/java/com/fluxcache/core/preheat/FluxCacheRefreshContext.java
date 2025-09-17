package com.fluxcache.core.preheat;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.annotation.FluxRefresh;
import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * @author : wh
 * @date : 2025/8/12
 * @description:
 */
@Data
@Builder
public class FluxCacheRefreshContext {

    private Object bean;

    private Method method;

    private String cacheName;

    private FluxRefresh refreshConfig;

    private FluxCache cache;

    private Collection<?> keys;
}
