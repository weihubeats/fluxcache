package com.fluxcache.example.service.impl;

import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.FluxRefresh;
import com.fluxcache.example.service.OrderMultipleKeysProvider;
import com.fluxcache.example.service.OrderProvider;
import com.fluxcache.example.service.OrderProviderService;
import com.fluxcache.example.utils.OrderDataUtils;
import com.fluxcache.example.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : wh
 * @date : 2025/9/16 14:36
 * @description:
 */
@Service
@Slf4j
public class OrderProviderServiceImpl implements OrderProviderService {

    @Override
    @FluxCacheable(
            cacheName = "orderTestRefreshCache",
            key = "'all'",
            refresh = @FluxRefresh(
                    enabled = true,
                    provider = OrderProvider.class,
                    cron = "0/2 * * * * ?" // 0 */1 * * * ? 一分钟
            )
    )
    public List<OrderVO> testRefreshCache() {
        log.info("开始查询数据");
        return OrderDataUtils.randomOrders();
    }

    @FluxCacheable(
            cacheName = "orderRefreshCacheByOneParam",
            key = "#name",
            refresh = @FluxRefresh(
                    enabled = true,
                    provider = OrderMultipleKeysProvider.class,
                    preheatOnStartup = true,
                    cron = "0/2 * * * * ?" // 2s刷新一次
            )
    )
    @Override
    public List<OrderVO> refreshCacheByOneParam(String name) {
        return OrderDataUtils.randomOrders();
    }

}
