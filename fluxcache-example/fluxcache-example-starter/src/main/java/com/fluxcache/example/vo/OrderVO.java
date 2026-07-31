package com.fluxcache.example.vo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/11/16 16:14
 * @description:
 */
@Data
@AllArgsConstructor
public class OrderVO {

    private Long orderId;

    private String orderName;

    private int orderAmount;

    private LocalDateTime createTime = LocalDateTime.now();

    public OrderVO() {
    }

    public OrderVO(Long orderId, String orderName, int orderAmount) {
        this.orderId = orderId;
        this.orderName = orderName;
        this.orderAmount = orderAmount;
    }
}
