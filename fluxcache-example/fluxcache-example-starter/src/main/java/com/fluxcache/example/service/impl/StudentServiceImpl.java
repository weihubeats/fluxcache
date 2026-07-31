package com.fluxcache.example.service.impl;

import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.FluxRefresh;
import com.fluxcache.example.service.StudentMultipleKeysProvider;
import com.fluxcache.example.service.StudentService;
import com.fluxcache.example.utils.RandomDataUtils;
import com.fluxcache.example.vo.StudentVO;
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
public class StudentServiceImpl implements StudentService {

    @Override
    public List<StudentVO> mockSelectSql() {
        log.info("开始查询数据");
        List<StudentVO> studentVOS = Lists.newArrayList(new StudentVO(1L, "小奏技术", 18), new StudentVO(2L, "小奏技术1", 19));
        return studentVOS;
    }

    @Override
    @FluxCacheable(
            cacheName = "multipleKeys",
            key = "#name",
            refresh = @FluxRefresh(
                    enabled = true,
                    provider = StudentMultipleKeysProvider.class,
                    preheatOnStartup = true,
                    cron = "0 */1 * * * ?" // 1分钟刷新一次
            )
    )
    public List<StudentVO> multipleKeys(String name) {
        log.info("开始查询数据");
        return RandomDataUtils.randomStudents(name);
    }
}
