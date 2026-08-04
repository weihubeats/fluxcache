# FluxCache 注解简化优化与 Podman 兼容 Docker 集成测试实践

## 一、背景

FluxCache 是一个面向 Spring Boot 的多级缓存框架，支持 Caffeine L1、Redis L2、缓存穿透、缓存击穿、缓存刷新、Dashboard 监控等能力。

在早期使用中，`@FluxCacheable` 的写法成本较高，典型写法如下：

```java
@FluxCacheable(cacheName = "firstCacheByCaffeine", key = "#name",
    firstCacheable = @FirstCacheable(
        fluxCacheType = FluxCacheType.CAFFEINE,
        ttl = 5L,
        unit = TimeUnit.MINUTES,
        maxSize = 2000,
        initSize = 20
    ))
public List<StudentVO> firstCacheByCaffeine(String name) {
    return mockSelectSql();
}
```

主要问题有两类：

1. `fluxCacheLevel` 与 `firstCacheable` / `secondaryCacheable` 语义重复，一级/二级缓存要重复声明。
2. `flux.cache.first-cache` / `flux.cache.secondary-cache` 全局 YAML 配置已经存在，但注解解析器从未读取，导致这些配置成为“死配置”。因此每方法都必须重复写 ttl、cacheType、maxSize、initSize 等参数。

## 二、最终优化目标

优化后希望达到：

```java
@FluxCacheable(cacheName = "firstCacheByCaffeine", key = "#name")
public List<StudentVO> firstCacheByCaffeine(String name) {
    return mockSelectSql();
}
```

缓存行为由全局配置接管：

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

二级缓存也通过 `secondaryCacheable.enabled = true` 自动推断，不再需要手写 `fluxCacheLevel`：

```java
@FluxCacheable(cacheName = "studentLocalRedis", key = "#name",
    firstCacheable = @FirstCacheable(ttl = 1L),
    secondaryCacheable = @SecondaryCacheable(enabled = true, ttl = 3L))
public List<StudentVO> secondaryCacheByCaffeineRedis(String name) {
    return mockSelectSql();
}
```

## 三、核心改动

### 1. 删除 `FluxCacheable.fluxCacheLevel`

之前注解上需要显式声明一级或二级缓存：

```java
fluxCacheLevel = FluxCacheLevel.SecondaryCacheable
```

但二级缓存本身已经通过 `secondaryCacheable` 表达。因此删除该字段，避免语义重复。

现在解析逻辑变为：

- 如果 `secondaryCacheable.enabled = true`，自动推断为二级缓存；
- 否则使用 `FluxCacheProperties.defaultCacheLevel`；
- 没有显式配置时，回落到框架内置默认值。

关键解析器代码：

```java
private FluxCacheLevel resolveCacheLevel(FluxCacheable ca) {
    if (ca.secondaryCacheable().enabled()) {
        return FluxCacheLevel.SecondaryCacheable;
    }
    return cacheProperties.fluxCacheLevel(FluxCacheLevel.NULL);
}
```

### 2. 给 `SecondaryCacheable` 增加 `enabled`

```java
boolean enabled() default false;
```

含义很直观：

- `enabled = false`：不开启二级缓存；
- `enabled = true`：启用二级缓存，并自动推断为二级缓存。

这样可以把原来的：

```java
@FluxCacheable(
    cacheName = "studentLocalRedis",
    key = "#name",
    fluxCacheLevel = FluxCacheLevel.SecondaryCacheable,
    secondaryCacheable = @SecondaryCacheable(ttl = 3L)
)
```

简化为：

```java
@FluxCacheable(
    cacheName = "studentLocalRedis",
    key = "#name",
    secondaryCacheable = @SecondaryCacheable(enabled = true, ttl = 3L)
)
```

### 3. 让注解默认值支持“未设置”语义

原来的注解默认值是硬编码的业务默认值，比如：

- `ttl = 30L`
- `maxSize = 10000`
- `initSize = 16`
- `fluxCacheType = CAFFEINE / REDIS`

这会导致注解总是“覆盖”全局配置，因此 `flux.cache.first-cache` 无法真正生效。

现在改为使用 sentinel 值表示“未设置”：

