package com.fluxcache.admin.controller;

import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.properties.FluxCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : wh
 * @date : 2024/8/31 15:12
 * @description:
 */
@RestController
@RequestMapping("${flux.cache.prefix:/cache/manager/v1}")
@RequiredArgsConstructor
public class FluxCacheController {

    private final FluxCacheManager cacheManager;

    private final FluxCacheProperties cacheProperties;

}
