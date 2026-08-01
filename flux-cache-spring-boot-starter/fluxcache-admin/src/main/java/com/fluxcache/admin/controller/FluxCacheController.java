package com.fluxcache.admin.controller;

import com.fluxcache.admin.vo.FluxCacheAllStaticsVO;
import com.fluxcache.admin.vo.FluxCacheOperationVO;
import com.fluxcache.admin.vo.FluxCacheStaticsSummaryVO;
import com.fluxcache.admin.vo.FluxCacheValueVO;
import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.monitor.FluxCacheStatics;
import com.fluxcache.core.properties.FluxCacheProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 获取所有缓存统计
     *
     * @return
     */
    @GetMapping("/getAllStatics")
    public FluxCacheAllStaticsVO getAllStatics(@RequestParam String cacheName) {
        FluxCacheAllStaticsVO vo = new FluxCacheAllStaticsVO(cacheName, cacheManager.getCacheStatics(cacheName));
        return vo;
    }

    /**
     * 全部缓存统计摘要（供 Dashboard 总览表格）
     */
    @GetMapping("/statics/summary")
    public FluxCacheStaticsSummaryVO staticsSummary() {
        List<FluxCacheStaticsSummaryVO.Item> items = new ArrayList<>();
        List<FluxCacheOperation> operations = cacheManager.getAllCacheMetaData();
        if (operations != null) {
            for (FluxCacheOperation op : operations) {
                if (op == null || op.getCacheName() == null) {
                    continue;
                }
                FluxCacheStatics statics = cacheManager.getCacheStatics(op.getCacheName());
                if (statics == null) {
                    items.add(new FluxCacheStaticsSummaryVO.Item(op.getCacheName(), 0D, 0L, 0L, 0L, 0L));
                    continue;
                }
                FluxCacheAllStaticsVO detail = new FluxCacheAllStaticsVO(op.getCacheName(), statics);
                items.add(new FluxCacheStaticsSummaryVO.Item(
                        op.getCacheName(),
                        detail.getOverallHitRate(),
                        detail.getTotalRequest(),
                        detail.getTotalHit(),
                        detail.getTotalMiss(),
                        detail.getMaxLoadTimeOverall()
                ));
            }
        }
        return new FluxCacheStaticsSummaryVO(cacheProperties.namespace(), items);
    }

    /**
     * 获取缓存
     *
     * @param cacheName
     * @param key
     * @return
     */
    @GetMapping("/getValue")
    public FluxCacheValueVO getValue(@RequestParam String cacheName, @RequestParam String key) {
        FluxCacheValueVO vo = new FluxCacheValueVO();
        FluxCache cache = cacheManager.getCache(cacheName);
        if (Objects.nonNull(cache)) {
            FluxCache.ValueWrapper wrapper = cache.get(key);
            if (Objects.nonNull(wrapper)) {
                vo.setValue(wrapper.get());
                vo.setFlag(true);
                return vo;
            }
        }
        vo.setFlag(false);
        return vo;
    }

    @GetMapping("/all/caches")
    public FluxCacheOperationVO evictCache() {
        // todo for the time being, only string is returned
        FluxCacheOperationVO operationVO = new FluxCacheOperationVO(cacheProperties.namespace(), cacheManager.getAllCacheMetaData());
        return operationVO;
    }

    /**
     * 清理缓存
     *
     * @param cacheName
     * @param keys
     * @return
     */
    @PostMapping("/evict")
    public boolean evictCache(@RequestParam String cacheName, @RequestParam List<Object> keys) {
        return cacheManager.evictCache(cacheName, keys);
    }

    @PostMapping("/clear")
    public boolean clearCache(@RequestParam String cacheName) {
        return cacheManager.clearCacheByName(cacheName);
    }

}
