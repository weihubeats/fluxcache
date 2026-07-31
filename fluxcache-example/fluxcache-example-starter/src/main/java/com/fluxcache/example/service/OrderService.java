package com.fluxcache.example.service;

import com.fluxcache.example.vo.OrderVO;
import java.util.List;

/**
 * @author : wh
 * @date : 2024/11/16 16:14
 * @description:
 */
public interface OrderService {

    List<OrderVO> mockSelectSql();

    List<OrderVO> multipleKeys(String name);

}
