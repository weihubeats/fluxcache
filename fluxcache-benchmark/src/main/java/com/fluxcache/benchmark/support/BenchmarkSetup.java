package com.fluxcache.benchmark.support;

import com.fluxcache.core.FluxCache;
import com.fluxcache.core.FluxCacheManager;
import com.fluxcache.core.caffeine.FluxCaffeineCache;
import com.fluxcache.core.caffeine.sync.CacheSyncStrategy;
import com.fluxcache.core.caffeine.sync.NoOpCacheSyncStrategy;
import com.fluxcache.core.enums.FluxCacheLevel;
import com.fluxcache.core.enums.FluxCacheType;
import com.fluxcache.core.impl.FluxAbstractValueAdaptingCache;
import com.fluxcache.core.impl.FluxCacheFactory;
import com.fluxcache.core.impl.FluxMultiLevelCache;
import com.fluxcache.core.impl.creator.CaffeineFluxCacheCreator;
import com.fluxcache.core.interceptor.FluxCacheAnnotationInterceptor;
import com.fluxcache.core.interceptor.FluxCacheOperationSource;
import com.fluxcache.core.model.FluxCacheCacheable;
import com.fluxcache.core.model.FluxCacheConfig;
import com.fluxcache.core.model.FluxCacheOperation;
import com.fluxcache.core.model.FluxMultilevelCacheCacheable;
import com.fluxcache.core.monitor.FluxCacheMonitor;
import com.fluxcache.core.monitor.FluxCacheMonitorEvent;
import com.fluxcache.core.monitor.FluxCacheStatics;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.core.spi.FluxCacheCreateContext;
import com.fluxcache.core.spi.FluxCacheCreator;
import com.fluxcache.core.spi.FluxCacheCreatorRegistry;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Builds a complete annotation-path benchmark environment (cache + interceptor + SpEL key
 * resolution + monitor), mirroring how the framework is actually used in a Spring Boot app.
 *
 * @author : wh
 * @date : 2026/8/4
 */
public final class BenchmarkSetup {

    public static final String CACHE_NAME = "bench-cache";

    private BenchmarkSetup() {
    }

    public static class Env {

        public final FluxCacheProperties properties;
        public final FluxCache<String, Object> cache;
        public final FluxCacheAnnotationInterceptor interceptor;
        public final SimulatedRedis redis;
        public final Method loadMethod;
        public final Object target = new Target();

