# FluxCache Dashboard

独立部署的**多服务**缓存管理与监控控制台，对接各业务实例上的 [`FluxCacheController`](../fluxcache-admin/src/main/java/com/fluxcache/admin/controller/FluxCacheController.java)。

可同时管理 `pay-service`、`order-service` 等多个服务的缓存元数据、统计与 Key 运维。

## 技术栈

- Vue 3 + Vite + TypeScript
- Ant Design Vue 4
- ECharts（`vue-echarts`）
- Pinia + Vue Router + Axios

## 功能

- **服务管理**：为每个业务实例配置名称、Base URL、API Prefix；探测连通性；启用/禁用
- **多服务总览**：并行拉取所有启用服务，聚合展示；可按服务筛选
- 单缓存监控：KPI + 命中率 / 请求量 / 最大加载耗时
- Key 查询、按 key 清理、整库清空（二次确认）

## 快速开始

### 1. 启动 example 后端

```bash
cd fluxcache-example
mvn spring-boot:run
```

默认端口：`8090`，API 前缀：`/cache/manager/v1`。

各业务服务需开启 CORS（example 已配置）：

```yaml
flux:
  cache:
    admin:
      cors:
        enabled: true
        allowed-origin-patterns:
          - "http://localhost:*"
          - "http://127.0.0.1:*"
```

### 2. 启动 Dashboard

```bash
cd fluxcache-dashboard
npm install
npm run dev
```

打开 `http://127.0.0.1:5173` → **服务管理**，添加服务，例如：

| 服务名 | Base URL | Prefix |
|--------|----------|--------|
| local-example | （留空走 Vite 代理） | `/cache/manager/v1` |
| pay-service | `http://pay-host:8080` | `/cache/manager/v1` |
| order-service | `http://order-host:8080` | `/cache/manager/v1` |

服务列表保存在浏览器 `localStorage`。

### 3. 生产构建

```bash
npm run build
```

产物在 `dist/`。多服务场景下一般在页面「服务管理」配置各实例地址；也可通过 `VITE_API_BASE` 预置默认第一条服务的 Base URL。

## API 依赖（每个服务各自暴露）

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `{prefix}/all/caches` | 缓存列表 |
| GET | `{prefix}/getAllStatics?cacheName=` | 单缓存窗口统计 |
| GET | `{prefix}/statics/summary` | 总览摘要 |
| GET | `{prefix}/getValue?cacheName=&key=` | 查值 |
| POST | `{prefix}/evict?cacheName=&keys=` | 按 key 清理 |
| POST | `{prefix}/clear?cacheName=` | 整库清空 |

默认 `{prefix}` = `/cache/manager/v1`。

## 脚本

| 命令 | 说明 |
|------|------|
| `npm run dev` | 本地开发（含代理） |
| `npm run build` | 类型检查 + 生产构建 |
| `npm run preview` | 预览构建产物 |
