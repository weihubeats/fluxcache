package com.fluxcache.example.controller;

import com.fluxcache.core.DefaultFluxCacheManager;
import com.fluxcache.core.monitor.FluxCacheInfo;
import com.fluxcache.core.monitor.FluxCacheStatics;
import com.fluxcache.core.properties.FluxCacheProperties;
import com.fluxcache.example.FluxCacheApplication;
import com.fluxcache.example.vo.StudentVO;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author : wh
 * @date : 2025/1/21 18:22
 * @description:
 */
@SpringBootTest(classes = FluxCacheApplication.class)
@Slf4j
@Testcontainers
public class TestControllerTest {

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:6.2.6"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("redis.host", redis::getHost);
        registry.add("redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("redis.password", () -> "");
    }

    private final static Long SLEEP_TIME = 3L;

    @Autowired
    private FluxCacheProperties cacheProperties;

    @Autowired
    private TestController testController;

    @Autowired
    private DefaultFluxCacheManager cacheManager;

    @Test
    public void testFirstCacheByCaffeine() {
        List<StudentVO> vos = testController.firstCacheByCaffeine("aaa");
        StudentVO vo = vos.get(0);
        int age = vo.getAge();
        List<StudentVO> vos1 = testController.firstCacheByCaffeine("aaa");
        StudentVO vo1 = vos1.get(0);
        int age1 = vo1.getAge();
        assertEquals(age, age1);
        List<StudentVO> vos2 = testController.firstCacheByCaffeine("bb");
        StudentVO vo2 = vos2.get(0);
        assertNotEquals(age, vo2.getAge());
    }

    @Test
    public void testFirstCacheByCaffeineByNull() {
        List<StudentVO> vos = testController.mockSelectSqlToNullByFirstCache("orderNull");
        List<StudentVO> vos1 = testController.mockSelectSqlToNullByFirstCache("orderNull");
    }

    @Test
    public void firstCacheByCaffeineAndOptional() {
        String key = "orderOptional";
        Optional<List<StudentVO>> vosOptionals = testController.firstCacheByCaffeineAndOptional(key);
        List<StudentVO> vos = vosOptionals.get();
        Optional<List<StudentVO>> vosOptional1s = testController.firstCacheByCaffeineAndOptional(key);
        List<StudentVO> vos1 = vosOptional1s.get();
        assertEquals(vos, vos1);

        testController.clearFirstCacheByCaffeineAndOptional(key);

        vosOptionals = testController.firstCacheByCaffeineAndOptional(key);

        assertNotEquals(vosOptionals.get(), vos);

        vosOptional1s = testController.firstCacheByCaffeineAndOptional(key);
        assertEquals(vosOptionals.get(), vosOptional1s.get());

    }

    @Test
    public void testFirstCacheByRedis() {
        List<StudentVO> vos = testController.firstCacheByRedis("aaa");
        StudentVO vo = vos.get(0);
        int age = vo.getAge();

        List<StudentVO> vos1 = testController.firstCacheByRedis("aaa");
        StudentVO vo1 = vos1.get(0);
        int age1 = vo1.getAge();
        assertEquals(age, age1);

        List<StudentVO> vos2 = testController.firstCacheByCaffeine("bb");
        StudentVO vo2 = vos2.get(0);
        assertNotEquals(age, vo2.getAge());
    }

    @Test
    public void testFirstCacheByRedisBucket() {
        String cacheName = "aaa-bucket";
        String cacheNameB = "bb-bucket";
        List<StudentVO> vos = testController.firstCacheByRedisBucket(cacheName);
        StudentVO vo = vos.get(0);
        int age = vo.getAge();

        List<StudentVO> vos1 = testController.firstCacheByRedisBucket(cacheName);
        StudentVO vo1 = vos1.get(0);
        int age1 = vo1.getAge();
        assertEquals(age, age1);

        List<StudentVO> vos2 = testController.firstCacheByRedisBucket(cacheNameB);
        StudentVO vo2 = vos2.get(0);
        assertNotEquals(age, vo2.getAge());

    }

    @Test
    public void testNullFirstCacheByRedisBucket() {
        String cacheName = "abcd-null-bucket";
        List<StudentVO> vos = testController.firstNullCacheByRedisBucket(cacheName);
        assertNull(vos);
        List<StudentVO> vos1 = testController.firstNullCacheByRedisBucket(cacheName);
        assertNull(vos1);
    }

    @Test
    public void testDeleteFirstCacheByRedisBucket() {
        String cacheName = "a-delete-bucket";
        List<StudentVO> vos = testController.firstCacheByRedisBucket(cacheName);

        StudentVO vo = vos.get(0);
        int age = vo.getAge();

        List<StudentVO> vos1 = testController.firstCacheByRedisBucket(cacheName);
        StudentVO vo1 = vos1.get(0);
        int age1 = vo1.getAge();
        assertEquals(age, age1);

        testController.deleteFirstCacheByRedisBucket(cacheName);
        List<StudentVO> vos2 = testController.firstCacheByRedisBucket(cacheName);
        StudentVO vo2 = vos2.get(0);
        assertNotEquals(age, vo2.getAge());

    }

