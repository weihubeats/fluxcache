# 全局配置

先配置一级/二级缓存默认值，大幅减少每个注解上的重复配置：

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

## 一键仅覆盖缓存类型与 TTL

**一级 Redis（仅覆盖缓存类型与 TTL）：**

```java
@FluxCacheable(cacheName = "studentRedis", key = "#name",
    firstCacheable = @FirstCacheable(fluxCacheType = FluxCacheType.REDIS, ttl = 5L))
public List<StudentVO> firstCacheByRedis(String name) {
    return mockSelectSql();
}
```

**一级缓存（完全使用全局 Caffeine 默认值）：**

```java
@Cacheable(cacheName = "firstCacheByCaffeine", key = "#name")
public List<StudentVO> firstCacheByCaffeine(String name) {
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