package com.fluxcache.core.annotation;

import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.properties.FluxCacheProperties;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 验证 @FluxCacheable 解析：
 * 1. 二级缓存通过 secondaryCacheable.enabled 自动推断
 * 2. 注解未设置的字段回落到全局 YAML 配置
 *
 * @author : wh
 */
public class FluxSpringCacheAnnotationParserTest {

    private FluxCacheProperties cacheProperties;

    private FluxSpringCacheAnnotationParser parser;

    @Before
    public void setUp() {
        cacheProperties = new FluxCacheProperties();
        cacheProperties.setDefaultCacheLevel(FluxCacheLevel.FirstCacheable);
        cacheProperties.setFirstCache(new FluxCacheProperties.FirstCacheConfig());
        cacheProperties.setSecondaryCache(new FluxCacheProperties.SecondaryCacheConfig());
        parser = new FluxSpringCacheAnnotationParser(cacheProperties);
    }

    @Test
    public void secondaryEnabled_inferredAsTwoLevel() throws Exception {
        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) parse("secondaryWithConfig");

        assertEquals(FluxCacheLevel.SecondaryCacheable, op.getFluxCacheLevel());
        assertNotNull(op.getSecondaryCacheConfig());
        assertEquals(Long.valueOf(5L), op.getSecondaryCacheConfig().getTtl());
        assertEquals(FluxCacheType.REDIS, op.getSecondaryCacheConfig().getCacheType());
    }

    @Test
    public void noSecondary_fallsBackToGlobalDefaultLevel() throws Exception {
        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) parse("plainFirstLevel");

        assertEquals(FluxCacheLevel.FirstCacheable, op.getFluxCacheLevel());
        assertNull(op.getSecondaryCacheConfig());
    }

    @Test
    public void unsetFields_fallBackToGlobalConfig() throws Exception {
        cacheProperties.getFirstCache().setTtl(7L);
        cacheProperties.getFirstCache().setTimeUnit(TimeUnit.SECONDS);
        cacheProperties.getFirstCache().setCacheType(FluxCacheType.REDIS);
        cacheProperties.getFirstCache().setInitSize(64);
        cacheProperties.getFirstCache().setMaxSize(5000);

        FluxCacheConfig cfg = firstConfigOf("unsetFields");

        assertEquals(Long.valueOf(7L), cfg.getTtl());
        assertEquals(TimeUnit.SECONDS, cfg.getUnit());
        assertEquals(FluxCacheType.REDIS, cfg.getCacheType());
        assertEquals(64, cfg.getInitSize());
        assertEquals(5000, cfg.getMaxSize());
    }

    @Test
    public void annotationOverridesGlobalConfig() throws Exception {
        cacheFieldConfig(FluxCacheType.CAFFEINE, 30L);

        FluxCacheConfig cfg = firstConfigOf("overrideGlobal");

        assertEquals(Long.valueOf(5L), cfg.getTtl());
        assertEquals(TimeUnit.MINUTES, cfg.getUnit());
        assertEquals(1000, cfg.getMaxSize());
    }

    @Test
    public void nothingConfigured_useBuiltinDefaults() throws Exception {
        cacheProperties.setFirstCache(null);
        cacheProperties.setSecondaryCache(null);

        FluxCacheConfig first = firstConfigOf("secondaryWithRedisson");
        assertEquals(Long.valueOf(FluxCacheConfig.DEFAULT_TTL), first.getTtl());
        assertEquals(FluxCacheType.CAFFEINE, first.getCacheType());
        assertEquals(FluxCacheConfig.DEFAULT_INIT_SIZE, first.getInitSize());
        assertEquals(FluxCacheConfig.DEFAULT_MAX_SIZE, first.getMaxSize());

        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) parse("secondaryWithRedisson");
        assertEquals(FluxCacheType.REDIS, op.getSecondaryCacheConfig().getCacheType());
    }

    @Test
    public void keyAndAllowNullCarried() throws Exception {
        FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable) parse("noCacheAndKey");

        assertEquals("user", op.getCacheName());
        assertEquals("#id", op.getKey());
        assertEquals(false, op.isAllowCacheNull());
    }

    private FluxCacheConfig firstConfigOf(String methodName) throws Exception {
        return ((FluxMultilevelCacheCacheable) parse(methodName)).getFirstCacheConfig();
    }

    private FluxCacheOperation parse(String methodName) throws Exception {
        Method method = Sample.class.getMethod(methodName, String.class);
        return parser.parseCacheAnnotation(method);
    }

    private void cacheFieldConfig(FluxCacheType type, long ttl) {
        cacheProperties.getFirstCache().setCacheType(type);
        cacheProperties.getFirstCache().setTtl(ttl);
    }

    static class Sample {

        @FluxCacheable(cacheName = "x", key = "#name",
                secondaryCacheable = @SecondaryCacheable(enabled = true, ttl = 5L))
        public String secondaryWithConfig(String name) {
            return name;
        }

        @FluxCacheable(cacheName = "y", key = "#name")
        public String plainFirstLevel(String name) {
            return name;
        }

        @FluxCacheable(cacheName = "z", key = "#name")
        public String unsetFields(String name) {
            return name;
        }

        @FluxCacheable(cacheName = "z", key = "#name",
                firstCacheable = @FirstCacheable(ttl = 5L, maxSize = 1000))
        public String overrideGlobal(String name) {
            return name;
        }

        @FluxCacheable(cacheName = "zz", key = "#name",
                secondaryCacheable = @SecondaryCacheable(enabled = true))
        public String secondaryWithRedisson(String name) {
            return name;
        }

        @FluxCacheable(cacheName = "user", key = "#id", allowCacheNull = false)
        public String noCacheAndKey(String id) {
            return id;
        }
    }
}