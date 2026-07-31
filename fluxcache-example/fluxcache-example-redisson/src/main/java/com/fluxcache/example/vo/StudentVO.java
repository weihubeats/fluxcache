package com.fluxcache.example.vo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/11/16 16:14
 * @description:
 */
@Data
@AllArgsConstructor
public class StudentVO {

    private Long id;

    private String name;

    private int age;

    private LocalDateTime createTime = LocalDateTime.now();

    public StudentVO() {
    }

    public StudentVO(Long id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }
}
