# FluxCache 对接 Grafana 完整指南

> 适用版本：`fluxcache-metrics` ≥ 0.0.4　｜　Spring Boot 2.7 / 3.x　｜　Micrometer 1.9+

本指南讲述从「缓存指标」到「Grafana 可视化 / 告警」的完整链路，可直接复制落地。

## 一、全链路架构

```text
┌────────────┐   monitor事件   ┌───────────────┐   scrape    ┌────────────┐   query    ┌──────────┐
│ FluxCache  │ ──────────────▶ │  应用 App       │ ◀────────▶ │ Prometheus │ ◀────────▶ │ Grafana  │
│ cores/Mon   │                 │ /actuator/    │             │            │            │ (面板/告警)│
│ Micrometer  │                 │ prometheus     │             │            │            │          │
└────────────┘                 └───────────────┘             └────────────┘            └──────────┘
       flux_cache_hit_total / miss_total / eviction_total / load_time_seconds / hit_rate / miss_rate
```

- **FluxCache**：`fluxcache-metrics` 把监控事件桥接为 Micrometer 指标（无需自建 Dashboard）
- **应用**：`spring-boot-starter-actuator` + `micrometer-registry-prometheus` 暴露 `/actuator/prometheus`
- **Prometheus**：定时 scrape 该端点
- **Grafana**：以 Prometheus 为数据源，查询渲染面板，触发告警

## 二、接入应用

### 1. 引入依赖

`fluxcache-metrics` 已传递依赖 `spring-boot-starter-actuator` 与 `micrometer-registry-prometheus`，**只需 2 个依赖**：

```xml
<!-- FluxCache 核心 + 你的 Redis 模块（redis-spring / redisson） -->
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-all-spring-boot-starter</artifactId>
    <version>0.0.4</version>
</dependency>

<!-- 指标桥接（传递依赖已包含 actuator + prometheus registry） -->
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-metrics</artifactId>
    <version>0.0.4</version>
</dependency>
```

> **Spring Boot 3** 使用 `3.0.0` 版本；此时 micrometer 由 Boot 3 的 BOM 管理，`fluxcache-metrics` 与任一 Micrometer 1.x 兼容（只用稳定 API）。无需再手动声明 micrometer 版本。

### 2. 暴露端点

`application.yml`：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus   # 至少包含 prometheus
  endpoint:
    prometheus:
      enabled: true
```

生产环境若对外网开放，建议将 `management.server.port` 设为独立端口并仅内网可达：

```yaml
management:
  server:
    port: 8091
```

### 3. 验证指标

重启应用后：

```bash
curl -s http://<app>:<port>/actuator/prometheus | grep flux_cache
```

预期输出：

```
# HELP flux_cache_hit_total Total cache hits
# TYPE flux_cache_hit_total counter
flux_cache_hit_total{cache="studentLocalRedis",} 42.0
# TYPE flux_cache_load_time_seconds summary
flux_cache_load_time_seconds{quantile="0.95",cache="studentCache",} 0.00042
```

有了 `flux_cache_*` 说明接通，进入 Prometheus 采集。

## 三、Prometheus 抓取

`prometheus.yml` 增加抓取任务：

```yaml
scrape_configs:
  - job_name: fluxcache
    metrics_path: /actuator/prometheus          # 与上面的端点一致
    scrape_interval: 15s
    static_configs:
      - targets: [ '10.0.0.10:8091' ]            # 应用地址（+ management.server.port）
        labels:
          app: order-service                     # 建议加应用标签，多应用便于区分
```

### 验证抓取

```bash
# Prometheus UI → Status → Targets，fluxcache job 应为 UP

# 或命令行验证
curl -s http://<prometheus>:9090/api/v1/query --data-urlencode \
  'query=rate(flux_cache_hit_total[5m])'
```

## 四、Grafana 接入

### 1. 添加 Prometheus 数据源

**方式 A：UI 添加**

Grafana → Configuration → Data sources → Add data source → Prometheus，填 `http://<prometheus>:9090`，Save & test。

**方式 B：Provisioning（推荐，集群/自愈）**