        @SuppressWarnings({"rawtypes", "unchecked"})
        public Env() {
            this(new SimulatedRedis(), true, FluxCacheLevel.SecondaryCacheable);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public Env(boolean singleFlight, FluxCacheLevel level) {
            this(new SimulatedRedis(), singleFlight, level);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public Env(SimulatedRedis redis, boolean singleFlight, FluxCacheLevel level) {
            this.redis = redis;
            this.properties = new FluxCacheProperties();
            this.properties.setSingleFlightEnable(singleFlight);
            this.properties.setSingleFlightTimeoutMillis(5000L);

            FluxCacheMonitor monitor = new NoopFluxCacheMonitor();
            CacheSyncStrategy syncStrategy = new NoOpCacheSyncStrategy();

            FluxMultilevelCacheCacheable op = (FluxMultilevelCacheCacheable)
                    new FluxMultilevelCacheCacheable.Builder()
                            .setFirstCacheConfig(caffeineConfig(30L, 100_000))
                            .setSecondaryCacheable(redisConfig())
                            .setAllowNullValues(true)
                            .setCacheName(CACHE_NAME)
                            .setMethodName("benchmarkLoad")
                            .setKey("#name")
                            .setFluxCacheLevel(level)
                            .build();

            FluxCacheFactory factory = level == FluxCacheLevel.SecondaryCacheable
                    ? new FluxCacheFactory(benchmarkRegistry(redis))
                    : FluxCacheFactory.withDefaults();
            cache = (FluxCache<String, Object>) factory.createFluxCache(op, properties, syncStrategy, monitor);

            FluxCacheManager cacheManager = new BenchmarkCacheManager(cache);
            FluxCacheOperationSource operationSource = (method, targetClass) -> op;
            interceptor = new FluxCacheAnnotationInterceptor(properties, operationSource, cacheManager, monitor);
            try {
                loadMethod = Target.class.getMethod("benchmarkLoad", String.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("benchmark target method missing", e);
            }
        }

        /**
         * Full annotation path: SpEL key resolution + interceptor + single-flight + cache + monitor.
         */
        public Object invokeWithKey(String key) throws Throwable {
            return invokeWithKey(key, () -> "benchmark-value");
        }

        /**
         * Same as {@link #invokeWithKey(String)} but with a custom datasource loader invoked on miss.
         */
        public Object invokeWithKey(String key, java.util.function.Supplier<Object> loader) throws Throwable {
            return interceptor.invoke(methodInvocation(key, loader));
        }

        private MethodInvocation methodInvocation(String key, java.util.function.Supplier<Object> loader) {
            return new MethodInvocation() {
                @Override
                public Method getMethod() {
                    return loadMethod;
                }

                @Override
                public Object[] getArguments() {
                    return new Object[]{key};
                }

                @Override
                public Object getThis() {
                    return target;
                }

                @Override
                public Object proceed() throws Throwable {
                    return loader.get();
                }

                @Override
                public java.lang.reflect.AccessibleObject getStaticPart() {
                    return loadMethod;
                }
            };
        }

        @SuppressWarnings("unchecked")
        public FluxMultiLevelCache<String, Object> multiLevelCache() {
            return (FluxMultiLevelCache<String, Object>) cache;
        }

        /**
         * L1 cache instance regardless of cache level: unwraps the multi-level composite or
         * returns the single-level cache itself.
         */
        @SuppressWarnings("unchecked")
        public FluxCaffeineCache firstCache() {
            if (cache instanceof FluxMultiLevelCache) {
                return (FluxCaffeineCache) multiLevelCache().getFluxFirstCache();
            }
            return (FluxCaffeineCache) cache;
        }
    }

    private static FluxCacheCreatorRegistry benchmarkRegistry(SimulatedRedis redis) {
        return new FluxCacheCreatorRegistry(Arrays.asList(
                new CaffeineFluxCacheCreator(),
                new SimulatedRedisCreator(redis)
        ));
    }

    private static FluxCacheConfig caffeineConfig(long ttl, int maxSize) {
        return new FluxCacheConfig.Builder()
                .setTtl(ttl)
                .setInitSize(16)
                .setMaxSize(maxSize)
                .setUnit(TimeUnit.MINUTES)
                .setCacheType(FluxCacheType.CAFFEINE)
                .build();
    }

    private static FluxCacheConfig redisConfig() {
        return new FluxCacheConfig.Builder()
                .setTtl(30L)
                .setInitSize(16)
                .setMaxSize(100_000)
                .setUnit(TimeUnit.MINUTES)
                .setCacheType(FluxCacheType.REDIS)
                .build();
    }

    private static class SimulatedRedisCreator implements FluxCacheCreator {

        private final SimulatedRedis redis;

        private SimulatedRedisCreator(SimulatedRedis redis) {
            this.redis = redis;
        }

        @Override
        public FluxCacheType supportType() {
            return FluxCacheType.REDIS;
        }

        @Override
        public FluxAbstractValueAdaptingCache<?, ?> create(FluxCacheCacheable cacheable, FluxCacheCreateContext context) {
            return new SimulatedRedisCache(cacheable.getCacheName(), redis);
        }
    }

    public static class Target {

        public String benchmarkLoad(String name) {
            // never invoked: loading is done through the benchmark invoker
            return "benchmark-value";
        }
    }

    public static final class NoopFluxCacheMonitor implements FluxCacheMonitor {

        @Override
        public void createNewCacheStatics(String cacheName) {
        }

        @Override
        public FluxCacheStatics getCacheStatics(String cacheName) {
            return null;
        }

        @Override
        public void publishMonitorEvent(FluxCacheMonitorEvent fluxCacheMonitorEvent) {
        }

        @Override
        public void createCacheStaticsMap(ConcurrentMap<String, FluxCacheOperation> data) {
        }
    }

    private static class BenchmarkCacheManager implements FluxCacheManager {

        private final FluxCache<String, Object> cache;

        private BenchmarkCacheManager(FluxCache<String, Object> cache) {
            this.cache = cache;
        }

        @Override
        public void createCache(FluxMultilevelCacheCacheable cacheable) {
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public <K, V> FluxCache<K, V> getCache(String cacheName) {
            return (FluxCache<K, V>) cache;
        }

        @Override
        public <K, V> boolean putCache(String key, FluxCache<K, V> cache) {
            return true;
        }

        @Override
        public List<FluxCache> getAllCaches() {
            return List.of(cache);
        }

        @Override
        public <K, V> V getCacheOrPut(String cacheName, K key, Callable<V> valueLoader) {
            return null;
        }

        @Override
        public <K, V> boolean evictCache(String cacheName, List<K> keys) {
            return true;
        }

        @Override
        public <K, V> boolean clearCacheByName(String cacheName) {
            return true;
        }

        @Override
        public FluxCacheOperation getCacheMetaData(String cacheName) {
            return null;
        }

        @Override
        public List<FluxCacheOperation> getAllCacheMetaData() {
            return List.of();
        }

        @Override
        public FluxCacheStatics getCacheStatics(String cacheName) {
            return null;
        }
    }
}