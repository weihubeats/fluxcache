package com.fluxcache.example.controller;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.example.FluxCacheApplication;
import com.fluxcache.example.config.ManuallyRefreshCache;
import com.fluxcache.example.config.MyFluxCacheDataRegistered;
import com.fluxcache.example.service.StudentMultipleKeysProvider;
import com.fluxcache.example.service.StudentProviderService;
import com.fluxcache.example.vo.StudentVO;
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
    private StudentProviderService studentProviderService;

    @Autowired
    private FluxCacheManager cacheManager;

    @Test
    public void testRefreshCacheByNoParam() throws Exception {
        List<StudentVO> vos = studentProviderService.testRefreshCache();
        List<StudentVO> vos1 = studentProviderService.testRefreshCache();
        Assertions.assertEquals(vos, vos1);

        TimeUnit.SECONDS.sleep(4);
        List<StudentVO> newVos = studentProviderService.testRefreshCache();
        Assertions.assertNotEquals(vos, newVos);
    }

    @Test
    public void testRefreshCacheByOneParam() throws Exception {
        String key = StudentMultipleKeysProvider.KEY;
        List<StudentVO> vos = studentProviderService.refreshCacheByOneParam(key);
        List<StudentVO> vos1 = studentProviderService.refreshCacheByOneParam(key);
        Assertions.assertEquals(vos, vos1);

        TimeUnit.SECONDS.sleep(4);
        List<StudentVO> newVos = studentProviderService.refreshCacheByOneParam(key);
        Assertions.assertNotEquals(vos, newVos);
    }

    @Test
    public void RefreshCacheByManually() throws Exception {
        FluxCache<String, List<StudentVO>> cache = cacheManager.getCache(MyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE);

        List<StudentVO> vos = cache.get(ManuallyRefreshCache.KEY).get();
        List<StudentVO> vos1 = cache.get(ManuallyRefreshCache.KEY).get();
        Assertions.assertEquals(vos, vos1);
        TimeUnit.SECONDS.sleep(4);

        List<StudentVO> newVos = cache.get(ManuallyRefreshCache.KEY).get();
        Assertions.assertNotEquals(vos, newVos);


    }
}
