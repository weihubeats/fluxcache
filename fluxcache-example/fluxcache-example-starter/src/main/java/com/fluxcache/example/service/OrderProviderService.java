package com.fluxcache.example.service;

import com.fluxcache.example.vo.OrderVO;

import java.util.List;

/**
 * @author : wh
 * @date : 2025/9/16 14:35
 * @description:
 */
public interface OrderProviderService {

    /**
     * 测试缓存刷新
     * @return
     */
    List<OrderVO> testRefreshCache();


    /**
     * 测试缓存刷新
     * @return
     */
    List<OrderVO> refreshCacheByOneParam(String name);
}
