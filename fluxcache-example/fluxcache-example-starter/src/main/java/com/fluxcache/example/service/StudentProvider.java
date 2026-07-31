package com.fluxcache.example.service;

import com.fluxcache.core.preheat.FluxPreheatDataProvider;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * @author : wh
 * @date : 2025/9/16 14:36
 * @description:
 */
@Service
public class StudentProvider implements FluxPreheatDataProvider<String> {


    @Override
    public Collection<String> getPreheatData() {
        return List.of("all");
    }
}
