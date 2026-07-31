package com.fluxcache.example.utils;

import com.fluxcache.example.vo.StudentVO;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author : wh
 * @date : 2025/9/16 14:33
 * @description:
 */
public class RandomDataUtils {


    public static List<StudentVO> randomStudents() {
        return randomStudents("小奏技术");
    }

    /**
     * 随机生成学生数据
     *
     * @return
     */
    public static List<StudentVO> randomStudents(String name) {
        List<StudentVO> studentVOS = Lists.newArrayList();
        for (int i = 0; i < 3; i++) {
            studentVOS.add(new StudentVO((long) i, name + ThreadLocalRandom.current().nextInt(0, 500), ThreadLocalRandom.current().nextInt(0, 500)));
        }
        return studentVOS;
    }
}