    @Test
    public void testPutFirstCacheByRedisBucket() {
        String cacheName = "a-put-bucket";
        List<StudentVO> vos = testController.firstCacheByRedisBucket(cacheName);

        StudentVO vo = vos.get(0);
        int age = vo.getAge();

        List<StudentVO> vos1 = testController.firstCacheByRedisBucket(cacheName);
        StudentVO vo1 = vos1.get(0);
        int age1 = vo1.getAge();
        assertEquals(age, age1);

        testController.deleteFirstCacheByRedisBucket(cacheName);

        List<StudentVO> vos2 = testController.putFirstCacheByRedisBucket(cacheName);
        StudentVO vo2 = vos2.get(0);
        assertNotEquals(age, vo2.getAge());

        List<StudentVO> vos3 = testController.firstCacheByRedisBucket(cacheName);
        StudentVO vo3 = vos3.get(0);
        assertEquals(vo2.getAge(), vo3.getAge());
    }

    @Test
    public void testNullBySecondaryCache() {
        String name = "orderByNull2";
        List<StudentVO> aNull = testController.mockSelectSqlToNullBySecondaryCache(name);
        List<StudentVO> aNull1 = testController.mockSelectSqlToNullBySecondaryCache(name);
        assertEquals(aNull, aNull1);
    }

    @Test
    public void testClearFirstCacheByCaffeineByKey() throws Exception {
        List<StudentVO> vos = testController.firstCacheByCaffeine("aaa");
        StudentVO vo = vos.get(0);
        int age = vo.getAge();
        TimeUnit.SECONDS.sleep(SLEEP_TIME);
        testController.clearFirstCacheByCaffeineByKey("aaa");
        TimeUnit.SECONDS.sleep(SLEEP_TIME);

        log.info("重新查询数据开始");
        List<StudentVO> vos1 = testController.firstCacheByCaffeine("aaa");
        log.info("重新查询数据结束");
        StudentVO vo1 = vos1.get(0);
        int age1 = vo1.getAge();
        assertNotEquals(age, age1);

        List<StudentVO> vos2 = testController.firstCacheByCaffeine("aaa");
        StudentVO vo2 = vos2.get(0);
        int age2 = vo2.getAge();

        testController.clearFirstCacheByCaffeineByKey("bb");
        List<StudentVO> vos3 = testController.firstCacheByCaffeine("aaa");
        StudentVO vo3 = vos3.get(0);
        int age3 = vo3.getAge();
        assertEquals(age2, age3);
    }

    @Test
    public void testClearFirstCacheByCaffeineByName() throws Exception {
        List<StudentVO> vos = testController.firstCacheByCaffeine("aaa");
        StudentVO vo = vos.get(0);
        int age = vo.getAge();

        List<StudentVO> vos1 = testController.firstCacheByCaffeine("aaa");
        StudentVO vo1 = vos1.get(0);
        int age1 = vo1.getAge();

        assertEquals(age, age1);

        List<StudentVO> vobs = testController.firstCacheByCaffeine("bbb");
        StudentVO vob = vobs.get(0);
        int ageb = vob.getAge();

        List<StudentVO> vosb1 = testController.firstCacheByCaffeine("bbb");
        StudentVO vob1 = vosb1.get(0);
        int ageb1 = vob1.getAge();

        assertEquals(ageb, ageb1);

        testController.clearFirstCacheByCaffeineByName("firstCacheByCaffeine");
        TimeUnit.SECONDS.sleep(SLEEP_TIME);

        List<StudentVO> vos3 = testController.firstCacheByCaffeine("aaa");
        StudentVO vo3 = vos3.get(0);
        int age3 = vo3.getAge();

        assertNotEquals(age, age3);

        List<StudentVO> vosb3 = testController.firstCacheByCaffeine("bbb");
        StudentVO vob3 = vosb3.get(0);
        int ageb3 = vob3.getAge();
        assertNotEquals(ageb, ageb3);
    }

    @Test
    public void testFirstCacheByRedisPut() {
        List<StudentVO> aa = testController.firstCacheByRedis("aa");
        StudentVO vo = aa.get(0);
        int age = vo.getAge();
        List<StudentVO> bb = testController.firstCacheByRedis("aa");
        StudentVO vb = bb.get(0);
        int ageB = vb.getAge();
        assertEquals(age, ageB);
        testController.redisCachePut("aa");
        List<StudentVO> cc = testController.firstCacheByRedis("aa");
        StudentVO vc = cc.get(0);
        int ageC = vc.getAge();
        assertNotEquals(age, ageC);
    }

    @Test
    public void testFirstCacheByCaffeinePut() throws Exception {
        List<StudentVO> aa = testController.firstCacheByCaffeine("aa");
        StudentVO vo = aa.get(0);
        int age = vo.getAge();
        List<StudentVO> bb = testController.firstCacheByCaffeine("aa");
        StudentVO vb = bb.get(0);
        int ageB = vb.getAge();
        assertEquals(age, ageB);
        testController.firstCacheByCaffeinePutCache("aa");
        TimeUnit.SECONDS.sleep(SLEEP_TIME);
        List<StudentVO> cc = testController.firstCacheByCaffeine("aa");
        StudentVO vc = cc.get(0);
        int ageC = vc.getAge();
        assertNotEquals(age, ageC);
    }

