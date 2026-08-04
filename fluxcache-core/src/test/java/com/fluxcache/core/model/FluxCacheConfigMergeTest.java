package com.fluxcache.core.model;

import com.fluxcache.core.annotation.FirstCacheable;
import com.fluxcache.core.annotation.SecondaryCacheable;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.properties.FluxCacheProperties.FirstCacheConfig;
import com.fluxcache.core.properties.FluxCacheProperties.SecondaryCacheConfig;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 缓存配置合并：注解优先、全局回落、内置默认值兜底；空值占位对象语义。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxCacheConfigMergeTest {

    @Test
    public void from_firstCacheable_annotationWins() {
        FirstCacheable anno = new FirstCacheable() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return FirstCacheable.class;
            }

            @Override
            public long ttl() {
                return 5;
            }

            @Override
            public TimeUnit unit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public int initSize() {
                return 8;
            }

            @Override
            public int maxSize() {
                return 50;
            }

            @Override
            public FluxCacheType fluxCacheType() {
                return FluxCacheType.CAFFEINE;
            }
        };

        FluxCacheConfig cfg = FluxCacheConfig.from(anno, new FirstCacheConfig());

        assertEquals(5L, cfg.getTtl().longValue());
        assertEquals(TimeUnit.SECONDS, cfg.getUnit());
        assertEquals(8, cfg.getInitSize());
        assertEquals(50, cfg.getMaxSize());
        assertEquals(FluxCacheType.CAFFEINE, cfg.getCacheType());
    }

    @Test
    public void from_firstCacheable_emptyAnnotation_fallsBackToGlobalAndDefaults() {
        FirstCacheable empty = new FirstCacheable() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return FirstCacheable.class;
            }

            @Override
            public long ttl() {
                return 0;
            }

            @Override
            public TimeUnit unit() {
                return TimeUnit.MINUTES;
            }

            @Override
            public int initSize() {
                return -1;
            }

            @Override
            public int maxSize() {
                return -1;
            }

            @Override
            public FluxCacheType fluxCacheType() {
                return FluxCacheType.NULL;
            }
        };

        FirstCacheConfig global = new FirstCacheConfig();
        global.setTtl(20);
        global.setTimeUnit(TimeUnit.HOURS);
        global.setInitSize(32);
        global.setMaxSize(256);
        global.setCacheType(FluxCacheType.CAFFEINE);

        FluxCacheConfig cfg = FluxCacheConfig.from(empty, global);

        assertEquals(20L, cfg.getTtl().longValue());
        assertEquals(TimeUnit.HOURS, cfg.getUnit());
        assertEquals(32, cfg.getInitSize());
        assertEquals(256, cfg.getMaxSize());
        assertEquals(FluxCacheType.CAFFEINE, cfg.getCacheType());
    }

    @Test
    public void from_firstCacheable_noGlobal_usesBuiltinDefaults() {
        FirstCacheable empty = new FirstCacheable() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return FirstCacheable.class;
            }

            @Override
            public long ttl() {
                return 0;
            }

            @Override
            public TimeUnit unit() {
                return TimeUnit.MINUTES;
            }

            @Override
            public int initSize() {
                return -1;
            }

            @Override
            public int maxSize() {
                return -1;
            }

            @Override
            public FluxCacheType fluxCacheType() {
                return FluxCacheType.NULL;
            }
        };

        FluxCacheConfig cfg = FluxCacheConfig.from(empty, null);

        assertEquals(FluxCacheConfig.DEFAULT_TTL, cfg.getTtl().longValue());
        assertEquals(TimeUnit.MINUTES, cfg.getUnit());
        assertEquals(FluxCacheConfig.DEFAULT_INIT_SIZE, cfg.getInitSize());
        assertEquals(FluxCacheConfig.DEFAULT_MAX_SIZE, cfg.getMaxSize());
        assertEquals(FluxCacheType.CAFFEINE, cfg.getCacheType());
    }

    @Test
    public void from_secondaryCacheable_defaultTypeRedisAndDefaults() {
        SecondaryCacheable anno = new SecondaryCacheable() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return SecondaryCacheable.class;
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public long ttl() {
                return 0;
            }

            @Override
            public TimeUnit unit() {
                return TimeUnit.MINUTES;
            }

            @Override
            public int initSize() {
                return -1;
            }

            @Override
            public int maxSize() {
                return -1;
            }

            @Override
            public FluxCacheType fluxCacheType() {
                return FluxCacheType.NULL;
            }
        };

        FluxCacheConfig cfg = FluxCacheConfig.from(anno, new SecondaryCacheConfig());

        assertEquals(FluxCacheConfig.DEFAULT_TTL, cfg.getTtl().longValue());
        assertEquals(FluxCacheType.REDIS, cfg.getCacheType());
    }

    @Test
    public void builder_andDefaultConstants() {
        FluxCacheConfig cfg = new FluxCacheConfig.Builder()
                .setTtl(1L)
                .setInitSize(2)
                .setUnit(TimeUnit.HOURS)
                .setMaxSize(3)
                .setCacheType(FluxCacheType.CAFFEINE)
                .build();
        FluxCacheConfig second = new FluxCacheConfig.Builder()
                .setTtl(1L)
                .setInitSize(2)
                .setUnit(TimeUnit.HOURS)
                .setMaxSize(3)
                .setCacheType(FluxCacheType.CAFFEINE)
                .build();
        assertEquals(cfg, second);
    }

    @Test
    public void nullValue_singletonSemantics() {
        assertNull(FluxNullValue.INSTANCE.equals(null) ? null : FluxNullValue.INSTANCE);
        assertTrue(FluxNullValue.INSTANCE.equals(FluxNullValue.INSTANCE));
        assertEquals(FluxNullValue.class.hashCode(), FluxNullValue.INSTANCE.hashCode());
        assertEquals("null", FluxNullValue.INSTANCE.toString());
    }
}