```java
long ttl() default 0L;
int initSize() default -1;
int maxSize() default -1;
FluxCacheType fluxCacheType() default FluxCacheType.NULL;
```

含义：

- `ttl <= 0`：未显式设置 TTL，回落到全局配置；
- `initSize/maxSize < 0`：未显式设置容量，回落到全局配置；
- `fluxCacheType = NULL`：未显式设置缓存类型，回落到全局配置或框架内置默认值。

### 4. 让 `FluxCacheProperties.CacheConfig` 真正参与配置

`FluxCacheProperties` 中新增/明确一级、二级缓存全局配置字段：

```java
@Data
public abstract static class CacheConfig {

    private long ttl = 30L;
    private TimeUnit timeUnit = TimeUnit.MINUTES;
    private FluxCacheType cacheType;
    private int initSize = -1;
    private int maxSize = -1;
}
```

这样可以通过 YAML 统一管理默认值：

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
```

### 5. `FluxCacheConfig` 增加合并逻辑

核心是将解析过程从“只读注解”变成“注解覆盖 + 全局默认 + 内置兜底”：

```java
public static FluxCacheConfig from(FirstCacheable cacheable, FirstCacheConfig global) {
    return merge(
        cacheable.ttl(),
        cacheable.unit(),
        cacheable.initSize(),
        cacheable.maxSize(),
        cacheable.fluxCacheType(),
        FluxCacheType.CAFFEINE,
        global
    );
}
```

合并规则如下：

```text
注解值已设置
  -> 使用注解值

注解值未设置，且全局配置已设置
  -> 使用全局配置

注解和全局都未设置
  -> 使用内置默认值
```

内置默认值包括：

- 一级缓存类型：`CAFFEINE`
- 二级缓存类型：`REDIS`
- TTL：30 分钟
- initSize：16
- maxSize：10000

## 四、解析器改造

`FluxSpringCacheAnnotationParser` 现在的解析方式变为：

```java
FluxCacheConfig firstCacheConfig = FluxCacheConfig.from(
    ca.firstCacheable(),
    cacheProperties.getFirstCache()
);

FluxCacheLevel cacheLevel = resolveCacheLevel(ca);

FluxCacheConfig secondaryCacheConfig = null;
if (FluxCacheLevel.isSecondaryCacheable(cacheLevel)) {
    secondaryCacheConfig = FluxCacheConfig.from(
        ca.secondaryCacheable(),
        cacheProperties.getSecondaryCache()
    );
}
```

好处很明显：

- 全局配置不再是死配置；
- 注解只保留差异配置；
- 大多数普通一级缓存可以只写 `cacheName` 和 `key`。

## 五、兼容策略

本次优化是向前兼容设计。

### 1. 显式注解配置优先级最高

如果业务方法需要特殊配置，仍然可以在注解中显式声明：

```java
@FluxCacheable(cacheName = "orderRedis", key = "#name",
    firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS, ttl = 5L))
public List<OrderVO> firstCacheByRedis(String name) {
    return mockSelectSql();
}
```

此时方法级配置会覆盖全局默认值。

### 2. 二级缓存语义更直观

原来需要同时理解：

```java
fluxCacheLevel + secondaryCacheable
```

现在只需要理解：

```java
secondaryCacheable.enabled
```

如果希望二级缓存，显式打开即可。

### 3. 旧代码仍能逐步迁移

如果旧代码还在使用 `fluxCacheLevel`，可以先保留语义兼容，再逐步删除。本次版本中选择了直接删除该字段，因此新代码不需要再维护它，减少概念复杂度。

### 4. 手动注册缓存不受影响

手动注册走 `FluxCacheConfig.Builder`，仍然可以完整控制：

```java
FluxCacheConfig build = new FluxCacheConfig.Builder()
    .setCacheType(FluxCacheType.CAFFEINE)
    .setTtl(10L)
    .setInitSize(10)
    .setMaxSize(100)
    .setUnit(TimeUnit.SECONDS)
    .build();
