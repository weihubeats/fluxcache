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
import org.springframework.cache.interceptor.CacheOperationInvoker;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.SimpleIdGenerator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    private final LocalVariableTableParameterNameDiscoverer nameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

    @Nullable
    @Override
    public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
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
        } catch (CacheOperationInvoker.ThrowableWrapper th) {
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
            publish(op.getCacheName(), MonitorEventEnum.CACHE_PUT, key, 1, 0, true);
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] FORCE_REFRESH skip cache read cache={} key={}", op.getCacheName(), key);
            }
        }

        long begin = System.nanoTime();

        // 调用真实方法
        Object result = invoker.invoke();
        long loadMs = (System.nanoTime() - begin) / 1_000_000;
        Object cacheValue = unwrapResult(result);

        // 按策略决定是否缓存 null / Optional.empty
        if (shouldCacheValue(cacheValue, allowCacheNull, allowEmptyOptional)) {
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
        cache.put(key, cacheValue);
        publish(op.getCacheName(), MonitorEventEnum.CACHE_PUT, key, 1, loadMs, false);

/*        if (shouldCacheValue(cacheValue, cacheProperties.isAllowCacheNull(), cacheProperties.isAllowCacheEmptyOptional())) {
            cache.put(key, cacheValue);
//            cacheMonitor.recordPut(op.getCacheName(), key);
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] PUT (CachePut) cache={} key={}", op.getCacheName(), key);
            }
        }*/
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

    private boolean shouldCacheValue(Object cacheValue,
                                     boolean allowNull,
                                     boolean allowEmptyOptional) {
        if (cacheValue == null) {
            return allowNull;
        }
        if (cacheValue instanceof Optional<?>) {
            Optional<?> opt = (Optional<?>) cacheValue;
            return opt.isPresent() || allowEmptyOptional;
        }
        return true;
    }

    private String resolveKey(FluxCacheOperationContexts contexts) {
        FluxCacheOperation op = contexts.getFluxCacheOperation();
        Method method = contexts.getMethod();
        Object[] args = contexts.getArgs();
        String rawExpression = op.getKey();
        Object target = contexts.getTarget();
        if (ObjectUtils.isEmpty(rawExpression)) {
            return null;
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
                // 返回 null key 不安全，给一个随机或固定 fallback
                return "NullKeyFallback:" + new SimpleIdGenerator().generateId();
            }
            return value.toString();
        } catch (Exception e) {
            log.error("[FluxCache] Key SpEL 解析失败 expression='{}' method={} 使用 fallback key",
                    rawExpression, method.getName(), e);
            return "SpelErrorKey:" + method.getName();
        }
    }

    private String buildExpressionCacheKey(Method method, String expr) {
        return method.toGenericString() + "::" + expr;
    }

    private Object unwrapReturnValue(Object returnValue) {
        return ObjectUtils.unwrapOptional(returnValue);
    }

    private Object wrapCacheValue(Method method, @org.springframework.lang.Nullable Object cacheValue) {
        if (method.getReturnType() == Optional.class &&
                (cacheValue == null || cacheValue.getClass() != Optional.class)) {
            return Optional.ofNullable(cacheValue);
        }
        return cacheValue;
    }

    /**
     * get key, key  run as null
     *
     * @param contexts
     * @return
     */
    private String generateKey(FluxCacheOperationContexts contexts) {
        String key = contexts.getFluxCacheOperation().getKey();
        if (ObjectUtils.isEmpty(key)) {
            return null;
        }
        return Objects.requireNonNull(parse(key, contexts.getMethod(), contexts.getArgs())).toString();
    }

    public static Object parse(String expressionString, Method method, Object[] args) {
        if (ObjectUtils.isEmpty(expressionString)) {
            return null;
        }
        //获取被拦截方法参数名列表
        LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();
        String[] paramNameArr = discoverer.getParameterNames(method);
        //SPEL解析
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < Objects.requireNonNull(paramNameArr).length; i++) {
            context.setVariable(paramNameArr[i], args[i]);
        }
        return parser.parseExpression(expressionString).getValue(context);
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
