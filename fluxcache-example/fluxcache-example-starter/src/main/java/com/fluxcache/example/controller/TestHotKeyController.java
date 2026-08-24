package com.fluxcache.example.controller;

import com.fluxcache.core.monitor.FluxHotKeyDetector;
import com.fluxcache.core.monitor.FluxHotKeySnapshot;
import com.fluxcache.example.service.HotKeyDemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 热 Key 演示：模拟热点流量，触发 {@link FluxHotKeyDetector} 探测。
 *
 * @author : wh
 * @date : 2026/08/07
 * @description:
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class TestHotKeyController {

    private final HotKeyDemoService hotKeyDemoService;

    private final ObjectProvider<FluxHotKeyDetector> hotKeyDetectorProvider;

    /**
     * 模拟热点流量：按固定 QPS 持续打压单个 key，1 秒后即可观察探测结果。
     *
     * @param seconds     持续秒数，需覆盖至少 2 个探测分片窗口
     * @param qps         每秒请求数，需高于 hot-qps-threshold
     * @param concurrency 压测并发线程数
     * @param name        被打压的 key
     * @return 请求总数与是否被判定为热 key
     */
    @GetMapping("/hot-key/pressure")
    public Map<String, Object> pressure(@RequestParam(defaultValue = "10") int seconds,
        @RequestParam(defaultValue = "500") int qps,
        @RequestParam(defaultValue = "64") int concurrency,
        @RequestParam(defaultValue = "hotKey-001") String name) {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        AtomicLong requestCount = new AtomicLong();
        while (System.currentTimeMillis() < deadline) {
            long batchDeadline = System.currentTimeMillis() + 1000L;
            for (int i = 0; i < qps; i++) {
                pool.execute(() -> {
                    requestCount.incrementAndGet();
                    hotKeyDemoService.getHotKeyData(name);
                });
            }
            long remain = batchDeadline - System.currentTimeMillis();
            if (remain > 0) {
                try {
                    Thread.sleep(remain);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        FluxHotKeyDetector detector = hotKeyDetectorProvider.getIfAvailable();
        Map<String, Object> result = new HashMap<>();
        result.put("requestCount", requestCount.get());
        result.put("hotKeyDetected", detector != null && detector.isHotKey(HotKeyDemoService.HOT_KEY_DEMO_CACHE, name));
        return result;
    }

    /**
     * 查询当前热 key 快照。
     *
     * @return 热 key 列表（含命中/未命中/QPS/命中率），未启用探测时返回空列表
     */
    @GetMapping("/hot-keys")
    public List<FluxHotKeySnapshot> hotKeys() {
        FluxHotKeyDetector detector = hotKeyDetectorProvider.getIfAvailable();
        if (detector == null) {
            return Collections.emptyList();
        }
        return detector.getHotKeysSnapshot();
    }
}
