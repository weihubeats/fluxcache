# 可观测性（Micrometer / Prometheus / Grafana）

企业级监控不依赖内置 Dashboard，支持通过 Micrometer 将缓存指标导出到 Prometheus + Grafana，与业务指标统一治理。

## 1. 引入依赖

`fluxcache-metrics` 已传递依赖 actuator 与 Prometheus registry，**只需 2 个依赖**：

```xml
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-all-spring-boot-starter</artifactId>
    <version>0.0.4</version>
</dependency>
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-metrics</artifactId>
    <version>0.0.4</version>
</dependency>
```

应用装配 `MeterRegistry` 后自动生效（`@ConditionalOnBean`），无需额外配置；未装配指标体系时对缓存链路零影响。

## 2. 指标清单

| 指标（Prometheus 名） | 类型 | 说明 | 标签 |
| --- | --- | --- | --- |
| `flux_cache_hit_total` | Counter | 命中累计 | `cache` |
| `flux_cache_miss_total` | Counter | 未命中累计 | `cache` |
| `flux_cache_eviction_total` | Counter | 驱逐累计 | `cache` |
| `flux_cache_load_time_seconds` | Summary/Histogram | L2/DB 加载耗时，含 p50/p95/p99 | `cache` |
| `flux_cache_hit_rate` | Gauge | 命中率 = hit/(hit+miss) | `cache` |
| `flux_cache_miss_rate` | Gauge | 未命中率 = miss/(hit+miss) | `cache` |

## 3. Prometheus 抓取

`/actuator/prometheus` 端点直接暴露：

```text
# TYPE flux_cache_hit_total counter
flux_cache_hit_total{cache="studentLocalRedis"} 12345
# TYPE flux_cache_load_time_seconds summary
flux_cache_load_time_seconds{quantile="0.95",cache="studentLocalRedis"} 0.00042
```

## 4. Grafana 面板 PromQL

```promql
# 命中率（各缓存）
sum(rate(flux_cache_hit_total[5m])) by (cache)
  / (sum(rate(flux_cache_hit_total[5m])) by (cache) + sum(rate(flux_cache_miss_total[5m])) by (cache))

# P99 加载耗时
histogram_quantile(0.99, sum(rate(flux_cache_load_time_seconds_bucket[5m])) by (le))

# 缓存读取 QPS
sum(rate(flux_cache_hit_total[5m]) + rate(flux_cache_miss_total[5m])) by (cache)
```

完整对接指南（Prometheus 抓取配置、Grafana 数据源/面板导入、告警规则）见仓库 docs：`docs/observability/prometheus-grafana.md`，含可直接导入的 `fluxcache-dashboard.json` 与告警规则 `alerts.yml`。