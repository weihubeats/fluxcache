package com.fluxcache.admin.vo;

import com.fluxcache.core.model.FluxCacheOperation;
import java.util.List;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/9/1 18:37
 * @description:
 */
@Data
public class FluxCacheOperationVO {

    private String namespace;

    List<FluxCacheOperation> cacheOperations;

    public FluxCacheOperationVO(String namespace, List<FluxCacheOperation> cacheOperations) {
        this.namespace = namespace;
        this.cacheOperations = cacheOperations;
    }


}
