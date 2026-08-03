package com.fluxcache.core.config;

import com.fluxcache.core.annotation.FluxCacheEvict;
import com.fluxcache.core.annotation.FluxCachePut;
import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 应用启动后自动调用带缓存注解的方法，生成监控数据，避免 Dashboard 为空。
 * 默认关闭，通过 {@code flux.cache.warmUpEnable=true} 开启。
 *
 * <p>扫描所有 Bean 中带有 {@link FluxCacheable}/{@link FluxCachePut}/{@link FluxCacheEvict}
 * 注解的方法，使用默认参数调用，使监控统计数据在启动后立即可见。
 */
@Slf4j
public class FluxCacheWarmUpRunner {

    private final FluxCacheProperties properties;
    private final Map<String, Object> beanMap;

    public FluxCacheWarmUpRunner(FluxCacheProperties properties,
                                  Map<String, Object> beanMap) {
        this.properties = properties;
        this.beanMap = beanMap;
    }

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (!properties.isWarmUpEnable()) {
            return;
        }
        int delay = properties.getWarmUpDelaySeconds();
        if (delay > 0) {
            try {
                Thread.sleep(delay * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        doWarmUp();
    }

    private void doWarmUp() {
        if (beanMap == null || beanMap.isEmpty()) {
            return;
        }
        List<Method> methods = collectCacheMethods();
        if (methods.isEmpty()) {
            log.info("[FluxCache] warmUp 未发现带缓存注解的方法，跳过");
            return;
        }
        log.info("[FluxCache] 开始缓存预热，共 {} 个带缓存注解的方法", methods.size());
        int success = 0;
        int fail = 0;
        for (Method method : methods) {
            try {
                invokeMethod(method);
                success++;
            } catch (Exception e) {
                fail++;
                log.warn("[FluxCache] warmUp 调用方法失败: {}.{}",
                        method.getDeclaringClass().getSimpleName(), method.getName(), e);
            }
        }
        log.info("[FluxCache] 缓存预热完成: success={}, fail={}", success, fail);
    }

    private List<Method> collectCacheMethods() {
        List<Method> result = new ArrayList<>();
        for (Object bean : beanMap.values()) {
            Class<?> clazz = AopUtils.getTargetClass(bean.getClass());
            for (Method method : clazz.getDeclaredMethods()) {
                if (hasCacheAnnotation(method)) {
                    result.add(method);
                }
            }
        }
        return result;
    }

    private boolean hasCacheAnnotation(Method method) {
        if (method.isAnnotationPresent(FluxCacheable.class)) {
            return true;
        }
        if (method.isAnnotationPresent(FluxCachePut.class)) {
            return true;
        }
        if (method.isAnnotationPresent(FluxCacheEvict.class)) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void invokeMethod(Method method) throws Exception {
        Object bean = findBean(method.getDeclaringClass());
        if (bean == null) {
            return;
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            method.invoke(bean);
            return;
        }
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> ptype = paramTypes[i];
            args[i] = defaultValueFor(ptype, i);
            if (args[i] == null) {
                return;
            }
        }
        method.invoke(bean, args);
    }

    private Object defaultValueFor(Class<?> type, int index) {
        if (type == String.class) {
            return "warmUp-key-" + index;
        }
        if (type == long.class || type == Long.class) {
            return 1L;
        }
        if (type == int.class || type == Integer.class) {
            return 1;
        }
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (type == double.class || type == Double.class) {
            return 1.0;
        }
        if (type == float.class || type == Float.class) {
            return 1.0f;
        }
        return null;
    }

    private Object findBean(Class<?> clazz) {
        for (Object bean : beanMap.values()) {
            if (bean != null && clazz.isAssignableFrom(AopUtils.getTargetClass(bean.getClass()))) {
                return bean;
            }
        }
        return null;
    }

}
