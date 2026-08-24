package com.fluxcache.core.config;

import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 应用启动后自动调用带 {@link FluxCacheable} 注解的方法，生成监控数据，避免 Dashboard 为空。
 * 默认关闭，通过 {@code flux.cache.warmUpEnable=true} 开启。
 *
 * <p>只预热只读的 {@link FluxCacheable} 方法；{@link FluxCachePut}/{@link FluxCacheEvict}
 * 具有业务副作用，不会被执行。调用在独立线程中异步执行，不阻塞启动流程。
 */
@Slf4j
public class FluxCacheWarmUpRunner {

    private final FluxCacheProperties properties;
    private final ObjectProvider<Map<String, Object>> beansProvider;

    public FluxCacheWarmUpRunner(FluxCacheProperties properties,
                                  ObjectProvider<Map<String, Object>> beansProvider) {
        this.properties = properties;
        this.beansProvider = beansProvider;
    }

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (!properties.isWarmUpEnable()) {
            return;
        }
        Thread worker = new Thread(this::runWarmUp, "flux-cache-warm-up");
        worker.setDaemon(true);
        worker.start();
    }

    private void runWarmUp() {
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
        Map<String, Object> beanMap = beansProvider.getIfAvailable(Collections::emptyMap);
        if (beanMap == null || beanMap.isEmpty()) {
            return;
        }
        List<Method> methods = collectCacheMethods(beanMap);
        if (methods.isEmpty()) {
            log.info("[FluxCache] warmUp 未发现带缓存注解的方法，跳过");
            return;
        }
        log.info("[FluxCache] 开始缓存预热，共 {} 个带缓存注解的方法", methods.size());
        int success = 0;
        int fail = 0;
        for (Method method : methods) {
            try {
                invokeMethod(beanMap, method);
                success++;
            } catch (Exception e) {
                fail++;
                log.warn("[FluxCache] warmUp 调用方法失败: {}.{}",
                        method.getDeclaringClass().getSimpleName(), method.getName(), e);
            }
        }
        log.info("[FluxCache] 缓存预热完成: success={}, fail={}", success, fail);
    }

    private List<Method> collectCacheMethods(Map<String, Object> beanMap) {
        List<Method> result = new ArrayList<>();
        for (Object bean : beanMap.values()) {
            Class<?> clazz = AopUtils.getTargetClass(bean);
            for (Method method : clazz.getDeclaredMethods()) {
                // 只预热只读方法；Put/Evict 有副作用，不能在启动阶段用伪造参数执行
                if (method.isAnnotationPresent(FluxCacheable.class)) {
                    result.add(method);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void invokeMethod(Map<String, Object> beanMap, Method method) throws Exception {
        Object bean = findBean(beanMap, method.getDeclaringClass());
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

    private Object findBean(Map<String, Object> beanMap, Class<?> clazz) {
        for (Object bean : beanMap.values()) {
            if (bean != null && clazz.isAssignableFrom(AopUtils.getTargetClass(bean))) {
                return bean;
            }
        }
        return null;
    }

}
