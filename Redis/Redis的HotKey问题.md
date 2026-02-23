在 Redis 使用中，hotkey（热点 key）是指被频繁访问的 key，可能导致单节点 Redis 服务器负载过高、网络带宽占用过大，甚至引发缓存雪崩等问题。处理 hotkey 需要从检测、缓解和根治三个层面入手，以下是常见的处理方案：

### 一、hotkey 的检测方法

1. **Redis 自带工具**
    - 使用`redis-cli --hotkeys`命令（Redis 4.0 + 支持），通过采样分析识别热点 key。
    - 监控命令：`INFO commandstats`统计命令执行频率，高频`GET`命令对应的 key 可能是热点。
2. **客户端埋点**在业务代码中对 key 的访问频率计数，定期上报到监控系统（如 Prometheus），通过阈值告警发现 hotkey。
3. **代理层拦截**若使用 Redis 代理（如 Twemproxy、Codis），可在代理层记录 key 的访问次数，汇总分析热点。

### 二、hotkey 的处理方案

根据 hotkey 的特性（如是否可拆分、是否只读），常用以下策略：

#### 1. **本地缓存 + 过期控制**

对热点 key 在应用本地缓存（如 Caffeine、Guava）一份，减少对 Redis 的直接访问。

- 优势：降低 Redis 压力，响应更快。
- 注意：需控制本地缓存的过期时间（短于 Redis 过期时间），避免数据不一致。


```java
// 示例：Java本地缓存+Redis的双层缓存
String getValue(String key) {
    // 1. 先查本地缓存
    String value = localCache.getIfPresent(key);
    if (value != null) {
        return value;
    }
    // 2. 本地缓存未命中，查Redis
    value = redisTemplate.opsForValue().get(key);
    if (value != null) {
        // 3. 写入本地缓存，设置较短过期时间（如10秒）
        localCache.put(key, value, Duration.ofSeconds(10));
    }
    return value;
}
```

#### 2. **key 分片（拆分热点 key）**

若热点 key 是单一 key（如`user:10086`），可拆分为多个子 key（如`user:10086:0`至`user:10086:9`），将访问分散到不同 Redis 节点。

- 步骤：
    1. 存储时将 value 写入所有子 key；
    2. 读取时随机访问一个子 key。
- 优势：分散单 key 的访问压力，适合只读或低频更新的场景。

#### 3. **读写分离 + 从节点分担压力**

利用 Redis 主从架构，将热点 key 的读请求转发到从节点，主节点仅处理写请求。

- 注意：需容忍一定的数据延迟（主从同步耗时），适合读多写少的场景。

#### 4. **热点数据预加载**

对已知的热点 key（如活动商品 ID），提前加载到 Redis 集群的多个节点，并设置合理的过期时间，避免冷启动时的集中访问。

#### 5. **使用 Redis Cluster 的 slot 迁移**

若热点 key 集中在某个 Redis 节点，可通过 Redis Cluster 的 slot 迁移功能，将热点 key 所在的 slot 迁移到负载较低的节点。

#### 6. **限流降级**

对超高频访问的 hotkey，在业务层通过限流（如 Guava RateLimiter）或降级（返回默认值）保护 Redis，避免雪崩。







```java
// 示例：对热点key进行限流
RateLimiter limiter = RateLimiter.create(1000); // 允许每秒1000次请求
String getHotValue(String key) {
    if (!limiter.tryAcquire()) {
        return "default_value"; // 限流时返回默认值
    }
    return redisTemplate.opsForValue().get(key);
}
```

### 三、总结

处理 hotkey 的核心思路是 **“分散压力、减少访问、提前准备”**：

- 短期缓解：本地缓存、限流、从节点分流；
- 长期根治：key 分片、集群负载均衡、热点预测与预加载。