package com.fluxcache.core.spi;

import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.exception.FluxCacheNotSupperException;
import com.fluxcache.core.impl.creator.CaffeineFluxCacheCreator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Registry of {@link FluxCacheCreator} plugins keyed by {@link FluxCacheType}.
 * When multiple creators claim the same type, the last one wins (override).
 *
 * @author : wh
 * @date : 2026/7/29
 */
@Slf4j
public class FluxCacheCreatorRegistry {

    private final ObjectProvider<FluxCacheCreator> creatorProvider;
    private volatile Map<FluxCacheType, FluxCacheCreator> creators;

    public FluxCacheCreatorRegistry(List<FluxCacheCreator> creators) {
        Objects.requireNonNull(creators, "creators must not be null");
        this.creatorProvider = null;
        this.creators = buildMap(creators);
    }

    public FluxCacheCreatorRegistry(ObjectProvider<FluxCacheCreator> creatorProvider) {
        this.creatorProvider = Objects.requireNonNull(creatorProvider, "creatorProvider must not be null");
    }

    public FluxCacheCreator getRequired(FluxCacheType cacheType) {
        FluxCacheCreator creator = resolve().get(cacheType);
        if (creator == null) {
            throw new FluxCacheNotSupperException("Unsupported cache type: " + cacheType);
        }
        return creator;
    }

    public boolean supports(FluxCacheType cacheType) {
        return resolve().containsKey(cacheType);
    }

    public Map<FluxCacheType, FluxCacheCreator> getCreators() {
        return resolve();
    }

    private Map<FluxCacheType, FluxCacheCreator> resolve() {
        Map<FluxCacheType, FluxCacheCreator> local = this.creators;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (this.creators == null) {
                List<FluxCacheCreator> list = creatorProvider.orderedStream().collect(Collectors.toList());
                this.creators = buildMap(list);
            }
            return this.creators;
        }
    }

    private static Map<FluxCacheType, FluxCacheCreator> buildMap(List<FluxCacheCreator> creators) {
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
        return Collections.unmodifiableMap(map);
    }

    /**
     * Built-in local creators for non-Spring / unit-test usage.
     */
    public static FluxCacheCreatorRegistry withDefaults() {
        return new FluxCacheCreatorRegistry(Collections.singletonList(new CaffeineFluxCacheCreator()));
    }
}
