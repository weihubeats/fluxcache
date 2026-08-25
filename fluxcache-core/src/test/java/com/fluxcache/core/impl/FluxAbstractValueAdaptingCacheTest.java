package com.fluxcache.core.impl;

import com.fluxcache.core.model.FluxNullValue;
import org.junit.Before;
import org.junit.Test;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Null 适配模板的边界分支：null 策略、类型校验、集合入参防御。
 *
 * @author : wh
 */
public class FluxAbstractValueAdaptingCacheTest {

    private FluxAbstractValueAdaptingCache<String, String> allowNullCache;

    @SuppressWarnings("unchecked")
    private FluxAbstractValueAdaptingCache<String, String> noNullCache;

    @Before
    public void setUp() {
        allowNullCache = new StubCache<>(true, "allow-null");
        noNullCache = new StubCache<>(false, "no-null");
    }

    @Test(expected = IllegalStateException.class)
    public void get_typeMismatch_throws() {
        allowNullCache.put("k", "str");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Class rawWrongType = Integer.class;
        allowNullCache.get("k", rawWrongType);
    }

    @Test
    public void get_typeMatch_returnsValue() {
        allowNullCache.put("k", "v");
        org.junit.Assert.assertEquals("v", allowNullCache.get("k", String.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAll_nullKeys_throws() {
        allowNullCache.getAll(null, String.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAllAsync_nullKeys_throws() {
        allowNullCache.getAllAsync(null, String.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void putAll_nullMap_throws() {
        allowNullCache.putAll(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void putAllAsync_nullMap_throws() {
        allowNullCache.putAllAsync(null);
    }

    @Test
    public void put_nullValue_disallowed_silentlySkipped() {
        // allowCacheNull=false 时 put(null) 静默跳过，不写缓存不抛异常
        noNullCache.put("k", null);
        org.junit.Assert.assertNull(noNullCache.get("k"));
    }

    @Test
    public void fromStoreValue_markerConvertedOnlyWhenAllowed() {
        org.junit.Assert.assertNull(
                ((StubCache<String, String>) allowNullCache).fromStoreValueForTest(FluxNullValue.INSTANCE));
        org.junit.Assert.assertSame(FluxNullValue.INSTANCE,
                ((StubCache<String, String>) noNullCache).fromStoreValueForTest(FluxNullValue.INSTANCE));
    }

    /**
     * 最小实现：内存 map 存 store 值。
     */
    private static final class StubCache<K, V> extends FluxAbstractValueAdaptingCache<K, V> {

        private final Map<K, Object> store = new java.util.HashMap<>();

        StubCache(boolean allowCacheNull, String name) {
            super(allowCacheNull, name);
        }

        @Override
        protected Map<K, V> getValues(java.util.List<K> keys) {
            Map<K, V> res = new java.util.HashMap<>();
            for (K key : keys) {
                Object v = store.get(key);
                if (v != null) {
                    res.put(key, (V) v);
                }
            }
            return res;
        }

        @Override
        protected Map<K, V> getValuesAsync(java.util.List<K> keys) {
            return getValues(keys);
        }

        @Override
        protected void putValues(@Nullable Map<K, V> map) {
            if (map != null) {
                store.putAll(map);
            }
        }

        @Override
        protected void putValuesAsync(@Nullable Map<K, V> map) {
            putValues(map);
        }

        @Override
        protected V getValue(K key, Callable<V> valueLoader) {
            return (V) store.get(key);
        }

        @Override
        protected void putValue(K key, Object value) {
            store.put(key, value);
        }

        @Override
        protected void evictValue(K key) {
            store.remove(key);
        }

        @Override
        protected void batchEvictValue(java.util.List<K> keys) {
            keys.forEach(store::remove);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected V lookup(K key) {
            return (V) store.get(key);
        }

        @Override
        public void clear() {
            store.clear();
        }

        @Nullable
        V fromStoreValueForTest(Object storeValue) {
            return fromStoreValue((V) storeValue);
        }
    }
}
