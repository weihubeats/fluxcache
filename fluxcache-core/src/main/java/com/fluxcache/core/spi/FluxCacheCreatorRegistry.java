package com.fluxcache.core.spi;

import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.exception.FluxCacheNotSupperException;
import com.fluxcache.core.impl.creator.CaffeineFluxCacheCreator;
import com.fluxcache.core.impl.creator.RedissonBucketFluxCacheCreator;
import com.fluxcache.core.impl.creator.RedissonRMapFluxCacheCreator;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of {@link FluxCacheCreator} plugins keyed by {@link FluxCacheType}.
 * When multiple creators claim the same type, the last one wins (override).
 *
 * @author : wh
 * @date : 2026/7/29
 */
@Slf4j
public class FluxCacheCreatorRegistry {

    private final Map<FluxCacheType, FluxCacheCreator> creators;

    public FluxCacheCreatorRegistry(List<FluxCacheCreator> creators) {
        Objects.requireNonNull(creators, "creators must not be null");
        Map<FluxCacheType, FluxCacheCreator> map = new EnumMap<>(FluxCacheType.class);
        for (FluxCacheCreator creator : creators) {
            if (creator == null || creator.supportType() == null) {
                throw new IllegalArgumentException("FluxCacheCreator and supportType must not be null");
            }
            FluxCacheCreator previous = map.put(creator.supportType(), creator);
            if (previous != null && previous != creator) {
                log.info("Override FluxCacheCreator for type {}: {} -> {}",
                        creator.supportType(), previous.getClass().getName(), creator.getClass().getName());
            }
        }
        this.creators = Collections.unmodifiableMap(map);
    }

    public FluxCacheCreator getRequired(FluxCacheType cacheType) {
        FluxCacheCreator creator = creators.get(cacheType);
        if (creator == null) {
            throw new FluxCacheNotSupperException("Unsupported cache type: " + cacheType);
        }
        return creator;
    }

    public boolean supports(FluxCacheType cacheType) {
        return creators.containsKey(cacheType);
    }

    public Map<FluxCacheType, FluxCacheCreator> getCreators() {
        return creators;
    }

    /**
     * Built-in creators for non-Spring / unit-test usage.
     */
    public static FluxCacheCreatorRegistry withDefaults() {
        return new FluxCacheCreatorRegistry(Arrays.asList(
                new CaffeineFluxCacheCreator(),
                new RedissonRMapFluxCacheCreator(),
                new RedissonBucketFluxCacheCreator()
        ));
    }
}
