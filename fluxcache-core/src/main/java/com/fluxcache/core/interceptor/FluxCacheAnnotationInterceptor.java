package com.fluxcache.core.interceptor;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.FluxCacheEvictOperation;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.model.FluxCachePutOperation;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.interceptor.CacheOperationInvoker;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * @author : wh
 * @date : 2024/11/12 12:39
 * @description:
 */
@RequiredArgsConstructor
public class FluxCacheAnnotationInterceptor implements MethodInterceptor {

    private final FluxCacheProperties cacheProperties;

    private final FluxCacheOperationSource FluxCacheOperationSource;

    private final FluxCacheManager cacheManager;

    private final FluxCacheMonitor cacheMonitor;

    @Nullable
    @Override
    public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        FluxCacheOperationInvoker aopAllianceInvoker = () -> {
            try {
                return invocation.proceed();
            } catch (Throwable ex) {
                throw new FluxCacheOperationInvoker.ThrowableWrapper(ex);
            }
        };
        Object target = invocation.getThis();
        Assert.state(target != null, "Target must not be null");

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
        // todo  Currently there is only one implementation class: FluxAnnotationCacheOperationSource.
        if (Objects.nonNull(FluxCacheOperationSource)) {
            // 
            FluxCacheOperation FluxCacheOperation = FluxCacheOperationSource.getCacheOperation(method, targetClass);
            if (Objects.nonNull(FluxCacheOperation)) {
                FluxCacheOperationContexts contexts = new FluxCacheOperationContexts(FluxCacheOperation, method, args, target, targetClass);
                return execute(invoker, contexts);
            }
        }
        return invoker.invoke();
    }

    private Object execute(FluxCacheOperationInvoker invoker, FluxCacheOperationContexts contexts) {
        // todo Write it off for now.
        FluxCacheOperation operation = contexts.getFluxCacheOperation();
        //key
        String key = generateKey(contexts);
        // 获取缓存
        FluxCache cache = cacheManager.getCache(operation.getCacheName());
        Object returnValue;
        Object cacheValue;
        boolean isCachePut = operation instanceof FluxCachePutOperation;
        boolean isCacheEvict = operation instanceof FluxCacheEvictOperation;
        if (Objects.nonNull(cache) && !isCachePut && !isCacheEvict) {
            FluxCache.ValueWrapper wrapper = cache.get(key);
            if (Objects.nonNull(wrapper)) {

                Method method = contexts.getMethod();
                returnValue = wrapCacheValue(method, wrapper.get());
            } else {
                returnValue = invoker.invoke();
                cacheValue = unwrapReturnValue(returnValue);
                cache.put(key, cacheValue);
            }
        } else {
            returnValue = invoker.invoke();
        }

        // cachePut
        if (isCachePut && Objects.nonNull(cache)) {
            cache.put(key, returnValue);
        }

        // cacheEvict
        if (isCacheEvict && Objects.nonNull(cache)) {
            // clear all
            if (ObjectUtils.isEmpty(key)) {
                cache.clear();
            } else {
                cache.evict(key);
            }

        }
        return returnValue;

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

}
