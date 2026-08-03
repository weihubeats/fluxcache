# fluxcache
多级缓存框架 (multilevel cache framework)

A lightweight multilevel cache framework for Spring Boot applications. Supports Caffeine L1 and Redis L2 cache with cache penetration/breakdown/avalanche protection, distributed cache invalidation via Pub/Sub, single-flight cache loading, and a built-in cache management dashboard. Annotate-based usage, compatible with Spring Data Redis and Redisson.

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

```
fluxcache
├── fluxcache-core                          核心引擎与抽象（无 Redis 客户端依赖）
├── flux-cache-spring-boot-starter/         Spring Boot Starter 父模块
│   ├── fluxcache-redis-spring              Spring Data Redis 实现（REDIS）
│   ├── fluxcache-redis-redisson            Redisson 实现（REDIS + REDIS_MAP），不依赖 spring-data-redis
│   ├── fluxcache-admin                     Dashboard REST
│   └── fluxcache-all-spring-boot-starter   默认入口：redis-spring + admin
└── fluxcache-example/                      示例项目
```

## 使用

目前仅支持 Spring Boot。可参考：

- [fluxcache-example-starter](fluxcache-example/fluxcache-example-starter)（默认）
- [fluxcache-example-redisson](fluxcache-example/fluxcache-example-redisson)

### 1. 引入依赖（推荐）

```xml
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-all-spring-boot-starter</artifactId>
    <version>0.0.4</version>
</dependency>
```

默认包含 Spring Data Redis 实现与 admin。应用需提供 `RedisConnectionFactory`（Boot 自动配置或自行声明）。

Spring Boot 3 请使用 `spring-boot-3.x` 分支上的 `3.0.0` 版本。

### 仅使用 Redisson（不引入 spring-data-redis）

```xml
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-redis-redisson</artifactId>
    <version>0.0.4</version>
</dependency>
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-admin</artifactId>
    <version>0.0.4</version>
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

### 5. 全局配置（推荐）

先在 `application.yml` 中配置一级/二级缓存默认值，大幅减少每个注解上的重复配置：

```yaml
flux:
  cache:
    first-cache:
      ttl: 5
      time-unit: MINUTES
      cache-type: CAFFEINE
      init-size: 20
      max-size: 2000
    secondary-cache:
      ttl: 5
      time-unit: MINUTES
      cache-type: REDIS
    default-cache-level: FirstCacheable
```

注解上未显式设置的字段（`ttl <= 0`、`initSize/maxSize <= -1`、`fluxCacheType = NULL`）会自动回落到全局配置。

### 6. 注解示例

**一级缓存（完全使用全局 Caffeine 默认值）：**

```java
@FluxCacheable(cacheName = "firstCacheByCaffeine", key = "#name")
public List<StudentVO> firstCacheByCaffeine(String name) {
    return mockSelectSql();
}
```

**一级 Redis（仅覆盖缓存类型与 TTL）：**

```java
@FluxCacheable(cacheName = "studentRedis", key = "#name",
    firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS, ttl = 5L))
public List<StudentVO> firstCacheByRedis(String name) {
    return mockSelectSql();
}
```

**二级缓存 Caffeine + Redis（仅覆盖 TTL，其余走全局）：**

```java
@FluxCacheable(cacheName = "studentLocalRedis", key = "#name",
    firstCacheable = @FirstCacheable(ttl = 1L),
    secondaryCacheable = @SecondaryCacheable(enabled = true, ttl = 3L))
public List<StudentVO> secondaryCacheByCaffeineRedis(String name) {
    return mockSelectSql();
}
```

> 二级缓存通过 `secondaryCacheable.enabled = true` 自动推断，无需显式声明 `fluxCacheLevel`。

### 7. 手动注册缓存

实现 `FluxCacheDataRegistered`，在 `registerCache` 中返回 `FluxMultilevelCacheCacheable` 列表（示例见 example 模块）。

### 8. 缓存刷新

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