    @Test
    public void testSecondaryCacheByCaffeineRedis() {
        List<StudentVO> aa = testController.secondaryCacheByCaffeineRedis("product");
        StudentVO vo = aa.get(0);
        int age = vo.getAge();
        List<StudentVO> bb = testController.secondaryCacheByCaffeineRedis("product");
        StudentVO vb = bb.get(0);
        int ageB = vb.getAge();
        assertEquals(age, ageB);
    }

    @Test
    public void testProductManualCache() {
        List<StudentVO> vos = testController.productManualCache("manualProduct");
        StudentVO vo = vos.get(0);
        int age = vo.getAge();
        List<StudentVO> vos1 = testController.productManualCache("manualProduct");
        StudentVO vo1 = vos1.get(0);
        int age1 = vo1.getAge();
        assertEquals(age, age1);
    }

    @Test
    public void testProductManualCachePut() {
        List<StudentVO> vos = testController.productManualMultiLevelCache("productId1");
        StudentVO vo = vos.get(0);
        int age = vo.getAge();
        List<StudentVO> vos1 = testController.productManualMultiLevelCache("productId1");
        StudentVO vo1 = vos1.get(0);
        int age1 = vo1.getAge();
        assertEquals(age, age1);
    }

    @Test
    public void testFluxCacheInfo() {
        List<StudentVO> vos = testController.firstCacheByCaffeine("aaa");
        List<StudentVO> vos1 = testController.firstCacheByCaffeine("aaa");
        FluxCacheStatics caffeine = cacheManager.getCacheStatics("firstCacheByCaffeine");
        LinkedList<FluxCacheInfo> list = caffeine.getFluxCacheInfos();

    }

    @Test
    public void testBathMultiLevelCache() {
        testController.putAllManualMultiLevelCache("aaa", true);
        testController.putAllManualMultiLevelCache("bbb", true);
        Map<String, List> aaa = testController.getAllManualMultiLevelCache("aaa", true);
        Map<String, List> bbb = testController.getAllManualMultiLevelCache("bbb", true);
        assertNotEquals(aaa, bbb);
        Map<String, List> ccc = testController.getAllManualMultiLevelCache("aaa", true);
        assertEquals(aaa, ccc);

        testController.putAllManualMultiLevelCache("ccc", false);
        testController.putAllManualMultiLevelCache("ddd", false);
        Map<String, List> qqq = testController.getAllManualMultiLevelCache("ccc", false);
        Map<String, List> www = testController.getAllManualMultiLevelCache("ddd", false);
        assertNotEquals(qqq, www);
        Map<String, List> rrr = testController.getAllManualMultiLevelCache("ccc", false);
        assertEquals(qqq, rrr);
    }

    @Test
    public void testBathRedisFirstCache() throws InterruptedException {
        testController.pullAllRedisFirstCache("qqq", true);
        TimeUnit.SECONDS.sleep(SLEEP_TIME);
        testController.pullAllRedisFirstCache("www", true);
        TimeUnit.SECONDS.sleep(SLEEP_TIME);
        Map<String, List> aaa = testController.getAllRedisFirstCache("qqq", true);
        Map<String, List> bbb = testController.getAllRedisFirstCache("www", true);
        assertNotEquals(aaa, bbb);
        Map<String, List> ccc = testController.getAllRedisFirstCache("qqq", true);
        assertEquals(aaa, ccc);

        testController.pullAllRedisFirstCache("ttt", false);
        TimeUnit.SECONDS.sleep(SLEEP_TIME);
        testController.pullAllRedisFirstCache("yyy", false);
        TimeUnit.SECONDS.sleep(SLEEP_TIME);
        Map<String, List> qqq = testController.getAllRedisFirstCache("ttt", false);
        Map<String, List> www = testController.getAllRedisFirstCache("yyy", false);
        assertNotEquals(qqq, www);
        Map<String, List> eee = testController.getAllRedisFirstCache("ttt", false);
        assertEquals(qqq, eee);
    }

    @Test
    public void testBathLocalFirstCache() {
        testController.pullAllLocalFirstCache("aaa", true);
        testController.pullAllLocalFirstCache("bbb", true);
        Map<String, List> aaa = testController.getAllLocalFirstCache("aaa", true);
        Map<String, List> bbb = testController.getAllLocalFirstCache("bbb", true);
        assertNotEquals(aaa, bbb);
        Map<String, List> ccc = testController.getAllLocalFirstCache("aaa", true);
        assertEquals(aaa, ccc);

        testController.pullAllLocalFirstCache("ccc", false);
        testController.pullAllLocalFirstCache("ddd", false);
        Map<String, List> ddd = testController.getAllLocalFirstCache("ccc", false);
        Map<String, List> eee = testController.getAllLocalFirstCache("ddd", false);
        assertNotEquals(ddd, eee);
        Map<String, List> fff = testController.getAllLocalFirstCache("ccc", false);
        assertEquals(ddd, fff);
    }

}