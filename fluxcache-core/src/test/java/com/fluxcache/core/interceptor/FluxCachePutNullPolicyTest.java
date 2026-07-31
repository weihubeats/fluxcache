package com.fluxcache.core.interceptor;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxCacheFactory;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxCachePutOperation;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FluxCachePutNullPolicyTest {

    private FluxCacheProperties cacheProperties;
    private FluxCache cache;
    private FluxCacheAnnotationInterceptor interceptor;
    private FluxCachePutOperation putOp;
    private Method putMethod;
    private PutTarget putTarget;

    @Before
    public void setUp() {
        cacheProperties = new FluxCacheProperties();
        CacheSyncStrategy syncStrategy = mock(CacheSyncStrategy.class);
        FluxCacheMonitor monitor = mock(FluxCacheMonitor.class);

        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) new FluxMultilevelCacheCacheable.Builder()
                .setFirstCacheConfig(caffeineConfig(5L, 100))
                .setAllowNullValues(true)
                .setCacheName("put-test")
                .setMethodName("getValue")
                .setKey("#id")
                .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
                .build();

        FluxCache created = FluxCacheFactory.withDefaults()
                .createFluxCache(op, cacheProperties, syncStrategy, monitor);
        cache = created;

        FluxCacheManager cacheManager = mock(FluxCacheManager.class);
        when(cacheManager.getCache("put-test")).thenReturn(cache);

        try {
            putMethod = PutTarget.class.getMethod("getValue", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        putTarget = new PutTarget();

        putOp = (FluxCachePutOperation) new FluxCachePutOperation.Builder()
                .setCacheName("put-test")
                .setKey("#id")
                .build();

        FluxCacheOperationSource operationSource = mock(FluxCacheOperationSource.class);
        when(operationSource.getCacheOperation(putMethod, PutTarget.class)).thenReturn(putOp);

        interceptor = new FluxCacheAnnotationInterceptor(cacheProperties, operationSource, cacheManager, monitor);
    }

    private Object doInvoke(MethodInvocation invocation) {
        try {
            return interceptor.invoke(invocation);
        } catch (Throwable e) {
            if (e instanceof FluxCacheOperationInvoker.ThrowableWrapper) {
                throw new RuntimeException(e.getCause());
            }
            throw new RuntimeException(e);
        }
    }

    @Test
    public void cachePut_withNullAndAllowNull_falseDoesNotCache() {
        cacheProperties.setAllowCacheNull(false);

        MethodInvocation invocation = buildInvocation(() -> null);
        Object result = doInvoke(invocation);
        assertNull(result);

        assertNull(cache.get("key1"));
    }

    @Test
    public void cachePut_withNullAndAllowNull_trueDoesCache() {
        cacheProperties.setAllowCacheNull(true);

        MethodInvocation invocation = buildInvocation(() -> null);
        Object result = doInvoke(invocation);
        assertNull(result);

        assertNotNull(cache.get("key1"));
    }

    @Test
    public void cachePut_withValue_alwaysCaches() {
        cacheProperties.setAllowCacheNull(false);

        MethodInvocation invocation = buildInvocation(() -> "hello");
        Object result = doInvoke(invocation);
        assertEquals("hello", result);

        FluxCache.ValueWrapper wrapper = cache.get("key1");
        assertNotNull(wrapper);
        assertEquals("hello", wrapper.get());
    }

    private MethodInvocation buildInvocation(FluxCacheOperationInvoker invoker) {
        return new MethodInvocation() {
            @Override
            public Method getMethod() { return putMethod; }
            @Override
            public Object[] getArguments() { return new Object[]{"key1"}; }
            @Override
            public Object getThis() { return putTarget; }
            @Override
            public Object proceed() throws Throwable {
                try {
                    return invoker.invoke();
                } catch (FluxCacheOperationInvoker.ThrowableWrapper e) {
                    throw e.getOriginal();
                }
            }
            @Override
            public java.lang.reflect.AccessibleObject getStaticPart() { return putMethod; }
        };
    }

    private static FluxCacheConfig caffeineConfig(long ttl, int maxSize) {
        return new FluxCacheConfig.Builder()
                .setTtl(ttl)
                .setInitSize(16)
                .setMaxSize(maxSize)
                .setUnit(TimeUnit.MINUTES)
                .setCacheType(FluxCacheType.CAFFEINE)
                .build();
    }

    public static class PutTarget {
        public String getValue(String id) {
            return null;
        }
    }
}
