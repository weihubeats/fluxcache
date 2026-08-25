package com.fluxcache.core.impl;

import com.fluxcache.core.model.FluxNullValue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多级缓存一致性回归：
 * - 驱逐/清空必须先删共享 L2 再动本地 L1（顺序反了会让并发读者从旧 L2 复活脏数据）
 * - L2→L1 回填走非发布路径，不得触发集群广播
 * - LocalCache 直写方法只作用于 L1
 *
 * @author : wh
 */
public class FluxMultiLevelCacheOrderTest {

    @SuppressWarnings("unchecked")
    private final FluxAbstractValueAdaptingCache<String, String> first =
            mock(FluxAbstractValueAdaptingCache.class);

    @SuppressWarnings("unchecked")
    private final FluxAbstractValueAdaptingCache<String, String> second =
            mock(FluxAbstractValueAdaptingCache.class);

    private FluxMultiLevelCache<String, String> cache;

    @Before
    public void setUp() {
        cache = new FluxMultiLevelCache<>(true, "order-cache", first, second);
    }

    @Test
    public void evict_deletesSecondaryBeforePrimary() {
        cache.evict("k");

        InOrder order = inOrder(second, first);
        order.verify(second).evict("k");
        order.verify(first).evict("k");
    }

    @Test
    public void batchEvict_deletesSecondaryBeforePrimary() {
        List<String> keys = Arrays.asList("a", "b");

        cache.batchEvict(keys);

        InOrder order = inOrder(second, first);
        order.verify(second).batchEvict(keys);
        order.verify(first).batchEvict(keys);
    }

    @Test
    public void clear_clearsSecondaryBeforePrimary() {
        // bucket 型 L2 的 clear 可能抛异常，必须发生在本地 L1 清空之前，否则状态不可预期
        cache.clear();

        InOrder order = inOrder(second, first);
        order.verify(second).clear();
        order.verify(first).clear();
    }

    @Test
    public void l2Promotion_usesNonPublishingDirectPut() {
        Map<String, String> l2Values = new HashMap<>();
        l2Values.put("k", "v");
        when(first.getValues(anyList())).thenReturn(new HashMap<>());
        when(second.getValues(anyList())).thenReturn(l2Values);

        cache.getAll(Arrays.asList("k"), String.class);

        // 回填禁止走会广播 put 事件的路径
        verify(first).putAllDirectly(l2Values);
        verify(first, never()).putAllAsync(anyMap());
    }

    @Test
    public void singleKeyPromotion_directPutIntoPrimary() {
        when(first.lookup("k")).thenReturn(null);
        when(second.lookup("k")).thenReturn("v");

        cache.get("k", String.class);

        verify(first).putDirectly(same("k"), eq("v"));
    }

    @Test
    public void putDirectly_targetsLocalOnly() {
        cache.putDirectly("k", "v");

        verify(first).putDirectly("k", "v");
        verify(second, never()).putDirectly(eq("k"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void clearDirectly_delegatesToLocalOnly() {
        when(first.clearDirectly()).thenReturn(true);

        boolean changed = cache.clearDirectly();

        org.junit.Assert.assertTrue(changed);
        verify(second, never()).clearDirectly();
        verify(first).clearDirectly();
    }

    @Test
    public void nullMarker_promotedRawAndReturnedAsHitWithNull() {
        // L2 存的是穿透保护标记：回填与返回都必须保留"命中但为 null"语义
        when(first.lookup("nk")).thenReturn(null);
        when(second.lookup("nk")).thenAnswer(inv -> FluxNullValue.INSTANCE);

        com.fluxcache.core.FluxCache.ValueWrapper<String> wrapper = cache.get("nk");

        org.junit.Assert.assertNotNull(wrapper);
        org.junit.Assert.assertNull(wrapper.get());
        verify(first).putDirectly(same("nk"), same(FluxNullValue.INSTANCE));
    }

    @Test
    public void getAll_emptyKeys_returnsEmptyWithoutTouchingLevels() {
        Map<String, String> res = cache.getAll(java.util.Collections.emptyList(), String.class);

        org.junit.Assert.assertTrue(res.isEmpty());
        verify(first, never()).getValues(anyList());
        verify(second, never()).getValues(anyList());
    }

    @Test
    public void getAll_allKeysInL1_skipsL2() {
        Map<String, String> l1 = new HashMap<>();
        l1.put("a", "1");
        when(first.getValues(anyList())).thenReturn(l1);

        Map<String, String> res = cache.getAll(Arrays.asList("a"), String.class);

        org.junit.Assert.assertEquals("1", res.get("a"));
        verify(second, never()).getValues(anyList());
        verify(first, never()).putAllDirectly(anyMap());
    }

    @Test
    public void getAll_l2EmptyResult_noPromotion() {
        when(first.getValues(anyList())).thenReturn(new HashMap<>());
        when(second.getValues(anyList())).thenReturn(new HashMap<>());

        Map<String, String> res = cache.getAll(Arrays.asList("missing"), String.class);

        org.junit.Assert.assertTrue(res.isEmpty());
        verify(first, never()).putAllDirectly(anyMap());
    }
}
