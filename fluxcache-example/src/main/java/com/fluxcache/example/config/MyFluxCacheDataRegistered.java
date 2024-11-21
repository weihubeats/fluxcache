package com.fluxcache.example.config;

import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.manual.FluxCacheDataRegistered;
import com.fluxcache.core.model.FluxCacheCacheableConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * @author : wh
 * @date : 2024/11/17 17:37
 * @description:
 */
@Component
public class MyFluxCacheDataRegistered implements FluxCacheDataRegistered {

    public static final String PRODUCT_MANUAL_CACHE = "productManualCache";

    public static final String PRODUCT_MANUAL_MultiLevel_CACHE = "productManualMultiLevelCache";

    public static final String PRODUCT_Redis_First_CACHE = "productRedisFirstCache";

    public static final String PRODUCT_LOCAL_FIRST_CACHE = "productLocalFirstCache";

    @Override
    public List<FluxMultilevelCacheCacheable> registerCache() {
        List<FluxMultilevelCacheCacheable> cacheables = new ArrayList<>();
        FluxCacheCacheableConfig build = new FluxCacheCacheableConfig.Builder()
            .setCacheType(FluxCacheType.CAFFEINE)
            .setMaxSize(100)
            .setTtl(10L)
            .setInitSize(10)
            .setUnit(TimeUnit.SECONDS)
            .build();

        FluxMultilevelCacheCacheable cacheable = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
            .setCacheName(PRODUCT_MANUAL_CACHE)
            .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
            .setFirstCacheConfig(build)
            .build();


        FluxCacheCacheableConfig build1 = new FluxCacheCacheableConfig.Builder()
            .setCacheType(FluxCacheType.CAFFEINE)
            .setMaxSize(100)
            .setTtl(10L)
            .setInitSize(10)
            .setUnit(TimeUnit.SECONDS)
            .build();

        FluxCacheCacheableConfig build2 = new FluxCacheCacheableConfig.Builder()
            .setCacheType(FluxCacheType.REDIS_R_MAP)
            .setMaxSize(100)
            .setTtl(10L)
            .setInitSize(10)
            .setUnit(TimeUnit.SECONDS)
            .build();


        FluxMultilevelCacheCacheable cacheable1 = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
            .setCacheName(PRODUCT_MANUAL_MultiLevel_CACHE)
            .setFluxCacheLevel(FluxCacheLevel.SecondaryCacheable)
            .setFirstCacheConfig(build1)
            .setSecondaryCacheConfig(build2)
            .build();

        FluxCacheCacheableConfig RedisFirst = new FluxCacheCacheableConfig.Builder()
            .setCacheType(FluxCacheType.REDIS_R_MAP)
            .setMaxSize(100)
            .setTtl(10L)
            .setInitSize(10)
            .setUnit(TimeUnit.SECONDS)
            .build();

        FluxMultilevelCacheCacheable redisFirstCacheable = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
            .setCacheName(PRODUCT_Redis_First_CACHE)
            .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
            .setFirstCacheConfig(RedisFirst)
            .build();

        FluxCacheCacheableConfig localFirst = new FluxCacheCacheableConfig.Builder()
            .setCacheType(FluxCacheType.CAFFEINE)
            .setMaxSize(100)
            .setTtl(10L)
            .setInitSize(10)
            .setUnit(TimeUnit.SECONDS)
            .build();

        FluxMultilevelCacheCacheable localFirstCacheable = new FluxMultilevelCacheCacheable.CacheConfigBuilder()
            .setCacheName(PRODUCT_LOCAL_FIRST_CACHE)
            .setFluxCacheLevel(FluxCacheLevel.FirstCacheable)
            .setFirstCacheConfig(localFirst)
            .build();

        cacheables.add(cacheable);
        cacheables.add(cacheable1);
        cacheables.add(redisFirstCacheable);
        cacheables.add(localFirstCacheable);
        return cacheables;
    }
}
