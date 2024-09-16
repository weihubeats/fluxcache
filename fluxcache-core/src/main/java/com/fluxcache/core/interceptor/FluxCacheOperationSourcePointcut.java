package com.fluxcache.core.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/9/16 13:20
 * @description:
 */
public abstract class FluxCacheOperationSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        FluxCacheOperationSource fluxCacheOperationSource = getFluxCacheOperationSource();
        return false;
    }

    @Nullable
    protected abstract FluxCacheOperationSource getFluxCacheOperationSource();
}
