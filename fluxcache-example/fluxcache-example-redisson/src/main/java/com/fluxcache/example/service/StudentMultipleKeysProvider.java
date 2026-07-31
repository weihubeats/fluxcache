package com.fluxcache.example.service;

import com.fluxcache.core.preheat.FluxPreheatDataProvider;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * @author : wh
 * @date : 2025/9/16 14:38
 * @description:
 */
@Service
public class StudentMultipleKeysProvider implements FluxPreheatDataProvider<String> {

    public static final String KEY = "xiaozou";

    @Override
    public Collection<String> getPreheatData() {
        return Lists.newArrayList(KEY, "aa", "dd");
    }
}