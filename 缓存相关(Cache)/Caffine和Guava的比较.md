### 一、核心改进：缓存淘汰算法（命中率提升 15%-25%）

这是 Caffeine 最核心的改进，直接解决了 Guava 缓存命中率低的问题。

| 特性     | Guava Cache                                                  | Caffeine                                                     |
| -------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 淘汰算法 | **LRU（最近最少使用）**：仅基于 “访问时间” 淘汰，无法识别 “高频但近期少访问” 的热点数据，命中率低；（Guava 11+ 引入 LRU 的变种 LIRS，但优化有限） | **W-TinyLFU（结合 LFU + LRU）**：1. 用「频率草图（Frequency Sketch）」统计访问频率（LFU 核心），识别长期热点；2. 用「分段时钟（Segmented LRU）」处理近期访问（LRU 核心），避免 “缓存污染”；3. 对低频但突发访问的 “新晋热点” 友好，命中率远超 LRU； |
| 实际效果 | 面对突发流量 / 非均匀访问时，命中率低，缓存失效频繁          | 工业界公认 “接近理论最优” 的命中率，实测比 Guava 高 15%-25%（官方压测数据） |

#### 算法对比示例：

- 场景

  ：缓存中有 3 个数据 A（高频访问但最近 10s 未访问）、B（低频但最近访问）、C（极低频），缓存满时需淘汰一个。

  - Guava（LRU）：淘汰 A（最近最少访问），但 A 是长期热点，后续会频繁缓存穿透；
  - Caffeine（W-TinyLFU）：淘汰 C（频率最低），保留 A（高频）和 B（近期访问），命中率更高。

### 二、并发性能优化（吞吐量提升数倍）

Guava Cache 的并发控制设计偏保守，高并发下性能瓶颈明显，Caffeine 针对性重构了并发模型：

#### 1. 锁机制优化

- **Guava**：采用「分段锁（Segment）」（类似 JDK 1.7 ConcurrentHashMap），默认 4 个分段，高并发下分段竞争仍会导致锁等待；

- Caffeine：

  - 核心操作（读 / 写）基于「Stripe 锁 + 无锁 CAS」：读操作完全无锁，写操作仅锁定当前缓存项的哈希分段（分段数动态适配 CPU 核心数）；
  - 批量操作（如清理过期数据）采用「线程本地队列 + 延迟处理」，避免全局锁阻塞。

#### 2. 异步能力（Guava 无原生支持）

- **Guava**：所有缓存加载 / 刷新操作都是同步的，高并发下会阻塞线程（如 `CacheLoader.load()` 耗时会导致线程挂起）；

- Caffeine：

  - 原生支持异步 API（`AsyncLoadingCache`），基于 `CompletableFuture` 实现非阻塞加载；
  - 可结合线程池（如 ForkJoinPool）异步刷新缓存，避免同步加载的线程阻塞，适配高并发场景。

#### 性能对比（官方压测，8 核 CPU）：

| 操作类型         | Guava QPS | Caffeine QPS | 提升倍数 |
| ---------------- | --------- | ------------ | -------- |
| 读（无锁竞争）   | ~100 万   | ~700 万      | 7 倍     |
| 写（高并发）     | ~50 万    | ~300 万      | 6 倍     |
| 缓存加载（异步） | -         | ~200 万      | 无对比   |

### 三、内存管理与过期策略优化

#### 1. 过期时间精度与触发机制

- Guava：

  - 过期时间基于「固定周期扫描」（默认 1 秒），过期数据清理不及时，可能返回已过期数据；
  - 弱引用 / 软引用的内存回收依赖 JVM GC，触发时机不可控；

- Caffeine：

  - 采用「惰性清理 + 定时清理 + 批量清理」结合：
    - 惰性清理：读 / 写操作时检查并清理当前缓存项的过期数据；
    - 定时清理：基于时间轮（TimeWheel）精准触发，精度达毫秒级；
    - 批量清理：低并发时异步批量清理过期数据，不影响核心操作；
  - 内存回收（弱 / 软引用）与 JVM GC 联动更高效，支持自定义内存阈值触发清理。

