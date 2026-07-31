package com.fluxcache.example.controller;

import com.fluxcache.core.DefaultFluxCacheManager;
import com.fluxcache.core.FluxCache;
import com.fluxcache.core.impl.FluxMultiLevelCache;
import com.fluxcache.example.FluxCacheApplication;
import com.fluxcache.example.config.MyFluxCacheDataRegistered;
import com.fluxcache.example.vo.StudentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * FluxCache 集成行为测试（通过 Spring 代理的 Controller 方法触发注解缓存）。
 */
@SpringBootTest(classes = FluxCacheApplication.class)
@Testcontainers
class FluxCacheBehaviorTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:6.2.6"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("redis.host", redis::getHost);
        registry.add("redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("redis.password", () -> "");
    }

    @Autowired
    private TestController testController;

    @Autowired
    private DefaultFluxCacheManager cacheManager;

    private static String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static int firstAge(List<StudentVO> vos) {
        assertThat(vos).isNotEmpty();
        return vos.get(0).getAge();
    }

    private <T> void assertHit(Function<String, T> loader, String key) {
        T first = loader.apply(key);
        T second = loader.apply(key);
        assertThat(second).isEqualTo(first);
    }

    /**
     * 当前节点也会消费自己发出的 Redis pub/sub，需等 listener 处理完再读/写，
     * 否则会出现「刚回填又被异步 evict 清掉」的竞态。
     */
    private void awaitCacheSyncSettled() {
        await().pollDelay(500, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS).until(() -> true);
    }

    private void awaitCachedAge(String cacheName, String key, int expectedAge) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            FluxCache.ValueWrapper<?> wrapper = cacheManager.getCache(cacheName).get(key);
            assertThat(wrapper).isNotNull();
            assertThat(firstAge((List<StudentVO>) wrapper.get())).isEqualTo(expectedAge);
        });
    }

    @Nested
    @DisplayName("Caffeine 一级缓存")
    class CaffeineFirstLevel {

        @Test
        @DisplayName("同 key 命中；不同 key 不共享")
        void sameKey_hitsCache_differentKey_misses() {
            String keyA = uniqueKey("caf");
            String keyB = uniqueKey("caf");

            int ageA = firstAge(testController.firstCacheByCaffeine(keyA));
            assertThat(firstAge(testController.firstCacheByCaffeine(keyA))).isEqualTo(ageA);
            assertThat(firstAge(testController.firstCacheByCaffeine(keyB))).isNotEqualTo(ageA);
        }

        @Test
        @DisplayName("allowCacheNull=true 时缓存 null")
        void allowNull_cachesNullValue() {
            String key = uniqueKey("caf-null");
            assertThat(testController.mockSelectSqlToNullByFirstCache(key)).isNull();
            assertThat(testController.mockSelectSqlToNullByFirstCache(key)).isNull();

            FluxCache.ValueWrapper<?> wrapper = cacheManager.getCache("testNullFirstCache").get(key);
            assertThat(wrapper).isNotNull();
            assertThat(wrapper.get()).isNull();
        }

        @Test
        @DisplayName("allowCacheNull=false 时不缓存 null")
        void disallowNull_doesNotCacheNullValue() {
            String key = uniqueKey("caf-no-null");
            assertThat(testController.mockSelectSqlToNoNullByFirstCache(key)).isNull();

            FluxCache.ValueWrapper<?> wrapper = cacheManager.getCache("testNoNullFirstCache").get(key);
            assertThat(wrapper).isNull();

            assertThat(testController.mockSelectSqlToNoNullByFirstCache(key)).isNull();
            assertThat(cacheManager.getCache("testNoNullFirstCache").get(key)).isNull();
        }

        @Test
        @DisplayName("Optional 返回值可缓存，evict 后重新加载")
        void optionalValue_cacheAndEvict() {
            String key = uniqueKey("caf-opt");
            Optional<List<StudentVO>> first = testController.firstCacheByCaffeineAndOptional(key);
            assertThat(testController.firstCacheByCaffeineAndOptional(key)).isEqualTo(first);

            testController.clearFirstCacheByCaffeineAndOptional(key);
            awaitCacheSyncSettled();

            Optional<List<StudentVO>> reloaded = testController.firstCacheByCaffeineAndOptional(key);
            assertThat(reloaded).isPresent();
            assertThat(firstAge(reloaded.get())).isNotEqualTo(firstAge(first.orElseThrow()));
            assertThat(firstAge(testController.firstCacheByCaffeineAndOptional(key).orElseThrow()))
                    .isEqualTo(firstAge(reloaded.get()));
        }

        @Test
        @DisplayName("按 key evict 后重新加载；不影响其它 key")
        void evictByKey_reloadsOnlyTargetKey() {
            String keyA = uniqueKey("caf-evict");
            String keyB = uniqueKey("caf-evict");

            int ageA = firstAge(testController.firstCacheByCaffeine(keyA));
            int ageB = firstAge(testController.firstCacheByCaffeine(keyB));

            testController.clearFirstCacheByCaffeineByKey(keyA);
            awaitCacheSyncSettled();

            int reloadedA = firstAge(testController.firstCacheByCaffeine(keyA));
            assertThat(reloadedA).isNotEqualTo(ageA);
            awaitCachedAge("firstCacheByCaffeine", keyA, reloadedA);
            assertThat(firstAge(testController.firstCacheByCaffeine(keyB))).isEqualTo(ageB);
        }

        @Test
        @DisplayName("按 cacheName 清空后所有 key 重新加载")
        void evictByCacheName_reloadsAllKeys() {
            String keyA = uniqueKey("caf-clear");
            String keyB = uniqueKey("caf-clear");
            int ageA = firstAge(testController.firstCacheByCaffeine(keyA));
            int ageB = firstAge(testController.firstCacheByCaffeine(keyB));

            testController.clearFirstCacheByCaffeineByName("firstCacheByCaffeine");
            awaitCacheSyncSettled();

            assertThat(firstAge(testController.firstCacheByCaffeine(keyA))).isNotEqualTo(ageA);
            assertThat(firstAge(testController.firstCacheByCaffeine(keyB))).isNotEqualTo(ageB);
            awaitCacheSyncSettled();
        }

        @Test
        @DisplayName("CachePut 更新缓存值")
        void cachePut_updatesCachedValue() {
            String key = uniqueKey("caf-put");
            int age = firstAge(testController.firstCacheByCaffeine(key));
            assertThat(firstAge(testController.firstCacheByCaffeine(key))).isEqualTo(age);

            int putAge = firstAge(testController.firstCacheByCaffeinePutCache(key));
            assertThat(putAge).isNotEqualTo(age);
            awaitCachedAge("firstCacheByCaffeine", key, putAge);
        }
    }

    @Nested
    @DisplayName("Redis 一级缓存")
    class RedisFirstLevel {

        @Test
        @DisplayName("RMap 同 key 命中；不同 key 不共享")
        void rMap_sameKey_hitsCache() {
            String keyA = uniqueKey("redis-map");
            String keyB = uniqueKey("redis-map");
            int ageA = firstAge(testController.firstCacheByRedis(keyA));
            assertThat(firstAge(testController.firstCacheByRedis(keyA))).isEqualTo(ageA);
            assertThat(firstAge(testController.firstCacheByRedis(keyB))).isNotEqualTo(ageA);
        }

        @Test
        @DisplayName("Bucket 同 key 命中；不同 key 不共享")
        void bucket_sameKey_hitsCache() {
            String keyA = uniqueKey("redis-bucket");
            String keyB = uniqueKey("redis-bucket");
            int ageA = firstAge(testController.firstCacheByRedisBucket(keyA));
            assertThat(firstAge(testController.firstCacheByRedisBucket(keyA))).isEqualTo(ageA);
            assertThat(firstAge(testController.firstCacheByRedisBucket(keyB))).isNotEqualTo(ageA);
        }

        @Test
        @DisplayName("Bucket 缓存 null")
        void bucket_cachesNull() {
            String key = uniqueKey("redis-bucket-null");
            assertThat(testController.firstNullCacheByRedisBucket(key)).isNull();
            assertThat(testController.firstNullCacheByRedisBucket(key)).isNull();

            FluxCache.ValueWrapper<?> wrapper = cacheManager.getCache("studentRedisBucketNull").get(key);
            assertThat(wrapper).isNotNull();
            assertThat(wrapper.get()).isNull();
        }

        @Test
        @DisplayName("Bucket evict 后重新加载")
        void bucket_evict_reloads() {
            String key = uniqueKey("redis-bucket-evict");
            int age = firstAge(testController.firstCacheByRedisBucket(key));
            assertThat(firstAge(testController.firstCacheByRedisBucket(key))).isEqualTo(age);

            testController.deleteFirstCacheByRedisBucket(key);
            assertThat(firstAge(testController.firstCacheByRedisBucket(key))).isNotEqualTo(age);
        }

        @Test
        @DisplayName("Bucket CachePut 更新缓存值")
        void bucket_cachePut_updatesValue() {
            String key = uniqueKey("redis-bucket-put");
            int age = firstAge(testController.firstCacheByRedisBucket(key));
            testController.deleteFirstCacheByRedisBucket(key);

            int putAge = firstAge(testController.putFirstCacheByRedisBucket(key));
            assertThat(putAge).isNotEqualTo(age);
            assertThat(firstAge(testController.firstCacheByRedisBucket(key))).isEqualTo(putAge);
        }

        @Test
        @DisplayName("RMap CachePut 更新缓存值")
        void rMap_cachePut_updatesValue() {
            String key = uniqueKey("redis-map-put");
            int age = firstAge(testController.firstCacheByRedis(key));
            assertThat(firstAge(testController.firstCacheByRedis(key))).isEqualTo(age);

            int putAge = firstAge(testController.redisCachePut(key));
            assertThat(putAge).isNotEqualTo(age);
            assertThat(firstAge(testController.firstCacheByRedis(key))).isEqualTo(putAge);
        }
    }

    @Nested
    @DisplayName("二级缓存")
    class SecondaryCache {

        @Test
        @DisplayName("同 key 命中")
        void sameKey_hitsCache() {
            assertHit(testController::secondaryCacheByCaffeineRedis, uniqueKey("l2"));
        }

        @Test
        @DisplayName("L1 miss 时回源 L2 并回填 L1，不重新查库")
        void l1Miss_hitsL2_andBackfillsL1() {
            String key = uniqueKey("l2-backfill");
            int age = firstAge(testController.secondaryCacheByCaffeineRedis(key));

            @SuppressWarnings({"rawtypes", "unchecked"})
            FluxMultiLevelCache multilevel =
                    (FluxMultiLevelCache) cacheManager.getCache("studentLocalRedis");
            multilevel.getFluxFirstCache().evictDirectly(key);
            assertThat(multilevel.getFluxFirstCache().get(key)).isNull();

            int fromL2 = firstAge(testController.secondaryCacheByCaffeineRedis(key));
            assertThat(fromL2).isEqualTo(age);
            FluxCache.ValueWrapper l1 = multilevel.getFluxFirstCache().get(key);
            assertThat(l1).isNotNull();
            assertThat(firstAge((List<StudentVO>) l1.get())).isEqualTo(age);
        }

        @Test
        @DisplayName("二级缓存允许缓存 null")
        void allowNull_cachesNullValue() {
            String key = uniqueKey("l2-null");
            assertThat(testController.mockSelectSqlToNullBySecondaryCache(key)).isNull();
            assertThat(testController.mockSelectSqlToNullBySecondaryCache(key)).isNull();

            FluxCache.ValueWrapper<?> wrapper = cacheManager.getCache("testNullSecondaryCache").get(key);
            assertThat(wrapper).isNotNull();
            assertThat(wrapper.get()).isNull();
        }
    }

    @Nested
    @DisplayName("手动注册缓存")
    class ManualCache {

        @Test
        @DisplayName("一级手动缓存同 key 命中")
        void firstLevel_sameKey_hitsCache() {
            assertHit(testController::productManualCache, uniqueKey("manual"));
        }

        @Test
        @DisplayName("多级手动缓存同 key 命中")
        void multiLevel_sameKey_hitsCache() {
            assertHit(testController::productManualMultiLevelCache, uniqueKey("manual-l2"));
        }
    }

    @Nested
    @DisplayName("批量读写")
    class BatchOperations {

        @ParameterizedTest(name = "多级批量 async={0}")
        @ValueSource(booleans = {true, false})
        void multiLevel_batchPutAndGet(boolean async) {
            String prefix = uniqueKey("batch-l2");
            testController.putAllManualMultiLevelCache(prefix, async);
            awaitCacheKeys(MyFluxCacheDataRegistered.PRODUCT_MANUAL_MultiLevel_CACHE, prefix, async);

            Map<String, List> first = testController.getAllManualMultiLevelCache(prefix, async);
            Map<String, List> other = testController.getAllManualMultiLevelCache(uniqueKey("batch-l2-other"), async);
            assertThat(first).isNotEqualTo(other);
            assertThat(testController.getAllManualMultiLevelCache(prefix, async)).isEqualTo(first);
        }

        @ParameterizedTest(name = "Redis 一级批量 async={0}")
        @ValueSource(booleans = {true, false})
        void redisFirst_batchPutAndGet(boolean async) {
            String prefix = uniqueKey("batch-redis");
            testController.pullAllRedisFirstCache(prefix, async);
            awaitCacheKeys(MyFluxCacheDataRegistered.PRODUCT_Redis_First_CACHE, prefix, async);

            Map<String, List> first = testController.getAllRedisFirstCache(prefix, async);
            Map<String, List> other = testController.getAllRedisFirstCache(uniqueKey("batch-redis-other"), async);
            assertThat(first).isNotEqualTo(other);
            assertThat(testController.getAllRedisFirstCache(prefix, async)).isEqualTo(first);
        }

        @ParameterizedTest(name = "本地一级批量 async={0}")
        @ValueSource(booleans = {true, false})
        void localFirst_batchPutAndGet(boolean async) {
            String prefix = uniqueKey("batch-local");
            testController.pullAllLocalFirstCache(prefix, async);
            awaitCacheKeys(MyFluxCacheDataRegistered.PRODUCT_LOCAL_FIRST_CACHE, prefix, async);

            Map<String, List> first = testController.getAllLocalFirstCache(prefix, async);
            Map<String, List> other = testController.getAllLocalFirstCache(uniqueKey("batch-local-other"), async);
            assertThat(first).isNotEqualTo(other);
            assertThat(testController.getAllLocalFirstCache(prefix, async)).isEqualTo(first);
        }

        private void awaitCacheKeys(String cacheName, String prefix, boolean async) {
            FluxCache<String, List> cache = cacheManager.getCache(cacheName);
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                Map<String, List> values = async
                        ? cache.getAllAsync(List.of(prefix + "1", prefix + "2"), List.class)
                        : cache.getAll(List.of(prefix + "1", prefix + "2"), List.class);
                assertThat(values).hasSize(2);
            });
        }
    }
}
