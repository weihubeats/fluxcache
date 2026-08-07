---
layout: home
title: FluxCache

hero:
  name: FluxCache
  text: 轻量级多级缓存框架
  tagline: 基于 Spring Boot 的 Caffeine / Redis 多级缓存解决方案。防穿透、防击穿、防雪崩，分布式失效，一条注解搞定。
  image:
    src: /flux-col-logo.svg
    alt: FluxCache
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/getting-started
    - theme: alt
      text: 查看源码
      link: https://github.com/weihubeats/fluxcache

features:
  - icon: 🚀
    title: 性能极致
    details: L1 命中延迟 0.385 µs，相对纯 Redis 快约 6,400x；框架开销仅 +4.4%。
  - icon: 🍃
    title: Caffeine + Redis 多级缓存
    details: 本地内存 L1 + 分布式 L2，兼容 Spring Data Redis 与 Redisson。
  - icon: ⛰️
    title: 防雪崩·穿透·击穿
    details: 随机过期防雪崩、缓存 null 防穿透、单飞 single-flight 防击穿。
  - icon: 📡
    title: 分布式失效
    details: 基于 Redis Pub/Sub 的跨进程缓存删除与更新，多实例实时一致。
  - icon: 🧰
    title: 内建 Dashboard
    details: 缓存元数据管理 / 清空 / 按 key 查询与清理 / 命中率监控。
  - icon: 📊
    title: Micrometer 指标
    details: 导出 Prometheus + Grafana，包含 p50/p95/p99 加载耗时与命中率。
---

## 性能速览

JMH 基准（M2 Max / JDK21 / JMH 1.37），FluxCache 总是走完整注解链路（SpEL + 拦截器 + 单飞 + 监控）：

| 场景 | FluxCache | 纯 Redis 基线 | 提升 |
| --- | --- | --- | --- |
| L1 命中延迟（完整链路） | `0.385 µs` | `2.475 ms` | **~6,400x** |
| 90% L1 / 10% L2 混合读吞吐 | `31,494 ops/s` | `2,808 ops/s` | **11.2x** |
| 16 线程同一冷 key（单飞开 vs 关） | `9,180 ops/s` | `1,609 ops/s` | **5.7x** |
| 击穿 → DB 调用 | `0.042 次/op` | `1.43 次/op` | **~34x 减少** |

> 纯 Redis 基线以 2 ms 固定延迟模拟同城/局域网 RTT。完整报告见 [性能基准](/guide/benchmark)。

## 一条注解 = 多级缓存

```java
@FluxCacheable(cacheName = "studentLocalRedis", key = "#name",
    firstCacheable = @FirstCacheable(ttl = 1L),
    secondaryCacheable = @SecondaryCacheable(enabled = true, ttl = 3L))
public List<StudentVO> getUser(String name) {
    return mockSelectSql();
}
```

<footer class="vp-flex" style="margin-top:40px">
无服务器、免运维，纯注解使用。加入 [GitHub](https://github.com/weihubeats/fluxcache) 了解全部用法。
</footer>