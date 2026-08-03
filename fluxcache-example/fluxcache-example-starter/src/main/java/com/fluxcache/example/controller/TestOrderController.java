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
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.example.config.OrderMyFluxCacheDataRegistered;
import com.fluxcache.example.vo.OrderVO;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author : wh
 * @date : 2024/11/16 16:15
 * @description:
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class TestOrderController {

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
    @FluxCacheable(cacheName = "orderCacheByCaffeine", key = "#name")
    public List<OrderVO> firstCacheByCaffeine(String name) {
        return mockSelectSql();
    }

    /**
     * 本地缓存 测试Optional
     *
     * @param name
     * @return
     */
    @GetMapping("/firstCacheByCaffeineAndOptional")
    @FluxCacheable(cacheName = "orderCacheByCaffeineAndOptional", key = "#name")
    public Optional<List<OrderVO>> firstCacheByCaffeineAndOptional(String name) {
        return mockSelectSqlAndOptional();
    }

    @DeleteMapping("/deleteFirstCacheByCaffeineAndOptional")
    @FluxCacheEvict(cacheName = "orderCacheByCaffeineAndOptional", key = "#name")
    public void clearFirstCacheByCaffeineAndOptional(String name) {
        log.info("删除缓存");
    }

    @GetMapping("/redis")
    @FluxCacheable(cacheName = "orderRedis", key = "#name",
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS, ttl = 5L))
    public List<OrderVO> firstCacheByRedis(String name) {
        return mockSelectSql();
    }

    @GetMapping("/redis-bucket")
    @FluxCacheable(cacheName = "orderRedisBucket", key = "#name",
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS, ttl = 1L))
    public List<OrderVO> firstCacheByRedisBucket(String name) {
        return mockSelectSql();
    }

    @GetMapping("/redis-bucket-null")
    @FluxCacheable(cacheName = "orderRedisBucketNull", key = "#name",
        firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS, ttl = 1L))
    public List<OrderVO> firstNullCacheByRedisBucket(String name) {
        System.out.println("开始查询数据库");
        return null;
    }

    @DeleteMapping("/redis-bucket")
    @FluxCacheEvict(cacheName = "orderRedisBucket", key = "#name")
    public void deleteFirstCacheByRedisBucket(String name) {
        System.out.println("开始删除 redis-bucket 缓存");
    }

    @PutMapping("/redis-bucket")
    @FluxCachePut(cacheName = "orderRedisBucket", key = "#name")
    public List<OrderVO> putFirstCacheByRedisBucket(String name) {
        return mockSelectSql();

    }

    /**
     * 删除缓存
     *
     * @param name
     */
    @DeleteMapping("/deleteCache")
    @FluxCacheEvict(cacheName = "orderCacheByCaffeine", key = "#name")
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
    @FluxCachePut(cacheName = "orderCacheByCaffeine", key = "#aa")
    public List<OrderVO> firstCacheByCaffeinePutCache(String aa) {
        log.info("更新缓存");
        return Lists.newArrayList(new OrderVO(4L, "小奏技术44", RandomUtils.nextInt(1, 1000)), new OrderVO(5L, "小奏技术55", RandomUtils.nextInt(1, 1000)));
    }

    /**
     * 删除本地缓存 by name
     *
     * @param name
     */
    @DeleteMapping("/deleteCache/name")
    @FluxCacheEvict(cacheName = "orderCacheByCaffeine")
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
    @FluxCacheable(cacheName = "orderLocalRedis", key = "#name",
        firstCacheable = @FirstCacheable(ttl = 1L),
        secondaryCacheable = @SecondaryCacheable(enabled = true, ttl = 3L))
    public List<OrderVO> secondaryCacheByCaffeineRedis(String name) {
        return mockSelectSql();
    }

    @GetMapping("/test-null-firstCache")
    @FluxCacheable(cacheName = "orderTestNullFirstCache", key = "#name")
    public List<OrderVO> mockSelectSqlToNullByFirstCache(String name) {
        return mockSelectSqlToNull();
    }

    /**
     * 不缓存null
     * @param name
     * @return
     */
    @GetMapping("/test-no-null-firstCache")
    @FluxCacheable(cacheName = "orderTestNoNullFirstCache", key = "#name", allowCacheNull = false)
    public List<OrderVO> mockSelectSqlToNoNullByFirstCache(String name) {
        return mockSelectSqlToNull();
    }

    @GetMapping("/test-null-secondaryCache")
    @FluxCacheable(cacheName = "orderTestNullSecondaryCache", key = "#name",
        secondaryCacheable = @SecondaryCacheable(enabled = true, ttl = 5L))
    public List<OrderVO> mockSelectSqlToNullBySecondaryCache(String name) {
        return mockSelectSqlToNull();
    }

    @GetMapping("/test-null")
    @FluxCacheable(cacheName = "orderTestNull", key = "#name")
    public List<OrderVO> mockSelectSqlToNull(String name) {
        return mockSelectSqlToNull();
    }

    @PutMapping("/put")
    @FluxCachePut(cacheName = "order", key = "#name")
    public List<OrderVO> localCachePut(String name) {
        log.info("更新缓存");
        return Lists.newArrayList(new OrderVO(4L, "小奏技术44", RandomUtils.nextInt(1, 1000)), new OrderVO(5L, "小奏技术55", 55));
    }

    @PutMapping("/putRedis")
    @FluxCachePut(cacheName = "orderRedis", key = "#name")
    public List<OrderVO> redisCachePut(String name) {
        log.info("更新缓存");
        return Lists.newArrayList(new OrderVO(6L, "redis66", RandomUtils.nextInt(1, 1000)), new OrderVO(7L, "redis77", RandomUtils.nextInt(1, 1000)));
    }

    @GetMapping("/productManualCache")
    public List<OrderVO> productManualCache(String name) {
        List<OrderVO> orderVOS = cacheManager.getCacheOrPut(OrderMyFluxCacheDataRegistered.PRODUCT_MANUAL_CACHE, name, this::mockSelectSql);
        return orderVOS;
    }

    @GetMapping("/productManualMultiLevelCache")
    public List<OrderVO> productManualMultiLevelCache(String name) {
        List<OrderVO> orderVOS = cacheManager.getCacheOrPut(OrderMyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE, name, this::mockSelectSql);
        return orderVOS;

    }

    @GetMapping("/getAllManualMultiLevelCache")
    public Map<String, List> getAllManualMultiLevelCache(String name, boolean isAsync) {
        FluxCache<String, List> cache = cacheManager.getCache(OrderMyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE);
        if (isAsync) {
            return cache.getAllAsync(Arrays.asList(name + "1", name + "2"), List.class);
        } else {
            return cache.getAll(Arrays.asList(name + "1", name + "2"), List.class);
        }
    }

    @GetMapping("/putAllManualMultiLevelCache")
    public void putAllManualMultiLevelCache(String name, boolean isAsync) {
        FluxCache<String, List<OrderVO>> cache = cacheManager.getCache(OrderMyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE);
        Map<String, List<OrderVO>> map = new HashMap<>();
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
        FluxCache<String, List> cache = cacheManager.getCache(OrderMyFluxCacheDataRegistered.PRODUCT_Redis_First_CACHE);
        if (isAsync) {
            return cache.getAllAsync(Arrays.asList(name + "1", name + "2"), List.class);
        } else {
            return cache.getAll(Arrays.asList(name + "1", name + "2"), List.class);
        }
    }

    @GetMapping("/pullAllRedisFirstCache")
    public void pullAllRedisFirstCache(String name, boolean isAsync) {
        FluxCache<String, List<OrderVO>> cache = cacheManager.getCache(OrderMyFluxCacheDataRegistered.PRODUCT_Redis_First_CACHE);
        Map<String, List<OrderVO>> map = new HashMap<>();
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
        FluxCache<String, List> cache = cacheManager.getCache(OrderMyFluxCacheDataRegistered.PRODUCT_LOCAL_FIRST_CACHE);
        if (isAsync) {
            return cache.getAllAsync(Arrays.asList(name + "1", name + "2"), List.class);
        } else {
            return cache.getAll(Arrays.asList(name + "1", name + "2"), List.class);
        }
    }

    @GetMapping("/pullAllLocalFirstCache")
    public void pullAllLocalFirstCache(String name, boolean isAsync) {
        FluxCache<String, List<OrderVO>> cache = cacheManager.getCache(OrderMyFluxCacheDataRegistered.PRODUCT_LOCAL_FIRST_CACHE);
        Map<String, List<OrderVO>> map = new HashMap<>();
        map.put(name + "1", mockSelectSql());
        map.put(name + "2", mockSelectSql());
        if (isAsync) {
            cache.putAllAsync(map);
        } else {
            cache.putAll(map);
        }
    }

    private List<OrderVO> mockSelectSql() {
        log.info("开始查询数据");
        return Lists.newArrayList(new OrderVO(1L, "小奏技术", RandomUtils.nextInt(1, 1000)), new OrderVO(2L, "小奏技术1", RandomUtils.nextInt(1, 10000)));
    }

    private List<OrderVO> mockSelectSqlToNull() {
        log.info("开始查询数据");
        return null;
    }

    private Optional<List<OrderVO>> mockSelectSqlAndOptional() {
        log.info("开始查询数据");
        return Optional.of(Lists.newArrayList(new OrderVO(1L, "小奏技术", RandomUtils.nextInt(1, 1000)), new OrderVO(2L, "小奏技术1", RandomUtils.nextInt(1, 10000))));    }

}
