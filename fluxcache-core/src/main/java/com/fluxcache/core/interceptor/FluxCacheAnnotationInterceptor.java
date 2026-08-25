package com.fluxcache.core.interceptor;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.FluxCacheEvictOperation;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.model.FluxCachePutOperation;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.monitor.FluxCacheMonitorEvent;
import com.fluxcache.core.monitor.MonitorEventEnum;
import com.fluxcache.core.preheat.FluxForceRefreshContext;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author : wh
 * @date : 2024/11/12 12:39
 * @description:
 */
@RequiredArgsConstructor
@Slf4j
public class FluxCacheAnnotationInterceptor implements MethodInterceptor {

    private final FluxCacheProperties cacheProperties;

    private final FluxCacheOperationSource fluxCacheOperationSource;

    private final FluxCacheManager cacheManager;

    private final FluxCacheMonitor cacheMonitor;

    private final ExpressionParser spelParser = new SpelExpressionParser();

    // SpEL 缓存：method + rawExpression -> compiled Expression
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    // 单飞(single-flight)防护：cacheName::key -> 正在进行的加载
    private final Map<String, CompletableFuture<Object>> singleFlightMap = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Object target = invocation.getThis();
        Assert.state(target != null, "Target must not be null");
        FluxCacheOperationInvoker aopAllianceInvoker = () -> {
            try {
                return invocation.proceed();
            } catch (Throwable ex) {
                throw new FluxCacheOperationInvoker.ThrowableWrapper(ex);
            }
        };
        try {
            return execute(aopAllianceInvoker, target, method, invocation.getArguments());
        } catch (FluxCacheOperationInvoker.ThrowableWrapper th) {
            throw th.getOriginal();
        }
    }

    /**
     * @param invoker aopAllianceInvoker
     * @param target  target
     * @param method  method
     * @param args    arguments
     * @return
     */
    protected Object execute(FluxCacheOperationInvoker invoker, Object target, Method method, Object[] args) {
        Class<?> targetClass = AopUtils.getTargetClass(target);
        if (fluxCacheOperationSource == null) {
            return invoker.invoke();
        }
        // todo  Currently there is only one implementation class: FluxAnnotationCacheOperationSource.

        FluxCacheOperation op = fluxCacheOperationSource.getCacheOperation(method, targetClass);
        if (op == null) {
            return invoker.invoke();
        }
        FluxCacheOperationContexts contexts = new FluxCacheOperationContexts(op, method, args, target, targetClass);

        String key = resolveKey(contexts);

        boolean isPut = op instanceof FluxCachePutOperation;
        boolean isEvict = op instanceof FluxCacheEvictOperation;
        boolean force = FluxForceRefreshContext.isForceRefresh();

        FluxCache cache = cacheManager.getCache(op.getCacheName());
        if (Objects.isNull(cache)) {
            return invoker.invoke();
        }
        // CacheEvict 优先处理（通常执行方法前还是后？这里简单使用“后置”语义；如需 beforeInvocation 可在 op 中加标记）
        if (isEvict) {
            return handleEvict(invoker, cache, key, op);
        }

        if (isPut) {
            return handlePut(invoker, cache, key, op, method);
        }

        // 普通 Cacheable 流程
        return handleCacheable(invoker, cache, key, method, op, force);

    }

    /* ------------------ Cacheable ------------------ */

    private Object handleCacheable(FluxCacheOperationInvoker invoker,
                                   FluxCache cache,
                                   String key,
                                   Method method,
                                   FluxCacheOperation op,
                                   boolean force) {

        boolean allowCacheNull = cacheProperties.isAllowCacheNull();
        boolean allowEmptyOptional = cacheProperties.isAllowCacheEmptyOptional();

        if (!force) {
            FluxCache.ValueWrapper wrapper = safeGet(cache, key);
            if (wrapper != null) {
                Object cached = wrapper.get();
                // Optional 适配
                Object wrapped = adaptOptionalReturn(method, cached);
                if (isNullOrEmptyOptional(wrapped, allowCacheNull, allowEmptyOptional)) {
                    // 命中但策略认为不应该缓存这种值，视为未命中重新加载
                    publish(op.getCacheName(), MonitorEventEnum.CACHE_MISSING, key, 1, 0, false);
                } else {
                    publish(op.getCacheName(), MonitorEventEnum.CACHE_HIT, key, 1, 0, false);
                    if (log.isDebugEnabled()) {
                        log.debug("[FluxCache] HIT cache={} key={} force={}", op.getCacheName(), key, false);
                    }
                    return wrapped;
                }
            } else {
                publish(op.getCacheName(), MonitorEventEnum.CACHE_MISSING, key, 1, 0, false);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] FORCE_REFRESH skip cache read cache={} key={}", op.getCacheName(), key);
            }
        }

        // 单飞防击穿：并发未命中同一 key 时仅一个线程加载，其余等待复用结果
        if (cacheProperties.isSingleFlightEnable() && key != null) {
            return loadWithSingleFlight(invoker, cache, key, method, op,
                    allowCacheNull, allowEmptyOptional, force);
        }
        return doLoad(invoker, cache, key, method, op, allowCacheNull, allowEmptyOptional, force);
    }

    /**
     * 单飞加载：同一 cacheName + key 的并发未命中仅允许一个线程执行加载，
     * 其余线程等待其结果后直接返回（类似 JetCache 的 @CachePenetrationProtect）。
     */
    private Object loadWithSingleFlight(FluxCacheOperationInvoker invoker,
                                        FluxCache cache,
                                        String key,
                                        Method method,
                                        FluxCacheOperation op,
                                        boolean allowCacheNull,
                                        boolean allowEmptyOptional,
                                        boolean force) {
        String flightKey = op.getCacheName() + "::" + method.toGenericString() + "::" + key;
        CompletableFuture<Object> future = new CompletableFuture<>();
        CompletableFuture<Object> leader = singleFlightMap.putIfAbsent(flightKey, future);
        if (leader == null) {
            // 本线程为加载线程，加载完成后唤醒所有等待线程
            try {
                Object value = doLoad(invoker, cache, key, method, op, allowCacheNull, allowEmptyOptional, force);
                future.complete(value);
                return value;
            } catch (Throwable t) {
                future.completeExceptionally(t);
                throw t;
            } finally {
                singleFlightMap.remove(flightKey, future);
            }
        }
        // 等待线程：复用加载线程的结果
        try {
            return leader.get(cacheProperties.getSingleFlightTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // 加载线程失败或等待超时，回退为自行加载，避免等待线程被无限阻塞
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] single-flight 等待失败 key={} 回退自行加载 ex={}", flightKey, e.toString());
            }
            return doLoad(invoker, cache, key, method, op, allowCacheNull, allowEmptyOptional, force);
        }
    }

    private Object doLoad(FluxCacheOperationInvoker invoker,
                          FluxCache cache,
                          String key,
                          Method method,
                          FluxCacheOperation op,
                          boolean allowCacheNull,
                          boolean allowEmptyOptional,
                          boolean force) {
        long begin = System.nanoTime();

        // 调用真实方法
        Object result = invoker.invoke();
        long loadMs = (System.nanoTime() - begin) / 1_000_000;
        Object cacheValue = unwrapResult(result);

        // 按策略决定是否缓存 null / Optional.empty
        if (shouldCacheValue(cacheValue, allowCacheNull)) {
            cache.put(key, cacheValue);
            publish(op.getCacheName(), MonitorEventEnum.CACHE_PUT, key, 1, loadMs, force);
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] PUT cache={} key={} (force={})", op.getCacheName(), key, force);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] SKIP_PUT (value policy) cache={} key={}", op.getCacheName(), key);
            }
        }
        return adaptOptionalReturn(method, cacheValue);
    }

    private FluxCache.ValueWrapper safeGet(FluxCache cache, String key) {
        try {
            return cache.get(key);
        } catch (Exception e) {
            publish(cache.getName(), MonitorEventEnum.CACHE_MISSING, key, 1, 0, false);
            log.error("[FluxCache] cache.get 异常 cache={} key={}", cache.getName(), key, e);
            return null;
        }
    }

    private boolean isNullOrEmptyOptional(Object value,
                                          boolean allowNull,
                                          boolean allowEmptyOptional) {
        if (value == null)
            return !allowNull;
        if (value instanceof Optional<?>) {
            Optional<?> opt = (Optional<?>) value;
            return opt.isEmpty() && !allowEmptyOptional;
        }
        return false;
    }



    /* ------------------ CacheEvict ------------------ */

    private Object handleEvict(FluxCacheOperationInvoker invoker,
                               FluxCache cache,
                               String key,
                               FluxCacheOperation op) {
        Object result = invoker.invoke();
        if (ObjectUtils.isEmpty(key)) {
            cache.clear();
            publish(op.getCacheName(), MonitorEventEnum.CACHE_EVICT, "*", 1, 0, false);
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] EVICT_ALL cache={}", op.getCacheName());
            }
        } else {
            cache.evict(key);
            publish(op.getCacheName(), MonitorEventEnum.CACHE_EVICT, key, 1, 0, false);
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] EVICT cache={} key={}", op.getCacheName(), key);
            }
        }
        return result;
    }

    /* ------------------ CachePut ------------------ */

    private Object handlePut(FluxCacheOperationInvoker invoker,
                              FluxCache cache,
                              String key,
                              FluxCacheOperation op, Method method) {
        long begin = System.nanoTime();
        Object result = invoker.invoke();
        long loadMs = (System.nanoTime() - begin) / 1_000_000;
        Object cacheValue = unwrapResult(result);

        if (shouldCacheValue(cacheValue, cacheProperties.isAllowCacheNull())) {
            cache.put(key, cacheValue);
            publish(op.getCacheName(), MonitorEventEnum.CACHE_PUT, key, 1, loadMs, false);
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] PUT (CachePut) cache={} key={}", op.getCacheName(), key);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] SKIP_PUT (CachePut, value policy) cache={} key={}", op.getCacheName(), key);
            }
        }
        return adaptOptionalReturn(method, cacheValue);
    }

    private Object adaptOptionalReturn(Method method, Object cacheValue) {
        if (method.getReturnType() == Optional.class &&
                (!(cacheValue instanceof Optional))) {
            return Optional.ofNullable(cacheValue);
        }
        return cacheValue;
    }

    private Object unwrapResult(Object result) {
        return ObjectUtils.unwrapOptional(result);
    }

    private boolean shouldCacheValue(Object cacheValue, boolean allowNull) {
        return cacheValue != null || allowNull;
    }

    private String resolveKey(FluxCacheOperationContexts contexts) {
        FluxCacheOperation op = contexts.getFluxCacheOperation();
        Method method = contexts.getMethod();
        Object[] args = contexts.getArgs();
        String rawExpression = op.getKey();
        Object target = contexts.getTarget();
        if (ObjectUtils.isEmpty(rawExpression)) {
            // empty key only allowed for evict (clear-all semantics);
            // otherwise caching under a null key would corrupt or NPE after method execution
            if (op instanceof FluxCacheEvictOperation) {
                return null;
            }
            throw new IllegalStateException(
                    "FluxCache key is blank on method [" + method + "], specify a SpEL key expression");
        }

        try {
            String cacheKey = buildExpressionCacheKey(method, rawExpression);
            Expression exp = expressionCache.computeIfAbsent(
                    cacheKey,
                    k -> spelParser.parseExpression(rawExpression)
            );
            StandardEvaluationContext context = new StandardEvaluationContext();
            // 方法参数名
            String[] paramNames = nameDiscoverer.getParameterNames(method);
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            context.setVariable("target", target);
            context.setVariable("method", method);
            Object value = exp.getValue(context);
            if (value == null) {
                // a shared or random fallback key would poison data across callers - fail fast
                throw new IllegalStateException(
                        "FluxCache key expression '" + rawExpression + "' evaluated to null on method ["
                                + method.getName() + "]");
            }
            return value.toString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // a shared fallback key would serve caller A's cached value to caller B - fail fast
            throw new IllegalStateException("FluxCache key SpEL 解析失败 expression='" + rawExpression
                    + "' method=" + method.getName(), e);
        }
    }

    private String buildExpressionCacheKey(Method method, String expr) {
        return method.toGenericString() + "::" + expr;
    }

    private void publish(String cacheName, MonitorEventEnum type, String key, long count, long loadMs, boolean force) {
        cacheMonitor.publishMonitorEvent(
                FluxCacheMonitorEvent.builder()
                        .cacheName(cacheName)
                        .monitorEventEnum(type)
                        .count(count)
                        .loadTime(loadMs)
                        .timestamp(System.currentTimeMillis())
                        .key(key)
                        .forceRefresh(force)
                        .build()
        );
    }

}
