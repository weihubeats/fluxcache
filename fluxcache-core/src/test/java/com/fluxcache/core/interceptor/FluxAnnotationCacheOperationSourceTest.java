package com.fluxcache.core.interceptor;

import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.FluxCachePut;
import com.fluxcache.core.annotation.FluxSpringCacheAnnotationParser;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 注解操作源：方法级元数据解析、接口方法解析、元数据缓存。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxAnnotationCacheOperationSourceTest {

    private FluxAnnotationCacheOperationSource source;

    @Before
    public void setUp() {
        source = new FluxAnnotationCacheOperationSource(
                new FluxSpringCacheAnnotationParser(new FluxCacheProperties()));
    }

    @Test
    public void getCacheOperation_annotatedMethod_parsed() throws Exception {
        Method method = Service.class.getMethod("load", String.class);
        assertNotNull(source.getCacheOperation(method, Service.class));
        assertEquals("ops-1", source.getCacheOperation(method, Service.class).getCacheName());
    }

    @Test
    public void getCacheOperation_cachedOnSecondCall() throws Exception {
        Method method = Service.class.getMethod("load", String.class);
        com.fluxcache.core.model.FluxCacheOperation first =
                source.getCacheOperation(method, Service.class);
        com.fluxcache.core.model.FluxCacheOperation second =
                source.getCacheOperation(method, Service.class);
        assertSame(first, second);
    }

    @Test
    public void getCacheOperation_interfaceMethod_resolvesImplementation() throws Exception {
        Method interfaceMethod = CacheService.class.getMethod("get", String.class);
        Method implMethod = CacheServiceImpl.class.getMethod("get", String.class);
        assertNotNull(source.getCacheOperation(interfaceMethod, CacheServiceImpl.class));
        assertEquals("iface-cache", source.getCacheOperation(implMethod, CacheServiceImpl.class).getCacheName());
    }

    @Test
    public void getCacheOperation_objectMethod_returnsNull() throws Exception {
        Method toString = Object.class.getMethod("toString");
        assertNull(source.getCacheOperation(toString, Object.class));
    }

    @Test
    public void getCacheOperation_plainMethod_returnsNull() throws Exception {
        Method plain = Service.class.getMethod("plain");
        assertNull(source.getCacheOperation(plain, Service.class));
    }

    @Test
    public void isCandidateClass_delegatesToParser() {
        assertTrue(source.isCandidateClass(Service.class));
    }

    @Test
    public void findCacheOperation_parseDirectly() throws Exception {
        Method method = Service.class.getMethod("put", String.class);
        com.fluxcache.core.model.FluxCacheOperation op = source.findCacheOperation(method);
        assertNotNull(op);
        assertEquals("ops-put", op.getCacheName());
    }

    public interface CacheService {

        String get(String key);
    }

    public static class CacheServiceImpl implements CacheService {

        @FluxCacheable(cacheName = "iface-cache", key = "#key")
        @Override
        public String get(String key) {
            return key;
        }
    }

    public static class Service {

        @FluxCacheable(cacheName = "ops-1", key = "#key")
        public String load(String key) {
            return key;
        }

        @FluxCachePut(cacheName = "ops-put")
        public String put(String key) {
            return key;
        }

        public String plain() {
            return "x";
        }
    }
}