`/etc/grafana/provisioning/datasources/prometheus.yml`：

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
```

### 2. 导入面板

仓库提供开箱面板 **[fluxcache-dashboard.json](grafana/fluxcache-dashboard.json)**。

**方式 A：UI 导入**

Grafana → Dashboards → New → Import → 粘贴 JSON → 选择 Prometheus 数据源 → Import。

**方式 B：Provisioning**

将 JSON 放到 `/etc/grafana/provisioning/dashboards/fluxcache/`，并声明：

```yaml
# /etc/grafana/provisioning/dashboards/dashboards.yml
apiVersion: 1
providers:
  - name: fluxcache
    orgId: 1
    folder: fluxcache
    type: file
    options:
      path: /etc/grafana/provisioning/dashboards/fluxcache
```

### 3. 面板说明

| 面板 | PromQL | 用途 |
| --- | --- | --- |
| 缓存命中率（趋势） | `sum(rate(flux_cache_hit_total[5m])) by (cache) / (sum(rate(flux_cache_hit_total[5m])) by (cache) + sum(rate(flux_cache_miss_total[5m])) by (cache))` | 按缓存追踪命中率趋势 |
| 命中率（瞬时） | `flux_cache_hit_rate{cache="$cache"}` | 当前累计命中率（Gauge） |
| 读取 QPS | `sum(rate(flux_cache_hit_total[5m]) + rate(flux_cache_miss_total[5m])) by (cache)` | 每缓存吞吐 |
| P99 加载耗时 | `histogram_quantile(0.99, sum(rate(flux_cache_load_time_seconds_bucket[5m])) by (le, cache))` | L2/DB 加载延迟 |
| 驱逐速率 | `rate(flux_cache_eviction_total[5m])` | 缓存抖动信号 |

> **命中率口径提醒**：`flux_cache_hit_rate` 是「进程启动以来累计」的 Gauge，适合 stat 面板；看实时趋势请用第一条 rate 公式（5m 窗口）。

## 五、告警

### 方式 A：Prometheus 规则（推荐）

`prometheus/alerts.yml`（K8s 挂 `/etc/prometheus/rules/`）：

```yaml
groups:
  - name: fluxcache.alerts
    rules:
      # 命中率跌破阈值
      - alert: FluxCacheHitRateLow
        expr: |
          sum(rate(flux_cache_hit_total[10m])) by (cache)
            / (sum(rate(flux_cache_hit_total[10m])) by (cache)
               + sum(rate(flux_cache_miss_total[10m])) by (cache)) < 0.8
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: '缓存 {{ $labels.cache }} 命中率 < 80%'
          description: '命中率降至 {{ humanize $value }}，可能出现缓存穿透/失效风暴'

      # P99 加载耗时升高
      - alert: FluxCacheLoadTimeP99High
        expr: |
          histogram_quantile(0.99,
            sum(rate(flux_cache_load_time_seconds_bucket[5m])) by (le, cache)) > 0.05
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: '缓存 {{ $labels.cache }} P99 加载耗时 > 50ms'

      # 驱逐异常
      - alert: FluxCacheEvictionSpike
        expr: rate(flux_cache_eviction_total[5m]) > 100
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: '缓存 {{ $labels.cache }} 驱逐速率异常'
```

```yaml
# prometheus.yml 引用
rule_files:
  - /etc/prometheus/rules/alerts.yml
```

### 方式 B：Grafana Alert（免改 Prometheus）

面板 Edit → Alert → 配置条件（`Reducer: last / Threshold: < 0.8`）→ 通知渠道（钉钉/Slack/Webhook）。

## 六、常见问题

| 问题 | 排查 |
| --- | --- |
| `/actuator/prometheus` 404 | `management.endpoints.web.exposure.include` 未含 `prometheus`；`prometheusEnabled` 未开启 |
| 无 `flux_cache_*` 指标 | 未引 `fluxcache-metrics`，或 `MeterRegistry` bean 缺失（引 `micrometer-registry-prometheus` 即自带） |
| 缓存闲置时无指标 | 指标按需注册：首次访问某缓存后该 cache tag 才出现（Grafana 可按需隐藏空序列） |
| 抓取多个实例 | target 加 labels（如 `app`），PromQL 按 `app`/`cache` 两个维度分组 |
| Boot 3 版本差异 | 配置键相同，指标命名不变；PromQL 无需改动 |

## 七、附：文件清单

| 文件 | 内容 |
| --- | --- |
| [grafana/fluxcache-dashboard.json](grafana/fluxcache-dashboard.json) | 可直接导入的 Grafana 面板 |
| [prometheus/prometheus.yml](prometheus/prometheus.yml) | 抓取配置示例 |
| [prometheus/alerts.yml](prometheus/alerts.yml) | 告警规则示例 |