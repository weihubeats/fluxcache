package com.fluxcache.core.preheat;

import com.fluxcache.core.FluxCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author : wh
 * @date : 2025/8/12
 * @description:
 */
@Slf4j
public class FluxCacheRefreshExecutor {

    public void refresh(FluxCacheRefreshContext ctx) {
        loadThenSwap(ctx);
    }

    private void loadThenSwap(FluxCacheRefreshContext ctx) {
        FluxCache cache = ctx.getCache();
        if (cache == null) {
            log.warn("[FluxCache] 刷新跳过，缓存不存在 cacheName={}", ctx.getCacheName());
            return;
        }
        for (Object key : ctx.getKeys()) {
            if (!shouldRefreshKey(key, ctx)) {
                continue;
            }
            try {
                Object newValue = invokeLoaderSafely(ctx, key);
                if (newValue == null && !cache.allowCacheNull()) {
                    // 与拦截器策略一致：不允许缓存 null 时跳过写入，保留旧值
                    if (log.isDebugEnabled()) {
                        log.debug("[FluxCache] 刷新返回 null 且不允许缓存，跳过写入 cache={} key={}", cache.getName(), key);
                    }
                    continue;
                }
                cache.put(key, newValue);
            } catch (Exception e) {
                // 单个 key 刷新失败仅记录并保留旧缓存值，不影响其余 key
                Throwable cause = e instanceof InvocationTargetException && e.getCause() != null
                        ? e.getCause() : e;
                log.error("[FluxCache] 刷新 key={} 失败，保留旧缓存值 cache={} ex={}",
                        key, cache.getName(), cause, cause);
            }
        }
    }

    // todo 扩展可以实现单个key刷新间隔
    private boolean shouldRefreshKey(Object key, FluxCacheRefreshContext ctx) {
        return true;
    }

    private Object invokeLoaderSafely(FluxCacheRefreshContext ctx, Object key)
            throws InvocationTargetException, IllegalAccessException {
        // try/finally 保证强制刷新上下文在线程上被清除，避免 ThreadLocal 泄漏
        FluxForceRefreshContext.enable();
        try {
            return invokeLoader(ctx.getBean(), ctx.getMethod(), key);
        } finally {
            FluxForceRefreshContext.disable();
        }
    }

    private Object invokeLoader(Object bean, Method method, Object key)
            throws InvocationTargetException, IllegalAccessException {
        ReflectionUtils.makeAccessible(method);
        if (method.getParameterCount() == 0) {
            return method.invoke(bean);
        } else if (method.getParameterCount() == 1) {
            return method.invoke(bean, key);
        } else {
            throw new IllegalStateException("不支持多参数刷新: " + method);
        }
    }
}
