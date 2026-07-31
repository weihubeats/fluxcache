package com.fluxcache.example.controller;

import com.fluxcache.core.utils.JsonUtil;
import com.fluxcache.example.service.StudentProviderService;
import com.fluxcache.example.vo.StudentVO;
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
public class TestRefreshCacheController {

    private final StudentProviderService studentProviderService;

    @GetMapping("/nullParam")
    public List<StudentVO> testRefreshCache() {
        List<StudentVO> vos = studentProviderService.testRefreshCache();
        log.info("testRefreshCache result: {}", JsonUtil.serialize2Json(vos));
        return vos;
    }

/*    @GetMapping("/multiple-keys")
    public List<StudentVO> testMultipleKeys(String name) {
        List<StudentVO> vos = studentService.multipleKeys(name);
        log.info("multiple-keys result: {}", JsonUtil.serialize2Json(vos));
        return vos;
    }*/
}
