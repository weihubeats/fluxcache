package com.fluxcache.core.manual;

import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import java.util.List;

/**
 * @author : wh
 * @date : 2024/9/22 16:20
 * @description:
 */
public interface FluxCacheDataRegistered {

    List<FluxMultilevelCacheCacheable> registerCache();
}
