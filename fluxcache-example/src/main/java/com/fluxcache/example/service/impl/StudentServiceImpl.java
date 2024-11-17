package com.fluxcache.example.service.impl;

import com.fluxcache.example.service.StudentService;
import com.fluxcache.example.vo.StudentVO;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}
