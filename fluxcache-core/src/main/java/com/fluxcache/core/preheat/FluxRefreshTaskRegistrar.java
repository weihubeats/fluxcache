package com.fluxcache.core.preheat;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.FluxRefresh;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : wh
 * @date : 2025/8/11
 * @description:
 */
@Slf4j
@RequiredArgsConstructor
public class FluxRefreshTaskRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private static final String PREHEAT_LOCK_PREFIX = "flux-cache:preheat-lock:";

    private static final String REFRESH_LOCK_PREFIX = "flux-cache:refresh-lock:";

    private final ApplicationContext context;

    private final TaskScheduler taskScheduler;

    private final FluxCacheManager cacheManager;

    private final RedissonClient redissonClient;

    private final FluxCacheRefreshExecutor executor;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        // 只在根上下文执行
        if (Objects.nonNull(event.getApplicationContext().getParent())) {
            return;
        }

        log.info("[FluxCache] 开始扫描 @FluxCacheable 以注册预热与刷新任务");

        String[] beanNames = context.getBeanDefinitionNames();
        AtomicInteger methodCount = new AtomicInteger();

        for (String beanName : beanNames) {
            Object beanProxy = context.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(beanProxy);
            if (shouldSkipClass(targetClass)) {
                continue;
            }

            Map<Method, FluxCacheable> annotatedMethods =
                    MethodIntrospector.selectMethods(targetClass,
                            (MethodIntrospector.MetadataLookup<FluxCacheable>) m ->
                                    AnnotatedElementUtils.findMergedAnnotation(m, FluxCacheable.class));

            annotatedMethods.forEach((targetMethod, cacheable) -> {
                if (Objects.isNull(cacheable)) {
                    return;
                }

                FluxRefresh refresh = cacheable.refresh();
                if (refresh == null || !refresh.enabled()) {
                    if (log.isDebugEnabled()) {
                        log.debug("[FluxCache] 方法 {}#{} 未启用刷新", targetClass.getSimpleName(), targetMethod.getName());
                    }
                    return;
                }
                // 获取可在代理对象上调用的方法（避免 JDK 动态代理问题）
                Method invocableMethod = AopUtils.selectInvocableMethod(targetMethod, beanProxy.getClass());

                // 预热
                if (refresh.preheatOnStartup()) {
                    scheduleOneTime(() -> runRefresh(beanProxy, invocableMethod, cacheable.cacheName(), refresh, PREHEAT_LOCK_PREFIX), refresh, invocableMethod, cacheable.cacheName());
                }
                // 定时刷新
                scheduleRecurring(() -> runRefresh(beanProxy, invocableMethod, cacheable.cacheName(), refresh, REFRESH_LOCK_PREFIX), refresh, invocableMethod, cacheable.cacheName());
                methodCount.getAndIncrement();

            });
        }
        log.info("[FluxCache] 缓存刷新任务注册完成，共处理 {} 个方法", methodCount.get());

    }

    private void scheduleRecurring(Runnable task, FluxRefresh refresh, Method method, String cacheName) {
        Instant startTime = Instant.now()
                .plus(Duration.of(refresh.initialDelay(), refresh.unit().toChronoUnit()))
                .plus(jitter(refresh));

        if (ObjectUtils.isNotEmpty(refresh.cron())) {
            ZoneId zone = ZoneId.systemDefault();
            taskScheduler.schedule(task, new CronTrigger(refresh.cron(), zone));
            log.info("[FluxCache] 注册 CRON 刷新 cron={} zone={} method={} cache={}",
                    refresh.cron(), zone, method.getName(), cacheName);
        } else if (refresh.fixedRate() > 0) {
            Duration period = Duration.of(refresh.fixedRate(), refresh.unit().toChronoUnit());
            taskScheduler.scheduleAtFixedRate(task, startTime, period);
            log.info("[FluxCache] 注册 fixedRate 刷新 initialDelay={}ms rate={}ms method={} cache={}",
                    Duration.between(Instant.now(), startTime).toMillis(), period.toMillis(),
                    method.getName(), cacheName);
        } else if (refresh.fixedDelay() > 0) {
            Duration delay = Duration.of(refresh.fixedDelay(), refresh.unit().toChronoUnit());
            taskScheduler.scheduleWithFixedDelay(task, startTime, delay);
            log.info("[FluxCache] 注册 fixedDelay 刷新 initialDelay={}ms delay={}ms method={} cache={}",
                    Duration.between(Instant.now(), startTime).toMillis(), delay.toMillis(),
                    method.getName(), cacheName);
        } else {
            log.warn("[FluxCache] 未找到有效调度参数 (cron/fixedRate/fixedDelay 全空) method={} cache={}",
                    method.getName(), cacheName);
        }
    }

    private void scheduleOneTime(Runnable r, FluxRefresh cfg, Method method, String cacheName) {
        Instant start = Instant.now().plus(jitter(cfg));
        taskScheduler.schedule(r, start);
        log.info("[FluxCache] 注册启动预热任务 method={} cache={} startAt={}", method.getName(), cacheName, start);
    }

    private void runRefresh(Object bean, Method method, String cacheName, FluxRefresh cfg, String prefix) {
        try {
            Collection<?> keys = context.getBean(cfg.provider()).getPreheatData();
            if (keys == null || keys.isEmpty())
                return;
            FluxCacheRefreshContext ctx = FluxCacheRefreshContext.builder()
                    .bean(bean)
                    .method(method)
                    .cacheName(cacheName)
                    .refreshConfig(cfg)
                    .cache(cacheManager.getCache(cacheName))
                    .keys(keys)
                    .build();
            withDistributedLockIfNeeded(buildLockKey(prefix, bean, method, cacheName), cfg, () -> executor.refresh(ctx));
        } catch (Exception e) {
            log.error("[FluxCache] 执行刷新异常 method={} cache={}", method.getName(), cacheName, e);
        }
    }

    private void withDistributedLockIfNeeded(String key, FluxRefresh fluxRefresh, Runnable body) {
        if (!fluxRefresh.distributedLock()) {
            body.run();
            return;
        }

        try {
            var lock = redissonClient.getLock(key);
            if (lock.tryLock(fluxRefresh.lockWaitSeconds(), fluxRefresh.lockLeaseSeconds(), TimeUnit.SECONDS)) {
                try {
                    body.run();
                } finally {
                    if (lock.isHeldByCurrentThread())
                        lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildLockKey(String prefix, Object bean, Method method, String cacheName) {
        return prefix + bean.getClass().getName() + ":" + method.getName() + ":" + cacheName;
    }

    private void executeWithOptionalLock(String lockKey,
                                         FluxRefresh refresh,
                                         Runnable taskBody) {
        if (!refresh.distributedLock()) {
            taskBody.run();
            return;
        }
        RLock lock = redissonClient.getLock(lockKey);

        // todo maybe use properties to get default values
        long waitSeconds = refresh.lockWaitSeconds();
        long leaseSeconds = refresh.lockLeaseSeconds();

        boolean locked = false;
        try {
            locked = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (!locked) {
                log.debug("[FluxCache] 获取锁失败 lockKey {} 等待 {}s", lockKey, waitSeconds);
                return;
            }
            log.debug("[FluxCache] 获取锁成功 lockKey {}", lockKey);
            taskBody.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[FluxCache] 尝试获取锁被中断 lockKey {}", lockKey, e);
        } catch (Exception ex) {
            log.error("[FluxCache] 分布式锁执行异常 lockKey {}", lockKey, ex);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception unlockEx) {
                    log.error("[FluxCache] 释放锁异常 lockKey {}", lockKey, unlockEx);
                }
            }
        }
    }

    private void executeRefreshLogic(Object beanProxy,
                                     Method method,
                                     String cacheName,
                                     FluxRefresh refresh,
                                     boolean startupPreheat) {

        long startNanos = System.nanoTime();
        boolean success = true;
        int keyCount = 0;
        Throwable error = null;

        try {
            FluxPreheatDataProvider<?> provider = context.getBean(refresh.provider());

            Collection<?> keys;
            try {
                keys = provider.getPreheatData();
            } catch (Exception providerEx) {
                log.error("[FluxCache] provider 获取预热数据异常 provider={} method={} cache={}",
                        refresh.provider().getSimpleName(), method.getName(), cacheName, providerEx);
                return;
            }

            if (keys == null || keys.isEmpty()) {
                log.debug("[FluxCache] 无可刷新 key method={} cache={}", method.getName(), cacheName);
                return;
            }

            keyCount = keys.size();

            FluxCache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.error("[FluxCache] 找不到缓存 cacheName={} method={}", cacheName, method.getName());
                return;
            }

            // todo 刷新策略 可扩展：EVICT_BATCH、PARALLEL、PUT_DIRECT 等
            for (Object key : keys) {
                invokeMethodForKey(beanProxy, method, key);
            }

            log.info("[FluxCache] 刷新完成 cache={} method={} keys={} startupPreheat={}",
                    cacheName, method.getName(), keyCount, startupPreheat);

        } catch (Throwable t) {
            success = false;
            error = t;
            log.error("[FluxCache] 刷新执行异常 cache={} method={}", cacheName, method.getName(), t);
        } finally {
            long costMs = (System.nanoTime() - startNanos) / 1_000_000;
            // metrics 占位：可接入 Micrometer
            // metricsRecorder.record(cacheName, method, success, keyCount, costMs);
            if (log.isDebugEnabled()) {
                log.debug("[FluxCache] 刷新统计 cache={} method={} success={} keys={} cost={}ms error={}",
                        cacheName, method.getName(), success, keyCount, costMs,
                        (error == null ? "N/A" : error.getClass().getSimpleName()));
            }
        }
    }

    private void invokeMethodForKey(Object beanProxy, Method method, Object key)
            throws InvocationTargetException, IllegalAccessException {
        ReflectionUtils.makeAccessible(method);
        if (method.getParameterCount() == 0) {
            method.invoke(beanProxy);
        } else if (method.getParameterCount() == 1) {
            method.invoke(beanProxy, key);
        } else {
            log.warn("[FluxCache] 暂不支持多参数刷新 method={} 参数个数={}", method.getName(), method.getParameterCount());
        }
    }

    private boolean shouldSkipClass(Class<?> clazz) {
        String pkg = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
        return pkg.startsWith("org.springframework.") ||
                pkg.startsWith("java.") ||
                pkg.startsWith("jakarta.") ||
                pkg.startsWith("sun.");
    }

    /**
     * 计算抖动 (jitter) 用于错峰
     */
    private Duration jitter(FluxRefresh refresh) {
        long boundMs = refresh.jitterMillis();
        if (boundMs <= 0)
            return Duration.ZERO;
        long rand = ThreadLocalRandom.current().nextLong(boundMs);
        return Duration.ofMillis(rand);
    }
}
