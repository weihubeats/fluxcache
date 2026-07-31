package com.fluxcache.example.config;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.example.utils.RandomDataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author : wh
 * @date : 2025/9/16 14:32
 * @description:
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ManuallyRefreshCache implements ApplicationListener<ContextRefreshedEvent> {

    private final FluxCacheManager cacheManager;

    public static final String KEY = "ManuallyRefreshCache";

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 获取key

        new Thread(() -> {
            while (true) {
                FluxCache<Object, Object> cache = cacheManager.getCache(MyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE);
                cache.put(KEY, RandomDataUtils.randomStudents());
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

    }
}
