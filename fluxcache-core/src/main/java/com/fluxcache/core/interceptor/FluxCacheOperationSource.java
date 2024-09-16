package com.fluxcache.core.interceptor;

import com.fluxcache.core.model.FluxCacheOperation;
import java.lang.reflect.Method;
import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/9/16 13:18
 * @description:
 */
public interface FluxCacheOperationSource {

    default boolean isCandidateClass(Class<?> targetClass) {
        return true;
    }

    /**
     * 注解元数据
     *
     * @param method
     * @param targetClass
     * @return
     */
    @Nullable
    FluxCacheOperation getCacheOperation(Method method, @Nullable Class<?> targetClass);

}