#### 2. 内存占用优化

- **Guava**：缓存项的元数据（如访问时间、过期时间）存储冗余，内存开销较高；

- Caffeine：

  - 元数据采用「压缩存储」（如用整型替代对象存储时间戳）；
  - 哈希表采用「开放寻址法 + 线性探测」（Guava 是链表法），减少链表节点的内存开销；
  - 支持「缓存项预分配」，避免频繁扩容的内存碎片。

### 四、功能增强（弥补 Guava 的功能缺失）

| 缺失功能         | Guava Cache                          | Caffeine                                                     |
| ---------------- | ------------------------------------ | ------------------------------------------------------------ |
| 异步缓存         | 无原生支持，需手动封装线程池         | 原生 `AsyncLoadingCache`，基于 CompletableFuture 异步加载 / 刷新 |
| 缓存加载回调     | 仅支持同步 `CacheLoader`，无失败回调 | 支持 `AsyncCacheLoader`，可自定义加载成功 / 失败回调         |
| 统计维度         | 仅基础统计（命中率、加载时间）       | 精细化统计（如各分段锁竞争次数、过期数据清理耗时、异步加载成功率） |
| 自定义淘汰监听器 | 仅支持简单的 `RemovalListener`       | 支持异步淘汰监听器，避免监听器耗时阻塞缓存操作               |
| 缓存预热         | 无原生 API，需手动 put 数据          | 支持 `putAll()` 批量预热，且支持异步预热                     |
| 最大大小计算方式 | 仅支持 “缓存项数量” 或 “权重”        | 支持基于 “内存占用字节数” 设置最大大小（需自定义 `Weigher`） |

### 五、API 兼容与迁移成本

Caffeine 完全兼容 Guava Cache 的核心 API，迁移成本极低：

```java
// Guava Cache 代码
LoadingCache<String, User> guavaCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build(new CacheLoader<String, User>() {
            @Override
            public User load(String key) throws Exception {
                return loadUserFromDB(key);
            }
        });

// Caffeine 等价代码（仅替换 CacheBuilder 为 Caffeine）
LoadingCache<String, User> caffeineCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build(new CacheLoader<String, User>() {
            @Override
            public User load(String key) throws Exception {
                return loadUserFromDB(key);
            }
        });

// Caffeine 新增异步 API（Guava 无）
AsyncLoadingCache<String, User> asyncCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .buildAsync((key, executor) -> CompletableFuture.supplyAsync(() -> loadUserFromDB(key), executor));
```

### 六、总结：Caffeine 解决的核心痛点

| Guava Cache 不足   | Caffeine 改进方案                          | 业务收益                       |
| ------------------ | ------------------------------------------ | ------------------------------ |
| LRU 算法命中率低   | W-TinyLFU 算法，接近理论最优命中率         | 减少缓存穿透，降低后端服务压力 |
| 高并发下锁竞争严重 | 无锁读 + 细粒度锁写 + CAS 并发控制         | 吞吐量提升数倍，支持更高并发   |
| 无异步加载能力     | 原生 AsyncLoadingCache + CompletableFuture | 避免线程阻塞，提升响应速度     |
| 过期清理不及时     | 惰性 + 定时 + 批量清理，毫秒级精度         | 保证缓存数据时效性             |
| 内存开销高         | 压缩元数据 + 开放寻址法哈希表              | 降低内存占用，减少 GC 压力     |

### 适用场景建议

- 若项目已用 Guava Cache 且并发 / 命中率要求不高：可继续使用，无需迁移；
- 若面临高并发、高缓存命中率要求（如电商秒杀、热点数据缓存）：优先替换为 Caffeine；
- 若需异步加载缓存、精细化统计：Caffeine 是唯一选择。

Caffeine 目前已成为 Spring Cache、Hibernate、Elasticsearch 等主流框架的默认缓存实现，也是工业界高性能缓存的事实标准。
