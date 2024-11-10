package com.fluxcache.core.caffeine.sync;

import com.fluxcache.core.model.AbstractLocalCacheDTO;

/**
 * @author : wh
 * @date : 2024/11/10 15:58
 * @description:
 */
public interface CacheSyncPostProcessor {


    void postProcessAfterClear(AbstractLocalCacheDTO abstractLocalCacheDTO);

}
