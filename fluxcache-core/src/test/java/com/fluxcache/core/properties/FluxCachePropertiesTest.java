package com.fluxcache.core.properties;

import com.fluxcache.core.enums.FluxCacheLevel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 全局缓存属性：缓存级别回退与 namespace 回退。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCachePropertiesTest {

    private final FluxCacheProperties properties = new FluxCacheProperties();

    @Test
    public void fluxCacheLevel_null_fallsBackToDefault() {
        properties.setDefaultCacheLevel(FluxCacheLevel.SecondaryCacheable);
        assertEquals(FluxCacheLevel.SecondaryCacheable, properties.fluxCacheLevel(null));
    }

    @Test
    public void fluxCacheLevel_explicit_kept() {
        assertEquals(FluxCacheLevel.FirstCacheable, properties.fluxCacheLevel(FluxCacheLevel.FirstCacheable));
    }

    @Test
    public void namespace_set_returnsNamespace() {
        properties.setNamespace("my-ns");
        properties.setApplicationName("app");
        assertEquals("my-ns", properties.namespace());
    }
}
