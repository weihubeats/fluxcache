package com.fluxcache.core.config;

import java.util.concurrent.RejectedExecutionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author : wh
 * @date : 2024/11/12 12:34
 * @description:
 */
public class CacheThreadPoolExecutor extends ThreadPoolTaskExecutor {

    public CacheThreadPoolExecutor(int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds
        , String threadNamePrefix, RejectedExecutionHandler rejectedExecutionHandler) {
        this.setCorePoolSize(corePoolSize);
        this.setMaxPoolSize(maxPoolSize);
        //最大队列数量
        this.setQueueCapacity(queueCapacity);
        // 1分钟
        this.setKeepAliveSeconds(keepAliveSeconds);
        this.setThreadNamePrefix(threadNamePrefix);
        this.setRejectedExecutionHandler(rejectedExecutionHandler);
    }
}
