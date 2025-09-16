package com.fluxcache.core.preheat;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.annotation.FluxRefresh;
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

//    private final ConcurrentHashMap<Object, Long> keyLastRefresh = new ConcurrentHashMap<>();

    public void refresh(FluxCacheRefreshContext ctx) {
        // todo implement the refresh logic here
        loadThenSwap(ctx);

    }

    private void loadThenSwap(FluxCacheRefreshContext ctx) {
        for (Object key : ctx.getKeys()) {
            if (!shouldRefreshKey(key, ctx))
                continue;

            Object newValue = safeInvokeLoader(ctx, key);
            putValue(ctx.getCache(), key, newValue, ctx.getRefreshConfig());
        }
    }

    private void putValue(FluxCache cache, Object key, Object value, FluxRefresh config) {
        cache.put(key, value);
    }

    // todo 扩展可以实现单个key刷新间隔
    private boolean shouldRefreshKey(Object key, FluxCacheRefreshContext ctx) {
        return true;
    }

    private Object safeInvokeLoader(FluxCacheRefreshContext ctx, Object key) {
        try {
            FluxForceRefreshContext.enable();
            return invokeLoader(ctx.getBean(), ctx.getMethod(), key);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            log.error("[FluxCache] 加载 key={} 失败 targetEx={}", key, target.toString());
        } catch (Exception e) {
            FluxForceRefreshContext.disable();
            log.error("[FluxCache] 加载 key={} 失败 ex={}", key, e.toString());
        }
        return null;
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
