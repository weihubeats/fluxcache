package com.fluxcache.core.exception;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.interceptor.FluxCacheErrorHandler;
import org.junit.Test;

import java.util.concurrent.Callable;

import static org.junit.Assert.assertSame;

/**
 * 异常体系与错误处理器：异常类型构造与错误传播。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheExceptionTest {

    private final FluxCacheErrorHandler handler = new FluxSimpleCacheErrorHandler();
    private final RuntimeException ex = new IllegalStateException("boom");
    private final FluxCache<Object, Object> cache = new StubCache();

    @Test
    public void metaDataException_holdsMessage() {
        FluxCacheMetaDataException e = new FluxCacheMetaDataException("dup-cache");
        org.junit.Assert.assertEquals("dup-cache", e.getMessage());
    }

    @Test
    public void notSupperException_holdsMessage() {
        FluxCacheNotSupperException e = new FluxCacheNotSupperException("unsupported");
        org.junit.Assert.assertEquals("unsupported", e.getMessage());
    }

    @Test
    public void handleCacheGetError_rethrows() {
        assertRethrown(() -> handler.handleCacheGetError(ex, cache, "k"));
    }

    @Test
    public void handleCachePutError_rethrows() {
        assertRethrown(() -> handler.handleCachePutError(ex, cache, "k", "v"));
    }

    @Test
    public void handleCacheEvictError_rethrows() {
        assertRethrown(() -> handler.handleCacheEvictError(ex, cache, "k"));
    }

    @Test
    public void handleCacheClearError_rethrows() {
        assertRethrown(() -> handler.handleCacheClearError(ex, cache));
    }

    private void assertRethrown(Runnable body) {
        try {
            body.run();
            org.junit.Assert.fail("应抛出原异常");
        } catch (RuntimeException thrown) {
            assertSame(ex, thrown);
        }
    }

    private static class StubCache implements FluxCache<Object, Object> {

        @Override
        public Object get(Object key, Callable<Object> valueLoader) {
            return null;
        }

        @Override
        public String getName() {
            return "stub";
        }

        @Override
        public ValueWrapper<Object> get(Object key) {
            return null;
        }

        @Override
        public Object get(Object key, Class<Object> type) {
            return null;
        }

        @Override
        public java.util.Map<Object, Object> getAll(java.util.List<Object> keys, Class<Object> type) {
            return null;
        }

        @Override
        public java.util.Map<Object, Object> getAllAsync(java.util.List<Object> keys, Class<Object> type) {
            return null;
        }

        @Override
        public void put(Object key, Object value) {
        }

        @Override
        public void putAll(java.util.Map<Object, Object> object) {
        }

        @Override
        public void putAllAsync(java.util.Map<Object, Object> object) {
        }

        @Override
        public void evict(Object key) {
        }

        @Override
        public void batchEvict(java.util.List<Object> keys) {
        }

        @Override
        public void clear() {
        }

        @Override
        public boolean allowCacheNull() {
            return true;
        }
    }
}