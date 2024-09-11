package com.fluxcache.core.annotation;

import com.fluxcache.core.model.FluxCacheOperation;
import java.lang.reflect.Method;
import org.springframework.lang.Nullable;

/**
 * @author : wh
 * @date : 2024/9/11 22:28
 * @description:
 */
public interface FluxCacheAnnotationParser {

    default boolean isCandidateClass(Class<?> targetClass) {
        return true;
    }

    @Nullable
    FluxCacheOperation parseCacheAnnotation(Class<?> type);

    @Nullable
    FluxCacheOperation parseCacheAnnotation(Method method);
}
