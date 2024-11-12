package com.fluxcache.core.interceptor;

import com.fluxcache.core.model.FluxCacheOperation;
import java.lang.reflect.Method;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/11/12 12:40
 * @description:
 */
@Data
public class FluxCacheOperationContexts {

    /**
     * cache operation
     */
    private FluxCacheOperation fluxCacheOperation;

    /**
     * method
     */
    private Method method;

    /**
     * args
     */
    private Object[] args;

    Object target;

    Class<?> targetClass;

    public FluxCacheOperationContexts() {
    }

    public FluxCacheOperationContexts(FluxCacheOperation fluxCacheOperation, Method method, Object[] args,
        Object target,
        Class<?> targetClass) {
        this.fluxCacheOperation = fluxCacheOperation;
        this.method = method;
        this.args = args;
        this.target = target;
        this.targetClass = targetClass;
    }
    
}
