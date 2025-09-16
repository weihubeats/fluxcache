package com.fluxcache.example.service;

import com.fluxcache.example.vo.StudentVO;

import java.util.List;

/**
 * @author : wh
 * @date : 2025/9/16 14:35
 * @description:
 */
public interface StudentProviderService {

    /**
     * 测试缓存刷新
     * @return
     */
    List<StudentVO> testRefreshCache();


    /**
     * 测试缓存刷新
     * @return
     */
    List<StudentVO> refreshCacheByOneParam(String name);
}
