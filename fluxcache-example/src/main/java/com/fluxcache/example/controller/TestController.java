package com.fluxcache.example.controller;

import com.fluxcache.admin.vo.FluxCacheAllStaticsVO;
import com.fluxcache.admin.vo.FluxCacheValueVO;
import com.fluxcache.core.DefaultFluxCacheManager;
import com.fluxcache.core.FluxCache;
import com.fluxcache.core.annotation.FirstCacheable;
import com.fluxcache.core.annotation.FluxCacheEvict;
import com.fluxcache.core.annotation.FluxCachePut;
import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.SecondaryCacheable;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.example.config.MyFluxCacheDataRegistered;
import com.fluxcache.example.vo.StudentVO;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : wh
 * @date : 2024/11/16 16:15
 * @description:
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class TestController {

    private final DefaultFluxCacheManager cacheManager;

    @GetMapping("/getAllStatics")
    public FluxCacheAllStaticsVO getAllStatics(@RequestParam String cacheName) {
        FluxCacheAllStaticsVO vo = new FluxCacheAllStaticsVO(cacheName, cacheManager.getCacheStatics(cacheName));
        return vo;
    }

    @GetMapping("/test1")
    public Object getCacheValue(String cacheName, String key) {
        FluxCacheValueVO vo = new FluxCacheValueVO();
        FluxCache<String, Object> cache = cacheManager.getCache(cacheName);
        if (Objects.nonNull(cache)) {
            FluxCache.ValueWrapper<Object> wrapper = cache.get(key);
            if (Objects.nonNull(wrapper)) {
                vo.setValue(wrapper.get());
                vo.setFlag(true);
                return vo;
            }
        }
        vo.setFlag(false);
        return vo;
    }

    @GetMapping("/test")
    @FluxCacheable(cacheName = "firstCacheByCaffeine", key = "#name",
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.CAFFEINE, ttl = 5L, unit = TimeUnit.MINUTES, maxSize = 2000, initSize = 20))
    public List<StudentVO> firstCacheByCaffeine(String name) {
        return mockSelectSql();
    }

    /**
     * 本地缓存 测试Optional
     *
     * @param name
     * @return
     */
    @GetMapping("/firstCacheByCaffeineAndOptional")
    @FluxCacheable(cacheName = "firstCacheByCaffeineAndOptional", key = "#name",
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.CAFFEINE, ttl = 5L, unit = TimeUnit.MINUTES, maxSize = 2000, initSize = 20))
    public Optional<List<StudentVO>> firstCacheByCaffeineAndOptional(String name) {
        return mockSelectSqlAndOptional();
    }

    @DeleteMapping("/deleteFirstCacheByCaffeineAndOptional")
    @FluxCacheEvict(cacheName = "firstCacheByCaffeineAndOptional", key = "#name")
    public void clearFirstCacheByCaffeineAndOptional(String name) {
        log.info("删除缓存");
    }

    @GetMapping("/redis")
    @FluxCacheable(cacheName = "studentRedis", key = "#name", fluxCacheLevel = FluxCacheLevel.FirstCacheable,
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS_R_MAP, ttl = 5L))
    public List<StudentVO> firstCacheByRedis(String name) {
        return mockSelectSql();
    }

    @GetMapping("/redis-bucket")
    @FluxCacheable(cacheName = "studentRedisBucket", key = "#name", fluxCacheLevel = FluxCacheLevel.FirstCacheable,
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS_BUCKET, ttl = 1L))
    public List<StudentVO> firstCacheByRedisBucket(String name) {
        return mockSelectSql();
    }

    @GetMapping("/redis-bucket-null")
    @FluxCacheable(cacheName = "studentRedisBucketNull", key = "#name", fluxCacheLevel = FluxCacheLevel.FirstCacheable,
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS_BUCKET, ttl = 1L))
    public List<StudentVO> firstNullCacheByRedisBucket(String name) {
        System.out.println("开始查询数据库");
        return null;
    }

    @DeleteMapping("/redis-bucket")
    @FluxCacheEvict(cacheName = "studentRedisBucket", key = "#name")
    public void deleteFirstCacheByRedisBucket(String name) {
        System.out.println("开始删除 redis-bucket 缓存");
    }

    @PutMapping("/redis-bucket")
    @FluxCachePut(cacheName = "studentRedisBucket", key = "#name")
    public List<StudentVO> putFirstCacheByRedisBucket(String name) {
        return mockSelectSql();

    }

    /**
     * 删除缓存
     *
     * @param name
     */
    @DeleteMapping("/deleteCache")
    @FluxCacheEvict(cacheName = "firstCacheByCaffeine", key = "#name")
    public void clearFirstCacheByCaffeineByKey(String name) {
        log.info("删除缓存");
    }

    /**
     * 更新本地缓存
     *
     * @param aa
     * @return
     */
    @PutMapping("/firstCacheByCaffeinePutCache")
    @FluxCachePut(cacheName = "firstCacheByCaffeine", key = "#aa")
    public List<StudentVO> firstCacheByCaffeinePutCache(String aa) {
        log.info("更新缓存");
        return Lists.newArrayList(new StudentVO(4L, "小奏技术44", RandomUtils.nextInt(1, 1000)), new StudentVO(5L, "小奏技术55", RandomUtils.nextInt(1, 1000)));
    }

    /**
     * 删除本地缓存 by name
     *
     * @param name
     */
    @DeleteMapping("/deleteCache/name")
    @FluxCacheEvict(cacheName = "firstCacheByCaffeine")
    public void clearFirstCacheByCaffeineByName(String name) {
        log.info("删除缓存");
    }

    /**
     * 二级缓存 本地 caffeine 二级 redis
     *
     * @param name
     * @return
     */
    @GetMapping("/local-redis")
    @FluxCacheable(cacheName = "studentLocalRedis", key = "#name", fluxCacheLevel = FluxCacheLevel.SecondaryCacheable,
        firstCacheable = @FirstCacheable(ttl = 1L, fluxCacheType = FluxCacheType.CAFFEINE, maxSize = 2000, initSize = 20),
        secondaryCacheable = @SecondaryCacheable(ttl = 3L, fluxCacheType = FluxCacheType.REDIS_BUCKET))
    public List<StudentVO> secondaryCacheByCaffeineRedis(String name) {
        return mockSelectSql();
    }

    @GetMapping("/test-null-firstCache")
    @FluxCacheable(cacheName = "testNullFirstCache", key = "#name",
        fluxCacheLevel = FluxCacheLevel.FirstCacheable,
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.CAFFEINE, ttl = 5L, unit = TimeUnit.MINUTES, maxSize = 2000, initSize = 20))
    public List<StudentVO> mockSelectSqlToNullByFirstCache(String name) {
        return mockSelectSqlToNull();
    }

    @GetMapping("/test-null-secondaryCache")
    @FluxCacheable(cacheName = "testNullSecondaryCache", key = "#name",
        fluxCacheLevel = FluxCacheLevel.SecondaryCacheable,
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.CAFFEINE, ttl = 5L, unit = TimeUnit.MINUTES, maxSize = 2000, initSize = 20),
        secondaryCacheable = @SecondaryCacheable(ttl = 5L, fluxCacheType = FluxCacheType.REDIS_BUCKET))
    public List<StudentVO> mockSelectSqlToNullBySecondaryCache(String name) {
        return mockSelectSqlToNull();
    }

    @GetMapping("/test-null")
    @FluxCacheable(cacheName = "testNull", key = "#name",
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.CAFFEINE, ttl = 5L, unit = TimeUnit.MINUTES, maxSize = 2000, initSize = 20))
    public List<StudentVO> mockSelectSqlToNull(String name) {
        return mockSelectSqlToNull();
    }

    @PutMapping("/put")
    @FluxCachePut(cacheName = "student", key = "#name")
    public List<StudentVO> localCachePut(String name) {
        log.info("更新缓存");
        return Lists.newArrayList(new StudentVO(4L, "小奏技术44", RandomUtils.nextInt(1, 1000)), new StudentVO(5L, "小奏技术55", 55));
    }

    @PutMapping("/putRedis")
    @FluxCachePut(cacheName = "studentRedis", key = "#name")
    public List<StudentVO> redisCachePut(String name) {
        log.info("更新缓存");
        return Lists.newArrayList(new StudentVO(6L, "redis66", RandomUtils.nextInt(1, 1000)), new StudentVO(7L, "redis77", RandomUtils.nextInt(1, 1000)));
    }

    @GetMapping("/productManualCache")
    public List<StudentVO> productManualCache(String name) {
        List<StudentVO> studentVOS = cacheManager.getCacheOrPut(MyFluxCacheDataRegistered.PRODUCT_MANUAL_CACHE, name, this::mockSelectSql);
        return studentVOS;
    }

    @GetMapping("/productManualMultiLevelCache")
    public List<StudentVO> productManualMultiLevelCache(String name) {
        List<StudentVO> studentVOS = cacheManager.getCacheOrPut(MyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE, name, this::mockSelectSql);
        return studentVOS;

    }

    @GetMapping("/getAllManualMultiLevelCache")
    public Map<String, List> getAllManualMultiLevelCache(String name, boolean isAsync) {
        FluxCache<String, List> cache = cacheManager.getCache(MyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE);
        if (isAsync) {
            return cache.getAllAsync(Arrays.asList(name + "1", name + "2"), List.class);
        } else {
            return cache.getAll(Arrays.asList(name + "1", name + "2"), List.class);
        }
    }

    @GetMapping("/putAllManualMultiLevelCache")
    public void putAllManualMultiLevelCache(String name, boolean isAsync) {
        FluxCache<String, List<StudentVO>> cache = cacheManager.getCache(MyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE);
        Map<String, List<StudentVO>> map = new HashMap<>();
        map.put(name + "1", mockSelectSql());
        map.put(name + "2", mockSelectSql());
        if (isAsync) {
            cache.putAllAsync(map);
        } else {
            cache.putAll(map);
        }
    }

    @GetMapping("/getAllRedisFirstCache")
    public Map<String, List> getAllRedisFirstCache(String name, boolean isAsync) {
        FluxCache<String, List> cache = cacheManager.getCache(MyFluxCacheDataRegistered.PRODUCT_Redis_First_CACHE);
        if (isAsync) {
            return cache.getAllAsync(Arrays.asList(name + "1", name + "2"), List.class);
        } else {
            return cache.getAll(Arrays.asList(name + "1", name + "2"), List.class);
        }
    }

    @GetMapping("/pullAllRedisFirstCache")
    public void pullAllRedisFirstCache(String name, boolean isAsync) {
        FluxCache<String, List<StudentVO>> cache = cacheManager.getCache(MyFluxCacheDataRegistered.PRODUCT_Redis_First_CACHE);
        Map<String, List<StudentVO>> map = new HashMap<>();
        map.put(name + "1", mockSelectSql());
        map.put(name + "2", mockSelectSql());
        if (isAsync) {
            cache.putAllAsync(map);
        } else {
            cache.putAll(map);
        }
    }

    @GetMapping("/getAllLocalFirstCache")
    public Map<String, List> getAllLocalFirstCache(String name, boolean isAsync) {
        FluxCache<String, List> cache = cacheManager.getCache(MyFluxCacheDataRegistered.PRODUCT_LOCAL_FIRST_CACHE);
        if (isAsync) {
            return cache.getAllAsync(Arrays.asList(name + "1", name + "2"), List.class);
        } else {
            return cache.getAll(Arrays.asList(name + "1", name + "2"), List.class);
        }
    }

    @GetMapping("/pullAllLocalFirstCache")
    public void pullAllLocalFirstCache(String name, boolean isAsync) {
        FluxCache<String, List<StudentVO>> cache = cacheManager.getCache(MyFluxCacheDataRegistered.PRODUCT_LOCAL_FIRST_CACHE);
        Map<String, List<StudentVO>> map = new HashMap<>();
        map.put(name + "1", mockSelectSql());
        map.put(name + "2", mockSelectSql());
        if (isAsync) {
            cache.putAllAsync(map);
        } else {
            cache.putAll(map);
        }
    }

    private List<StudentVO> mockSelectSql() {
        log.info("开始查询数据");
        return Lists.newArrayList(new StudentVO(1L, "小奏技术", RandomUtils.nextInt(1, 1000)), new StudentVO(2L, "小奏技术1", RandomUtils.nextInt(1, 1000)));
    }

    private List<StudentVO> mockSelectSqlToNull() {
        log.info("开始查询数据");
        return null;
    }

    private Optional<List<StudentVO>> mockSelectSqlAndOptional() {
        log.info("开始查询数据");
        return Optional.of(Lists.newArrayList(new StudentVO(1L, "小奏技术", RandomUtils.nextInt(1, 1000)), new StudentVO(2L, "小奏技术1", RandomUtils.nextInt(1, 1000))));
    }

}
