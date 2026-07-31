package com.fluxcache.example.service.impl;

import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.FluxRefresh;
import com.fluxcache.example.service.OrderMultipleKeysProvider;
import com.fluxcache.example.service.OrderService;
import com.fluxcache.example.utils.OrderDataUtils;
import com.fluxcache.example.vo.OrderVO;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : wh
 * @date : 2024/11/16 16:15
 * @description:
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Override
    public List<OrderVO> mockSelectSql() {
        log.info("开始查询数据");
        List<OrderVO> orderVOS = Lists.newArrayList(new OrderVO(1L, "小奏技术", 18), new OrderVO(2L, "小奏技术1", 19));
        return orderVOS;
    }

    @Override
    @FluxCacheable(
            cacheName = "orderMultipleKeys",
            key = "#name",
            refresh = @FluxRefresh(
                    enabled = true,
                    provider = OrderMultipleKeysProvider.class,
                    preheatOnStartup = true,
                    cron = "0 */1 * * * ?" // 1分钟刷新一次
            )
    )
    public List<OrderVO> multipleKeys(String name) {
        log.info("开始查询数据");
        return OrderDataUtils.randomOrders(name);
    }
}
