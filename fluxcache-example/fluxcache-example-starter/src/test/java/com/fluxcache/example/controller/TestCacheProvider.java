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
        List<OrderVO> vos = orderProviderService.testRefreshCache();
        List<OrderVO> vos1 = orderProviderService.testRefreshCache();
        Assertions.assertEquals(vos, vos1);

        TimeUnit.SECONDS.sleep(4);
        List<OrderVO> newVos = orderProviderService.testRefreshCache();
        Assertions.assertNotEquals(vos, newVos);
    }

    @Test
    public void testRefreshCacheByOneParam() throws Exception {
        String key = OrderMultipleKeysProvider.KEY;
        List<OrderVO> vos = orderProviderService.refreshCacheByOneParam(key);
        List<OrderVO> vos1 = orderProviderService.refreshCacheByOneParam(key);
        Assertions.assertEquals(vos, vos1);

        TimeUnit.SECONDS.sleep(4);
        List<OrderVO> newVos = orderProviderService.refreshCacheByOneParam(key);
        Assertions.assertNotEquals(vos, newVos);
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
