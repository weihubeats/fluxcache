package com.fluxcache.example.service.impl;

import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.FluxRefresh;
import com.fluxcache.example.service.StudentMultipleKeysProvider;
import com.fluxcache.example.service.StudentProvider;
import com.fluxcache.example.service.StudentProviderService;
import com.fluxcache.example.utils.RandomDataUtils;
import com.fluxcache.example.vo.StudentVO;
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
public class StudentProviderServiceImpl implements StudentProviderService {

    @Override
    @FluxCacheable(
            cacheName = "testRefreshCache",
            key = "'all'",
            refresh = @FluxRefresh(
                    enabled = true,
                    provider = StudentProvider.class,
                    cron = "0/2 * * * * ?" // 0 */1 * * * ? 一分钟
            )
    )
    public List<StudentVO> testRefreshCache() {
        log.info("开始查询数据");
        return RandomDataUtils.randomStudents();
    }

    @FluxCacheable(
            cacheName = "refreshCacheByOneParam",
            key = "#name",
            refresh = @FluxRefresh(
                    enabled = true,
                    provider = StudentMultipleKeysProvider.class,
                    preheatOnStartup = true,
                    cron = "0/2 * * * * ?" // 2s刷新一次
            )
    )
    @Override
    public List<StudentVO> refreshCacheByOneParam(String name) {
        return RandomDataUtils.randomStudents();
    }

}
