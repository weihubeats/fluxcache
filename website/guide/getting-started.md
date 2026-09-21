# 快速开始

支持 Spring Boot 2 与 3。版本对照：

| Spring Boot | JDK | FluxCache 版本 | 分支 |
| --- | --- | --- | --- |
| Boot 2.7.x | 8 / 11 | `0.0.x`（最新 `0.0.4`） | `main` |
| Boot 3.4.x | 17+ | `3.x`（最新 `3.0.1`） | `spring-boot-3.x`（默认分支） |

> Boot 2 用户请切换到 `main` 分支使用 `0.0.x` 版本；Boot 3 用户直接使用当前默认分支的 `3.x` 版本。注意 `0.0.4` 与 `3.x` 是两条版本线，不可混用。

参考示例项目：

- [fluxcache-example-starter](https://github.com/weihubeats/fluxcache/tree/spring-boot-3.x/fluxcache-example/fluxcache-example-starter)（默认）
- [fluxcache-example-redisson](https://github.com/weihubeats/fluxcache/tree/spring-boot-3.x/fluxcache-example/fluxcache-example-redisson)

## 1. 引入依赖（推荐）

```xml
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-all-spring-boot-starter</artifactId>
    <version>3.0.1</version>
</dependency>
```

默认包含 Spring Data Redis 实现与 admin。应用需提供 `RedisConnectionFactory`（Boot 自动配置或自行声明）。

### 仅使用 Redisson（不引入 spring-data-redis）

```xml
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-redis-redisson</artifactId>
    <version>3.0.1</version>
</dependency>
<dependency>
    <groupId>io.github.weihubeats</groupId>
    <artifactId>fluxcache-admin</artifactId>
    <version>3.0.1</version>
</dependency>
```

并提供 `RedissonClient` Bean。引入哪个 Redis 模块就用哪套实现，互斥依赖即可。

## 2. 启动类添加 `@EnableFluxCaching`

## 3. Redis 连接

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

## 4. 缓存类型

- `CAFFEINE`：本地缓存
- `REDIS`：可移植 Redis KV（Spring Data Redis 或 Redisson Bucket）
- `REDIS_MAP`：仅 Redisson `RMapCache`（按 entry TTL）

## 5. 注解示例

```java
@FluxCacheable(cacheName = "firstCacheByCaffeine", key = "#name")
public List<StudentVO> firstCacheByCaffeine(String name) {
    return mockSelectSql();
}
```

二级缓存 Caffeine + Redis：

```java
@FluxCacheable(cacheName = "studentLocalRedis", key = "#name",
    firstCacheable = @FirstCacheable(ttl = 1L),
    secondaryCacheable = @SecondaryCacheable(enabled = true, ttl = 3L))
public List<StudentVO> secondaryCacheByCaffeineRedis(String name) {
    return mockSelectSql();
}
```

> 二级缓存通过 `secondaryCacheable.enabled = true` 自动推断，无需显式声明 `fluxCacheLevel`。

## 6. 手动注册缓存

实现 `FluxCacheDataRegistered`，在 `registerCache` 中返回 `FluxMultilevelCacheCacheable` 列表（示例见 example 模块）。

## 7. 缓存刷新

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

## 下一步

- [全局配置](/guide/configuration) — 在 `application.yml` 中统一配置一级/二级缓存默认值
- [可观测性](/guide/observability) — Micrometer + Prometheus + Grafana 对接
- [性能基准](/guide/benchmark) — 完整 JMH 压测数据与复现方法
