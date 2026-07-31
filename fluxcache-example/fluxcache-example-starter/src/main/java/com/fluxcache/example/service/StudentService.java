package com.fluxcache.example.service;

import com.fluxcache.example.vo.StudentVO;
import java.util.List;

/**
 * @author : wh
 * @date : 2024/11/16 16:14
 * @description:
 */
public interface StudentService {

    List<StudentVO> mockSelectSql();

    List<StudentVO> multipleKeys(String name);

}