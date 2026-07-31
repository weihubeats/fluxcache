package com.fluxcache.core.caffeine.sync;

import com.fluxcache.core.model.DeleteCacheDTO;
import com.fluxcache.core.model.PutCacheDTO;

/**
 * No-op sync used when no Redis pub/sub provider is available (local-only).
 *
 * @author : wh
 * @date : 2026/7/30
 */
public class NoOpCacheSyncStrategy implements CacheSyncStrategy {

    @Override
    public void postClear(DeleteCacheDTO deleteCacheDTO) {
        // no-op
    }

    @Override
    public void postEvict(DeleteCacheDTO deleteCacheDTO) {
        // no-op
    }

    @Override
    public void sendPutEvent(PutCacheDTO putCacheDTO) {
        // no-op
    }
}
