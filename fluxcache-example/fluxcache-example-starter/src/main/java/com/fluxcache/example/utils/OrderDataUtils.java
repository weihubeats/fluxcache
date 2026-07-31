package com.fluxcache.example.utils;

import com.fluxcache.example.vo.OrderVO;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author : wh
 * @date : 2025/9/16 14:33
 * @description:
 */
public class OrderDataUtils {


    public static List<OrderVO> randomOrders() {
        return randomOrders("小奏技术");
    }

    /**
     * 随机生成订单数据
     *
     * @return
     */
    public static List<OrderVO> randomOrders(String name) {
        List<OrderVO> orderVOS = Lists.newArrayList();
        for (int i = 0; i < 3; i++) {
            orderVOS.add(new OrderVO((long) i, name + ThreadLocalRandom.current().nextInt(0, 500), ThreadLocalRandom.current().nextInt(0, 500)));
        }
        return orderVOS;
    }
}
