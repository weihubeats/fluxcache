package com.fluxcache.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary statistics for all caches (dashboard overview).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FluxCacheStaticsSummaryVO {

    private String namespace;

    private List<Item> items = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String cacheName;
        private double overallHitRate;
        private long totalRequest;
        private long totalHit;
        private long totalMiss;
        private long maxLoadTimeOverall;
    }
}
