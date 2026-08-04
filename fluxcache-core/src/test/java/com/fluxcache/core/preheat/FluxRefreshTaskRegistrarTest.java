package com.fluxcache.core.preheat;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.annotation.FluxCacheable;
import com.fluxcache.core.annotation.FluxRefresh;
import com.fluxcache.core.lock.FluxDistributedLock;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存刷新任务注册：cron/fixedRate/fixedDelay 调度、分布式锁、预热任务、上下文过滤。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxRefreshTaskRegistrarTest {

    private ApplicationContext context;
    private TaskScheduler scheduler;
    private FluxCacheManager cacheManager;
    private FluxDistributedLock distributedLock;
    private FluxCacheRefreshExecutor executor;
    private FluxRefreshTaskRegistrar registrar;
    private ContextRefreshedEvent event;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        context = mock(ApplicationContext.class);
        scheduler = mock(TaskScheduler.class);
        cacheManager = mock(FluxCacheManager.class);
        distributedLock = mock(FluxDistributedLock.class);
        executor = mock(FluxCacheRefreshExecutor.class);
        registrar = new FluxRefreshTaskRegistrar(context, scheduler, cacheManager, distributedLock, executor);
        event = mock(ContextRefreshedEvent.class);
        when(event.getApplicationContext()).thenReturn(context);
        when(context.getParent()).thenReturn(null);
        // 调度任务立即执行，便于同步断言
        when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                });
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(java.time.Duration.class)))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                });
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(java.time.Duration.class)))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                });
        when(scheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                });
    }

    private void registerBeans(Object... beans) {
        when(context.getBeanDefinitionNames()).thenReturn(
                java.util.stream.IntStream.range(0, beans.length)
                        .mapToObj(i -> "bean-" + i).toArray(String[]::new));
        for (int i = 0; i < beans.length; i++) {
            when(context.getBean("bean-" + i)).thenReturn(beans[i]);
        }
    }

    @Test
    public void providerReturnsNull_skipsRefresh() {
        registerBeans(new NoneProviderService());
        when(context.getBean(FluxPreheatDataProvider.None.class))
                .thenAnswer(inv -> new FluxPreheatDataProvider.None<String>());

        registrar.onApplicationEvent(event);

        verify(executor, never()).refresh(any());
    }

    @Test
    public void fixedRate_registersAndRefreshes() {
        registerBeans(new RefreshService());
        when(context.getBean(KeysProvider.class)).thenReturn(() -> List.of("k1", "k2"));
        when(cacheManager.getCache("refresh-cache")).thenReturn(mock(FluxCache.class));

        registrar.onApplicationEvent(event);

        verify(executor, org.mockito.Mockito.times(1)).refresh(any(FluxCacheRefreshContext.class));
    }

    @Test
    public void childContext_skipped() {
        when(context.getParent()).thenReturn(mock(ApplicationContext.class));

        registrar.onApplicationEvent(event);

        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    public void springAndJavaBeans_skipped() {
        registerBeans(new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler(),
                new java.util.concurrent.atomic.AtomicInteger());

        registrar.onApplicationEvent(event);

        verify(executor, never()).refresh(any());
    }

    @Test
    public void disabledRefresh_noSchedule() {
        registerBeans(new DisabledService());

        registrar.onApplicationEvent(event);

        verify(executor, never()).refresh(any());
    }

    @Test
    public void cronPath_registers() {
        registerBeans(new CronService());
        when(context.getBean(KeysProvider.class)).thenReturn(Collections::emptyList);

        registrar.onApplicationEvent(event);

        verify(scheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    public void fixedDelayPath_registers() {
        registerBeans(new FixedDelayService());
        when(context.getBean(KeysProvider.class)).thenReturn(Collections::emptyList);

        registrar.onApplicationEvent(event);

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class),
                any(java.time.Duration.class));
    }

    @Test
    public void noScheduleConfig_warnsButNoExecutor() {
        registerBeans(new NoScheduleService());
        when(context.getBean(KeysProvider.class)).thenReturn(Collections::emptyList);

        registrar.onApplicationEvent(event);

        verify(executor, never()).refresh(any());
    }

    @Test
    public void emptyProviderData_skipsRefresh() {
        registerBeans(new RefreshService());
        when(context.getBean(KeysProvider.class)).thenReturn(Collections::emptyList);

        registrar.onApplicationEvent(event);

        verify(executor, never()).refresh(any());
    }

    @Test
    public void providerThrows_refreshErrorLoggedNoPropagation() {
        registerBeans(new RefreshService());
        when(context.getBean(KeysProvider.class)).thenReturn(() -> {
            throw new IllegalStateException("provider-down");
        });

        registrar.onApplicationEvent(event);

        verify(executor, never()).refresh(any());
    }

    @Test
    public void distributedLock_acquired_runsAndUnlocks() {
        registerBeans(new LockService());
        when(context.getBean(LockKeysProvider.class)).thenAnswer(inv -> (FluxPreheatDataProvider<String>) () -> java.util.List.of("k"));
        when(distributedLock.tryLock(any(), anyLong(), anyLong())).thenReturn(true);
        when(cacheManager.getCache("lock-cache")).thenReturn(mock(FluxCache.class));

        registrar.onApplicationEvent(event);

        verify(executor, org.mockito.Mockito.times(1)).refresh(any(FluxCacheRefreshContext.class));
        verify(distributedLock).unlock(any());
    }

    @Test
    public void distributedLock_notAcquired_skips() {
        registerBeans(new LockService());
        when(context.getBean(LockKeysProvider.class)).thenAnswer(inv -> (FluxPreheatDataProvider<String>) () -> java.util.List.of("k"));
        when(distributedLock.tryLock(any(), anyLong(), anyLong())).thenReturn(false);

        registrar.onApplicationEvent(event);

        verify(executor, never()).refresh(any());
        verify(distributedLock, never()).unlock(any());
    }

    @Test
    public void distributedLock_missingBean_skipsRefresh() {
        registrar = new FluxRefreshTaskRegistrar(context, scheduler, cacheManager, null, executor);
        registerBeans(new LockService());
        when(context.getBean(LockKeysProvider.class)).thenAnswer(inv -> (FluxPreheatDataProvider<String>) () -> java.util.List.of("k"));

        registrar.onApplicationEvent(event);

        verify(executor, never()).refresh(any());
    }

    @Test
    public void distributedLock_interrupted_setsInterruptFlag() {
        FluxDistributedLock interrupting = new FluxDistributedLock() {
            @Override
            public boolean tryLock(String key, long waitSeconds, long leaseSeconds) {
                Thread.currentThread().interrupt();
                return false;
            }

            @Override
            public void unlock(String key) {
            }
        };
        registrar = new FluxRefreshTaskRegistrar(context, scheduler, cacheManager, interrupting, executor);
        registerBeans(new LockService());
        when(context.getBean(LockKeysProvider.class)).thenAnswer(inv -> (FluxPreheatDataProvider<String>) () -> java.util.List.of("k"));

        registrar.onApplicationEvent(event);

        assertTrue(Thread.interrupted());
    }

    @Test
    public void preheatOnStartup_runsOneTimeTask() {
        registerBeans(new PreheatService());
        when(context.getBean(PreheatKeysProvider.class)).thenAnswer(inv -> (FluxPreheatDataProvider<String>) () -> java.util.List.of("k"));

        registrar.onApplicationEvent(event);

        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
        verify(executor, org.mockito.Mockito.times(2)).refresh(any(FluxCacheRefreshContext.class));
    }

    @Test
    public void preheatOnStartup_withJitterAndLock_usesOneTimeSchedule() {
        registerBeans(new PreheatJitterService());
        when(context.getBean(PreheatKeysProvider.class)).thenAnswer(inv -> (FluxPreheatDataProvider<String>) () -> java.util.List.of("k"));
        when(distributedLock.tryLock(any(), anyLong(), anyLong())).thenReturn(true);

        registrar.onApplicationEvent(event);

        verify(executor, org.mockito.Mockito.times(2)).refresh(any(FluxCacheRefreshContext.class));
    }

    // ---------- fixtures ----------

    public static class RefreshService {

        @FluxCacheable(cacheName = "refresh-cache",
                refresh = @FluxRefresh(enabled = true, fixedRate = 5,
                        distributedLock = false, provider = KeysProvider.class))
        public String load(String key) {
            return "v-" + key;
        }
    }

    public static class DisabledService {

        @FluxCacheable(cacheName = "no-refresh",
                refresh = @FluxRefresh(enabled = false, distributedLock = false))
        public String load(String key) {
            return "v";
        }
    }

    public static class CronService {

        @FluxCacheable(cacheName = "cron-cache",
                refresh = @FluxRefresh(enabled = true, cron = "0 */5 * * * *",
                        distributedLock = false, provider = KeysProvider.class))
        public String load(String key) {
            return "v";
        }
    }

    public static class FixedDelayService {

        @FluxCacheable(cacheName = "delay-cache",
                refresh = @FluxRefresh(enabled = true, fixedDelay = 10,
                        distributedLock = false, provider = KeysProvider.class))
        public String load(String key) {
            return "v";
        }
    }

    public static class NoScheduleService {

        @FluxCacheable(cacheName = "no-schedule",
                refresh = @FluxRefresh(enabled = true, distributedLock = false,
                        provider = KeysProvider.class))
        public String load(String key) {
            return "v";
        }
    }

    public static class LockService {

        @FluxCacheable(cacheName = "lock-cache",
                refresh = @FluxRefresh(enabled = true, fixedRate = 5, distributedLock = true,
                        lockWaitSeconds = 1, lockLeaseSeconds = 10, provider = LockKeysProvider.class))
        public String load(String key) {
            return "v";
        }
    }

    public static class PreheatService {

        @FluxCacheable(cacheName = "preheat-cache",
                refresh = @FluxRefresh(enabled = true, fixedRate = 5, preheatOnStartup = true,
                        distributedLock = false, provider = PreheatKeysProvider.class))
        public String load(String key) {
            return "v";
        }
    }

    public static class PreheatJitterService {

        @FluxCacheable(cacheName = "preheat-jitter",
                refresh = @FluxRefresh(enabled = true, fixedRate = 5, preheatOnStartup = true,
                        jitterMillis = 10, initialDelay = 1, distributedLock = true,
                        provider = PreheatKeysProvider.class))
        public String load(String key) {
            return "v";
        }
    }

    public static class NoneProviderService {

        @FluxCacheable(cacheName = "none-provider",
                refresh = @FluxRefresh(enabled = true, fixedRate = 5, distributedLock = false,
                        provider = FluxPreheatDataProvider.None.class))
        public String load(String key) {
            return "v";
        }
    }

    public interface KeysProvider extends FluxPreheatDataProvider<String> {
    }

    public interface LockKeysProvider extends FluxPreheatDataProvider<String> {
    }

    public interface PreheatKeysProvider extends FluxPreheatDataProvider<String> {
    }
}