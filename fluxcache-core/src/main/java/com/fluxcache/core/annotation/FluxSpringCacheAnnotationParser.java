package com.fluxcache.core.annotation;

import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxCacheEvictOperation;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.model.FluxCachePutOperation;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author : wh
 * @date : 2024/9/16 21:17
 * @description:
 */
@RequiredArgsConstructor
public class FluxSpringCacheAnnotationParser implements FluxCacheAnnotationParser {

    private final FluxCacheProperties cacheProperties;

    private static final Set<Class<? extends Annotation>> CACHE_OPERATION_ANNOTATIONS = new LinkedHashSet<>(8);

    static {
        CACHE_OPERATION_ANNOTATIONS.add(FluxCacheEvict.class);
        CACHE_OPERATION_ANNOTATIONS.add(FluxCachePut.class);
        CACHE_OPERATION_ANNOTATIONS.add(FluxCacheable.class);
    }

    @Override
    public boolean isCandidateClass(Class<?> targetClass) {
        return AnnotationUtils.isCandidateClass(targetClass, CACHE_OPERATION_ANNOTATIONS);
    }

    @Override
    public FluxCacheOperation parseCacheAnnotation(Class<?> type) {
        return this.parseCacheAnnotation(type, true);

    }

    @Override
    public FluxCacheOperation parseCacheAnnotation(Method method) {
        return this.parseCacheAnnotation(method, true);
    }

    @Nullable
    private FluxCacheOperation parseCacheAnnotation(AnnotatedElement ae, boolean localOnly) {
        Collection<? extends Annotation> anns = (localOnly ?
                AnnotatedElementUtils.getAllMergedAnnotations(ae, CACHE_OPERATION_ANNOTATIONS) :
                AnnotatedElementUtils.findAllMergedAnnotations(ae, CACHE_OPERATION_ANNOTATIONS));
        if (anns.isEmpty()) {
            return null;
        }

        final Collection<FluxCacheOperation> ops = new ArrayList<>(1);
        anns.stream().filter(ann -> ann instanceof FluxCacheEvict).forEach(
                ann -> ops.add(parseEvictAnnotation(ae, (FluxCacheEvict) ann)));

        anns.stream().filter(ann -> ann instanceof FluxCachePut).forEach(
                ann -> ops.add(parsePutAnnotation(ae, (FluxCachePut) ann)));

        anns.stream().filter(ann -> ann instanceof FluxCacheable).forEach(
                ann -> ops.add(parseFluxCacheableAnnotation(ae, (FluxCacheable) ann)));

        if (ops.size() > 1) {
            throw new RuntimeException("flux cache Only single operations are supported");
        }
        return ops.stream().findFirst().orElseThrow(() -> new RuntimeException("FluxCacheOperation must not null"));
    }

    /**
     * put cache FluxCachePutOperation
     *
     * @param ae
     * @param cp
     * @return
     */
    private FluxCacheOperation parsePutAnnotation(AnnotatedElement ae, FluxCachePut cp) {
        return new FluxCachePutOperation.Builder()
                .setMethodName(ae.toString())
                .setCacheName(cp.cacheName())
                .setKey(cp.key())
                .build();
    }

    /**
     * enable cache FluxCacheable
     *
     * @param ae
     * @param ca
     * @return
     */
    private FluxCacheOperation parseFluxCacheableAnnotation(AnnotatedElement ae, FluxCacheable ca) {
        FluxCacheConfig firstCacheConfig = new FluxCacheConfig(ca.firstCacheable());
        FluxCacheLevel cacheLevel = cacheProperties.fluxCacheLevel(ca.fluxCacheLevel());
        FluxCacheConfig secondaryCacheable = FluxCacheLevel.isSecondaryCacheable(cacheLevel) ? new FluxCacheConfig(ca.secondaryCacheable()) : null;
        FluxCacheOperation fluxCacheOperation = new FluxMultilevelCacheCacheable.Builder()
                .setFirstCacheConfig(firstCacheConfig)
                .setSecondaryCacheable(secondaryCacheable)
                .setAllowNullValues(ca.allowCacheNull())
                .setFluxCacheLevel(cacheLevel)
                .setCacheName(ca.cacheName())
                .setMethodName(ae.toString())
                .setKey(ca.key())
                .build();
        return fluxCacheOperation;
    }

    /**
     * delete cache
     *
     * @param ae
     * @param ce
     * @return
     */
    private FluxCacheOperation parseEvictAnnotation(AnnotatedElement ae, FluxCacheEvict ce) {
        return new FluxCacheEvictOperation.Builder().setMethodName(ae.toString())
                .setCacheName(ce.cacheName())
                .setKey(ce.key())
                .build();
    }

}
