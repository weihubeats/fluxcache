package com.fluxcache.core.caffeine.sync;

import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.model.PutCacheDTO;

/**
 * @author : wh
 * @date : 2024/10/20 20:45
 * @description:
 */
public interface CacheSyncStrategy {

    void postClear(DeleteCacheDTO deleteCacheDTO);

    void postEvict(DeleteCacheDTO deleteCacheDTO);

    void sendPutEvent(PutCacheDTO putCacheDTO);


}
