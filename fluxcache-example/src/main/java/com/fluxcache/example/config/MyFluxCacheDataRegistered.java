package com.fluxcache.example.config;

import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.manual.FluxCacheDataRegistered;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        FluxCacheConfig build = new FluxCacheConfig.Builder()
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


        FluxCacheConfig build1 = new FluxCacheConfig.Builder()
                .setCacheType(FluxCacheType.CAFFEINE)
                .setMaxSize(100)
                .setTtl(10L)
                .setInitSize(10)
                .setUnit(TimeUnit.SECONDS)
                .build();

        FluxCacheConfig build2 = new FluxCacheConfig.Builder()
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

        FluxCacheConfig RedisFirst = new FluxCacheConfig.Builder()
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

        FluxCacheConfig localFirst = new FluxCacheConfig.Builder()
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
