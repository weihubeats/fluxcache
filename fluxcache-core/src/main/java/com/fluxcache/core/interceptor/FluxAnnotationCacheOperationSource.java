package com.fluxcache.core.interceptor;

import com.fluxcache.core.annotation.FluxCacheAnnotationParser;
import com.fluxcache.core.model.FluxCacheOperation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodClassKey;

/**
 * @author : wh
 * @date : 2024/9/28 13:59
 * @description:
 */
@RequiredArgsConstructor
public class FluxAnnotationCacheOperationSource implements FluxCacheOperationSource {

    private final FluxCacheAnnotationParser annotationParsers;

    /**
     * 缓存元数据
     */
    private final Map<MethodClassKey, FluxCacheOperation> attributeCache = new ConcurrentHashMap<>(1024);



    @Override
    public FluxCacheOperation getCacheOperation(Method method, Class<?> targetClass) {
        if (method.getDeclaringClass() == Object.class) {
            return null;
        }
        MethodClassKey cacheKey = getCacheKey(method, targetClass);
        FluxCacheOperation fluxCacheOperation = this.attributeCache.get(cacheKey);
        if (Objects.nonNull(fluxCacheOperation)) {
            return fluxCacheOperation;
        } else {
            // 获取接口实现类的的方法
            Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
            // 解析方法元数据
            FluxCacheOperation operation = findCacheOperation(specificMethod);
            // 丢到缓存里面去
            if (Objects.nonNull(operation)) {
                attributeCache.put(cacheKey, operation);
                return operation;
            }
        }
        return null;
    }

    private MethodClassKey getCacheKey(Method method, Class<?> targetClass) {
        return new MethodClassKey(method, targetClass);
    }

    @Override
    public boolean isCandidateClass(Class<?> targetClass) {
        return annotationParsers.isCandidateClass(targetClass);
    }

    protected FluxCacheOperation findCacheOperation(Method method) {
        return annotationParsers.parseCacheAnnotation(method);
    }


}

