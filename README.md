# fluxcache
多级缓存框架 (multilevel cache framework)

## 特性

- 自定义一级 / 二级缓存
- 支持 Caffeine 分布式删除、更新（基于 Redis Pub/Sub）
- 支持解决缓存雪崩（随机过期时间）
- 支持缓存穿透（缓存 null）
- 支持缓存击穿防护（单飞 single-flight：同一 key 并发未命中仅一个线程加载，其余线程复用结果）
- Dashboard 操作缓存元数据 / 清空 / 按 key 查询与清理
- Dashboard 缓存命中率等监控统计
- 纯注解使用
- **多 Redis 客户端**：默认 Spring Data Redis；Redisson 可选独立模块（含 `REDIS_MAP` / RMapCache）

## 页面

![cache-overview.png](docs/images/cache-overview.png)

![service-manage .png](docs/images/service-manage%20.png)

![cache-detail.png](docs/images/cache-detail.png)

## 模块

| 模块 | 说明 |
|------|------|
| `fluxcache-core` | 核心引擎与抽象（无 Redis 客户端依赖） |
| `fluxcache-redis-spring` | Spring Data Redis 实现（`REDIS`） |
| `fluxcache-redis-redisson` | Redisson 实现（`REDIS` + `REDIS_MAP`），**不依赖** spring-data-redis |
| `fluxcache-admin` | Dashboard REST |
| `fluxcache-all-spring-boot-starter` | 默认入口：`redis-spring` + `admin` |
| `fluxcache-example-starter` | starter 用法示例 |
| `fluxcache-example-redisson` | 仅 Redisson 用法示例 |

## 使用

目前仅支持 Spring Boot。可参考：

- [fluxcache-example-starter](fluxcache-example/fluxcache-example-starter)（默认）
- [fluxcache-example-redisson](fluxcache-example/fluxcache-example-redisson)

### 1. 引入依赖（推荐）

```xml
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-all-spring-boot-starter</artifactId>
    <version>0.0.3</version>
</dependency>
```

默认包含 Spring Data Redis 实现与 admin。应用需提供 `RedisConnectionFactory`（Boot 自动配置或自行声明）。

Spring Boot 3 请使用 `spring-boot-3.x` 分支上的 `3.0.0` 版本。

### 仅使用 Redisson（不引入 spring-data-redis）

```xml
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-redis-redisson</artifactId>
    <version>0.0.3</version>
</dependency>
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-admin</artifactId>
    <version>0.0.3</version>
</dependency>
```

并提供 `RedissonClient` Bean。引入哪个 Redis 模块就用哪套实现，互斥依赖即可。

### 2. 启动类添加 `@EnableFluxCaching`

### 3. Redis 连接

**Starter / Spring Data Redis：**

```java
@Bean
public RedisConnectionFactory redisConnectionFactory() {
    return new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 6379));
}
```

**Redisson：**

```java
@Bean
public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://127.0.0.1:6379");
    config.setCodec(new JsonJacksonCodec(new ObjectMapper().registerModule(new JavaTimeModule())));
    return Redisson.create(config);
}
```

### 4. 缓存类型

- `CAFFEINE`：本地缓存
- `REDIS`：可移植 Redis KV（Spring Data Redis 或 Redisson Bucket）
- `REDIS_MAP`：仅 Redisson `RMapCache`（按 entry TTL）

### 注解示例

一级 Caffeine：

```java
@FluxCacheable(cacheName = "firstCacheByCaffeine", key = "#name",
    firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.CAFFEINE, ttl = 5L, unit = TimeUnit.MINUTES, maxSize = 2000, initSize = 20))
public List<StudentVO> firstCacheByCaffeine(String name) {
    return mockSelectSql();
}
```

一级 Redis：

```java
@FluxCacheable(cacheName = "studentRedis", key = "#name", fluxCacheLevel = FluxCacheLevel.FirstCacheable,
    firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS, ttl = 5L))
public List<StudentVO> firstCacheByRedis(String name) {
    return mockSelectSql();
}
```

二级：Caffeine + Redis：

```java
@FluxCacheable(cacheName = "studentLocalRedis", key = "#name", fluxCacheLevel = FluxCacheLevel.SecondaryCacheable,
    firstCacheable = @FirstCacheable(ttl = 1L, fluxCacheType = FluxCacheType.CAFFEINE, maxSize = 2000, initSize = 20),
    secondaryCacheable = @SecondaryCacheable(ttl = 3L, fluxCacheType = FluxCacheType.REDIS))
public List<StudentVO> secondaryCacheByCaffeineRedis(String name) {
    return mockSelectSql();
}
```

### 手动注册缓存

实现 `FluxCacheDataRegistered`，在 `registerCache` 中返回 `FluxMultilevelCacheCacheable` 列表（示例见 example 模块）。

## 缓存刷新

使用 `@FluxRefresh`（需 Redis 模块提供 `FluxDistributedLock`）：

```java
@FluxCacheable(
    cacheName = "studentCache",
    key = "#name",
    refresh = @FluxRefresh(
        enabled = true,
        provider = StudentMultipleKeysProvider.class,
        fixedRate = 1,
        initialDelay = 0,
        unit = TimeUnit.MINUTES,
        preheatOnStartup = true
    )
)
```

## Dashboard

引入 starter（含 admin）后访问管理端能力；前端见 `fluxcache-dashboard`
