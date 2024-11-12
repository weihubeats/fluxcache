package com.fluxcache.core.constants;

/**
 * @author : wh
 * @date : 2024/11/12 12:45
 * @description:
 */
public interface ThreadPoolConstant {

    int DEFAULT_CORE_POOL_SIZE = 1;

    int DEFAULT_MAXIMUM_POOL_SIZE = 3;

    int DEFAULT_KEEP_ALIVE_TIME = 1;

    int DEFAULT_QUEUE_SIZE = 2000;

    String DEFAULT_THREAD_NAME_PREFIX = "flux-cache-";
}
