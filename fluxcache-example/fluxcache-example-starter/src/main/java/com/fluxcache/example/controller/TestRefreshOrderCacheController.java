package com.fluxcache.example.controller;

import com.fluxcache.core.utils.JsonUtil;
import com.fluxcache.example.service.OrderProviderService;
import com.fluxcache.example.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author : wh
 * @date : 2025/9/16 14:35
 * @description:
 */
@RestController
@RequestMapping("/test/refreshCache")
@Slf4j
@RequiredArgsConstructor
public class TestRefreshOrderCacheController {

    private final OrderProviderService orderProviderService;

    @GetMapping("/nullParam")
    public List<OrderVO> testRefreshCache() {
        List<OrderVO> vos = orderProviderService.testRefreshCache();
        log.info("testRefreshCache result: {}", JsonUtil.serialize2Json(vos));
        return vos;
    }

/*    @GetMapping("/multiple-keys")
    public List<OrderVO> testMultipleKeys(String name) {
        List<OrderVO> vos = orderService.multipleKeys(name);
        log.info("multiple-keys result: {}", JsonUtil.serialize2Json(vos));
        return vos;
    }*/
}
