package com.fluxcache.core.preheat;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.FluxRefresh;
import com.fluxcache.core.lock.FluxDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : wh
 * @date : 2025/8/11
 * @description:
 */
@Slf4j
public class FluxRefreshTaskRegistrar implements ApplicationListener<ContextRefreshedEvent>, DisposableBean {

    private static final String PREHEAT_LOCK_PREFIX = "flux-cache:preheat-lock:";

    private static final String REFRESH_LOCK_PREFIX = "flux-cache:refresh-lock:";

    private final ApplicationContext context;

    private final TaskScheduler taskScheduler;

    private final FluxCacheManager cacheManager;

    private final FluxDistributedLock distributedLock;

    private final FluxCacheRefreshExecutor executor;

    private final AtomicBoolean registered = new AtomicBoolean(false);

    private final List<ScheduledFuture<?>> scheduledTasks = new CopyOnWriteArrayList<>();

    public FluxRefreshTaskRegistrar(ApplicationContext context, TaskScheduler taskScheduler,
                                    FluxCacheManager cacheManager, FluxDistributedLock distributedLock,
                                    FluxCacheRefreshExecutor executor) {
        this.context = context;
        this.taskScheduler = taskScheduler;
        this.cacheManager = cacheManager;
        this.distributedLock = distributedLock;
        this.executor = executor;
    }

    @Override
    public void destroy() {
        scheduledTasks.forEach(task -> task.cancel(false));
        scheduledTasks.clear();
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        // 只在根上下文执行
        if (Objects.nonNull(event.getApplicationContext().getParent())) {
            return;
        }
        // ContextRefreshedEvent 可能触发多次（父子容器/devtools），任务只注册一次
        if (!registered.compareAndSet(false, true)) {
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
                Method invocableMethod = AopUtils.selectInvocableMethod(targetMethod, beanProxy.getClass());

                if (refresh.preheatOnStartup()) {
                    scheduleOneTime(() -> runRefresh(beanProxy, invocableMethod, cacheable.cacheName(), refresh, PREHEAT_LOCK_PREFIX), refresh, invocableMethod, cacheable.cacheName());
                }
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
            scheduledTasks.add(taskScheduler.schedule(task, new CronTrigger(refresh.cron(), zone)));
            log.info("[FluxCache] 注册 CRON 刷新 cron={} zone={} method={} cache={}",
                    refresh.cron(), zone, method.getName(), cacheName);
        } else if (refresh.fixedRate() > 0) {
            Duration period = Duration.of(refresh.fixedRate(), refresh.unit().toChronoUnit());
            scheduledTasks.add(taskScheduler.scheduleAtFixedRate(task, startTime, period));
            log.info("[FluxCache] 注册 fixedRate 刷新 initialDelay={}ms rate={}ms method={} cache={}",
                    Duration.between(Instant.now(), startTime).toMillis(), period.toMillis(),
                    method.getName(), cacheName);
        } else if (refresh.fixedDelay() > 0) {
            Duration delay = Duration.of(refresh.fixedDelay(), refresh.unit().toChronoUnit());
            scheduledTasks.add(taskScheduler.scheduleWithFixedDelay(task, startTime, delay));
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
        scheduledTasks.add(taskScheduler.schedule(r, start));
        log.info("[FluxCache] 注册启动预热任务 method={} cache={} startAt={}", method.getName(), cacheName, start);
    }

    private void runRefresh(Object bean, Method method, String cacheName, FluxRefresh cfg, String prefix) {
        try {
            // default provider is not a bean - skip instead of failing every tick with NoSuchBeanDefinitionException
            if (FluxPreheatDataProvider.None.class.equals(cfg.provider())) {
                if (log.isDebugEnabled()) {
                    log.debug("[FluxCache] 未配置 preheatDataProvider，跳过刷新 method={} cache={}",
                            method.getName(), cacheName);
                }
                return;
            }
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
        if (distributedLock == null) {
            log.warn("[FluxCache] distributedLock enabled but no FluxDistributedLock bean; skip refresh for key {}", key);
            return;
        }

        try {
            if (distributedLock.tryLock(key, fluxRefresh.lockWaitSeconds(), fluxRefresh.lockLeaseSeconds())) {
                try {
                    body.run();
                } finally {
                    distributedLock.unlock(key);
                }
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("[FluxCache] distributed lock error key={}", key, e);
        }
    }

    private String buildLockKey(String prefix, Object bean, Method method, String cacheName) {
        // target class, not proxy class: JDK proxy names are JVM-order dependent and would
        // break cross-node mutual exclusion
        return prefix + AopUtils.getTargetClass(bean).getName() + ":" + method.getName() + ":" + cacheName;
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

    private Duration jitter(FluxRefresh refresh) {
        long boundMs = refresh.jitterMillis();
        if (boundMs <= 0)
            return Duration.ZERO;
        long rand = ThreadLocalRandom.current().nextLong(boundMs);
        return Duration.ofMillis(rand);
    }
}
