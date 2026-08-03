package com.fluxcache.example.controller;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.example.FluxCacheApplication;
import com.fluxcache.example.config.OrderManuallyRefreshCache;
import com.fluxcache.example.config.OrderMyFluxCacheDataRegistered;
import com.fluxcache.example.service.OrderMultipleKeysProvider;
import com.fluxcache.example.service.OrderProviderService;
import com.fluxcache.example.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author : wh
 * @date : 2025/9/16 14:39
 * @description:
 */
@SpringBootTest(classes = FluxCacheApplication.class)
@Slf4j
@Testcontainers
public class TestCacheProvider {

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:6.2.6"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("redis.host", redis::getHost);
        registry.add("redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("redis.password", () -> "");
    }

    @Autowired
    private OrderProviderService orderProviderService;

    @Autowired
    private FluxCacheManager cacheManager;

    @Test
    public void testRefreshCacheByNoParam() throws Exception {
        String cacheName = "orderTestRefreshCache";
        String key = "all";

        List<OrderVO> before = orderProviderService.testRefreshCache();
        Assertions.assertNotNull(before);
        Assertions.assertFalse(before.isEmpty());

        cacheManager.getCache(cacheName).evict(key);
        List<OrderVO> afterEvict = orderProviderService.testRefreshCache();
        Assertions.assertNotNull(afterEvict);
        Assertions.assertFalse(afterEvict.isEmpty());
    }

    @Test
    public void testRefreshCacheByOneParam() throws Exception {
        String cacheName = "orderRefreshCacheByOneParam";
        String key = OrderMultipleKeysProvider.KEY;

        List<OrderVO> before = orderProviderService.refreshCacheByOneParam(key);
        Assertions.assertNotNull(before);
        Assertions.assertFalse(before.isEmpty());

        cacheManager.getCache(cacheName).evict(key);
        List<OrderVO> afterEvict = orderProviderService.refreshCacheByOneParam(key);
        Assertions.assertNotNull(afterEvict);
        Assertions.assertFalse(afterEvict.isEmpty());
    }

    @Test
    public void RefreshCacheByManually() throws Exception {
        FluxCache<String, List<OrderVO>> cache = cacheManager.getCache(OrderMyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE);

        List<OrderVO> vos = cache.get(OrderManuallyRefreshCache.KEY).get();
        List<OrderVO> vos1 = cache.get(OrderManuallyRefreshCache.KEY).get();
        Assertions.assertEquals(vos, vos1);
        TimeUnit.SECONDS.sleep(4);

        List<OrderVO> newVos = cache.get(OrderManuallyRefreshCache.KEY).get();
        Assertions.assertNotEquals(vos, newVos);


    }
}