```

## 六、测试覆盖

核心解析逻辑新增单测，覆盖：

1. `secondaryCacheable.enabled = true` 推断二级缓存；
2. 未启用二级缓存时回落到全局默认级别；
3. 注解未设置字段时回落到全局配置；
4. 注解值覆盖全局配置；
5. 注解和全局均未设置时使用内置默认值；
6. `key` 和 `allowCacheNull` 等字段正确传递。

测试通过结果：

```text
FluxSpringCacheAnnotationParserTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

核心模块测试通过结果：

```text
fluxcache-core
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
```

## 七、Podman 兼容 Docker 跑集成测试

FluxCache 的 example 测试使用 `@Testcontainers` 启动 Redis 容器。如果本机没有 Docker，而是使用 Podman，需要让 Maven/testcontainers 能找到 `docker` 命令。

### 1. 确认 Podman 已安装

```bash
podman --version
```

示例输出：

```text
podman version 5.8.3
```

### 2. 在用户目录创建 docker 兼容命令

创建一个用户级 bin 目录：

```bash
mkdir -p ~/.bin
```

将 Podman 软链接为 docker：

```bash
ln -sf /opt/podman/bin/podman ~/.bin/docker
```

验证：

```bash
~/.bin/docker --version
```

输出示例：

```text
docker version 5.8.3
```

### 3. 在 Maven 命令前注入 PATH

```bash
PATH=~/.bin:/opt/podman/bin:$PATH mvn test -pl fluxcache-core,fluxcache-example/fluxcache-example-starter -am
```

这会让当前 Maven 进程优先使用 `~/.bin/docker`，底层实际执行的是 Podman。

### 4. 为什么这样做可行

`@Testcontainers` 默认通过系统 `docker` 命令管理容器生命周期。只要环境变量 `PATH` 下存在 `docker`，并且该命令满足 Docker CLI 的基本接口，testcontainers 即可正常工作。

Podman 提供了 Docker CLI 兼容模式，因此通过 `~/.bin/docker -> podman` 即可让 Java 集成测试继续使用 Podman 启动容器。

### 5. 本次遇到的问题与修复

升级 surefire 插件到 JUnit 5 支持后，发现一个既有问题：

```text
Event parameter is mandatory for event listener method: public void FluxCacheWarmUpRunner.onApplicationReady()
```

原因是 `@EventListener` 修饰的方法没有事件参数。

修复方式：

```java
@EventListener
public void onApplicationReady(ApplicationReadyEvent event) {
    ...
}
```

这不是注解简化改动引入的问题，而是 surefire 2.18.1 之前无法执行 JUnit 5 测试，因此没有暴露。

## 八、示例效果对比

### 优化前

```java
@FluxCacheable(cacheName = "firstCacheByCaffeine", key = "#name",
    firstCacheable = @FirstCacheable(
        fluxCacheType = FluxCacheType.CAFFEINE,
        ttl = 5L,
        unit = TimeUnit.MINUTES,
        maxSize = 2000,
        initSize = 20
    ))
public List<StudentVO> firstCacheByCaffeine(String name) {
    return mockSelectSql();
}
```

### 优化后

```java
@FluxCacheable(cacheName = "firstCacheByCaffeine", key = "#name")
public List<StudentVO> firstCacheByCaffeine(String name) {
    return mockSelectSql();
}
```

### 二级缓存优化后

```java
@FluxCacheable(cacheName = "orderLocalRedis", key = "#name",
    firstCacheable = @FirstCacheable(ttl = 1L),
    secondaryCacheable = @SecondaryCacheable(enabled = true, ttl = 3L))
public List<OrderVO> secondaryCacheByCaffeineRedis(String name) {
    return mockSelectSql();
}
```

## 九、总结

本次优化让 FluxCache 从“注解上堆配置”变成“全局默认优先，注解只配置差异”。

关键收益：

- 普通缓存方法可极简，只需要 `cacheName` + `key`；
- `flux.cache.first-cache` / `flux.cache.secondary-cache` 真正生效；
- 二级缓存通过 `enabled` 推断，不再需要额外 `fluxCacheLevel`；
- 注解、全局 YAML、内置默认值三层联动，配置优先级清晰；
- 使用 Podman 兼容 Docker 后，可在本地运行 Redis 集成测试。

推荐新项目默认采用“全局 YAML 配置 + 最小注解”的方式，只在有特殊 TTL、缓存类型或容量要求时才在方法上覆盖。