package com.fluxcache.example.service;

import com.fluxcache.core.annotation.FirstCacheable;
import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.example.vo.OrderVO;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 热 Key 演示服务。
 *
 * <p>一级缓存 1 秒过期，模拟「高 QPS + 频繁过期回源」的真实热点场景，
 * 便于 {@code FluxHotKeyDetector} 稳定探测出热 Key。</p>
 *
 * @author : wh
 * @date : 2026/08/07
 * @description:
 */
@Service
@Slf4j
public class HotKeyDemoService {

    public static final String HOT_KEY_DEMO_CACHE = "hotKeyDemoCache";

    @FluxCacheable(cacheName = HOT_KEY_DEMO_CACHE, key = "#name",
        firstCacheable = @FirstCacheable(ttl = 1L, unit = TimeUnit.SECONDS))
    public List<OrderVO> getHotKeyData(String name) {
        log.debug("模拟数据库查询 name={}", name);
        return Lists.newArrayList(new OrderVO(100L, "hot-key-" + name, RandomUtils.nextInt(1, 1000)));
    }
}
