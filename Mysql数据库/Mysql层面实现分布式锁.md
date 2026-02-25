# MySQL 分布式锁实现详解 + 替代方案对比

分布式锁的核心目标是：**在分布式系统中，保证同一时刻只有一个进程 / 线程操作共享资源**（如库存扣减、订单创建），避免并发冲突。MySQL 是实现分布式锁的常用方案之一（基于数据库的唯一性约束），但并非最优解，需结合场景选择。

## 一、MySQL 分布式锁的实现方式（3 种核心方案）

MySQL 实现分布式锁的核心原理是「**利用数据库的唯一性约束 / 事务特性，确保锁的互斥性**」，常用 3 种方案，各有优劣：

### 方案 1：基于唯一索引（悲观锁思路，推荐）

#### 核心逻辑：

创建一张锁表，通过「唯一索引 + INSERT 语句」实现锁竞争 —— 同一锁标识（如 `lock_key`）只能插入一条记录，插入成功即获取锁，失败则表示锁被占用。

#### 实现步骤：

1. **创建锁表**（需保证表的可用性，建议加索引和主键）：








```sql
CREATE TABLE `distributed_lock` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `lock_key` varchar(64) NOT NULL COMMENT '锁标识（如“stock-1001”）',
  `holder` varchar(64) NOT NULL COMMENT '锁持有者（如进程 ID、服务 IP）',
  `expire_time` datetime NOT NULL COMMENT '锁过期时间（避免死锁）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lock_key` (`lock_key`) COMMENT '唯一索引，保证锁互斥'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分布式锁表';
```

1. **获取锁**（插入记录，唯一索引冲突则失败）：


```java
// 锁标识（针对特定资源，如商品 ID=1001 的库存锁）
String lockKey = "stock-1001";
// 锁持有者（唯一标识当前进程，避免误释放他人的锁）
String holder = InetAddress.getLocalHost().getHostAddress() + "-" + Thread.currentThread().getId();
// 锁过期时间（如 30 秒，防止服务宕机导致死锁）
String expireTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis() + 30 * 1000));

try {
    // 插入成功 → 获取锁；失败（DuplicateKeyException）→ 锁被占用
    String sql = "INSERT INTO distributed_lock (lock_key, holder, expire_time) VALUES (?, ?, ?)";
    jdbcTemplate.update(sql, lockKey, holder, expireTime);
    return true; // 锁获取成功
} catch (DuplicateKeyException e) {
    return false; // 锁获取失败
}
```

1. **释放锁**（删除自己持有的锁记录）：









```java
String sql = "DELETE FROM distributed_lock WHERE lock_key = ? AND holder = ?";
int rows = jdbcTemplate.update(sql, lockKey, holder);
return rows > 0; // 仅释放自己的锁，避免误删
```

1. **锁超时自动释放**（避免死锁）：

- 若服务宕机未释放锁，锁会在 `expire_time` 后过期，其他进程可通过「删除过期锁 + 重新获取」实现锁抢占：



```java
// 抢占前先清理过期锁（防止死锁）
String cleanExpiredSql = "DELETE FROM distributed_lock WHERE expire_time < NOW()";
jdbcTemplate.update(cleanExpiredSql);
// 再尝试获取锁（同上插入逻辑）
```

#### 优点：

- 实现简单，无需复杂语法，兼容性好（支持所有 MySQL 版本）；
- 锁互斥性强（唯一索引保证）；
- 支持锁超时，避免死锁。

#### 缺点：

- 性能一般（依赖数据库 IO，高并发下数据库压力大）；
- 存在 “锁穿透” 风险（若插入和清理过期锁的间隙，可能重复获取锁，需加事务或原子操作）；
- 不支持可重入（同一进程再次获取同一锁会因唯一索引冲突失败）。

#### 适用场景：

- 低并发、对性能要求不高的分布式场景（如后台任务调度、低频资源操作）；
- 已有 MySQL 集群，无需额外引入中间件的场景。

### 方案 2：基于 `SELECT ... FOR UPDATE`（悲观锁，行级锁）

#### 核心逻辑：

利用 InnoDB 引擎的「行级锁」，通过 `SELECT ... FOR UPDATE` 语句锁定目标记录，其他进程执行相同语句时会阻塞，直到锁释放。

#### 实现步骤：

1. **锁表设计**（无需唯一索引，只需锁标识字段）：









```sql
CREATE TABLE `distributed_lock` (
  `lock_key` varchar(64) NOT NULL COMMENT '锁标识',
  `holder` varchar(64) NOT NULL COMMENT '锁持有者',
  `expire_time` datetime NOT NULL COMMENT '锁过期时间',
  PRIMARY KEY (`lock_key`) COMMENT '主键，确保行级锁生效'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

1. **获取锁**（开启事务，锁定行记录）：











```java
@Transactional
public boolean acquireLock(String lockKey, String holder) {
    // 1. 清理过期锁
    jdbcTemplate.update("DELETE FROM distributed_lock WHERE lock_key = ? AND expire_time < NOW()", lockKey);
    // 2. 查询并锁定记录（FOR UPDATE 会加行级锁，无记录则返回 null）
    DistributedLock lock = jdbcTemplate.queryForObject(
        "SELECT * FROM distributed_lock WHERE lock_key = ? FOR UPDATE",
        new Object[]{lockKey},
        new BeanPropertyRowMapper<>(DistributedLock.class)
    );
    // 3. 无记录则插入（获取锁），有记录则返回失败
    if (lock == null) {
        String expireTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis() + 30 * 1000));
        jdbcTemplate.update("INSERT INTO distributed_lock (lock_key, holder, expire_time) VALUES (?, ?, ?)", lockKey, holder, expireTime);
        return true;
    }
    return false;
}
```

1. **释放锁**（删除记录或更新过期时间）：








```java
@Transactional
public boolean releaseLock(String lockKey, String holder) {
    return jdbcTemplate.update("DELETE FROM distributed_lock WHERE lock_key = ? AND holder = ?", lockKey, holder) > 0;
}
```

#### 优点：

- 锁粒度细（行级锁），并发冲突少；
- 支持事务一致性（锁释放与事务提交 / 回滚绑定）。

#### 缺点：

- 阻塞式锁（未获取锁的进程会阻塞在 `SELECT ... FOR UPDATE`，可能导致线程池耗尽）；
- 性能差（高并发下大量进程阻塞，数据库连接压力大）；
- 需手动管理事务，容易因事务超时导致锁释放异常。

#### 适用场景：

- 并发量低、需要事务一致性的场景（如分布式事务中的资源锁定）；
- 不推荐高并发场景使用。

### 方案 3：基于乐观锁（`version` 字段，非互斥锁）

#### 核心逻辑：

不直接加锁，而是通过「版本号」控制资源更新 —— 每次更新前检查版本号是否匹配，匹配则更新（并自增版本号），不匹配则表示资源已被修改，需重试。

#### 实现步骤：

1. **资源表添加版本号字段**（无需单独锁表，锁逻辑与资源表绑定）：










```sql
CREATE TABLE `product_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL COMMENT '商品 ID',
  `stock` int NOT NULL COMMENT '库存',
  `version` int NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁核心）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

1. **更新资源时检查版本号**（实现锁的效果）：











```java
public boolean deductStock(Long productId, int num) {
    int retryCount = 3; // 重试次数
    while (retryCount-- > 0) {
        // 1. 查询当前库存和版本号
        ProductStock stock = jdbcTemplate.queryForObject(
            "SELECT * FROM product_stock WHERE product_id = ?",
            new Object[]{productId},
            new BeanPropertyRowMapper<>(ProductStock.class)
        );
        if (stock.getStock() < num) {
            return false; // 库存不足
        }
        // 2. 乐观锁更新：版本号匹配才更新（避免并发冲突）
        int rows = jdbcTemplate.update(
            "UPDATE product_stock SET stock = stock - ?, version = version + 1 WHERE product_id = ? AND version = ?",
            num, productId, stock.getVersion()
        );
        if (rows > 0) {
            return true; // 更新成功（获取锁并操作完成）
        }
        // 3. 版本号不匹配，重试
        try { Thread.sleep(100); } catch (InterruptedException e) {}
    }
    return false; // 重试次数耗尽，失败
}
```

#### 优点：

- 非阻塞式，性能高（无数据库锁竞争，仅通过版本号判断）；
- 无需单独维护锁表，锁逻辑与业务表绑定；
- 无死锁风险。

#### 缺点：

- 仅适用于 “更新资源” 场景，不适用于 “独占资源” 场景（如防止重复下单）；
- 存在 “ABA 问题”（版本号从 A→B→A，可能误判为未修改，需用 `CAS + 时间戳` 解决）；
- 重试机制可能导致部分请求失败（需业务层兼容）。

#### 适用场景：

- 高并发、短耗时的资源更新场景（如库存扣减、积分修改）；
- 可接受重试失败的场景。

## 二、MySQL 分布式锁的核心问题（为什么不是最优解）

无论哪种 MySQL 方案，都存在以下固有缺陷，限制了其在高并发场景的使用：

1. **性能瓶颈**：依赖数据库 IO，高并发下（如每秒上万请求），数据库会成为瓶颈（连接数耗尽、锁竞争激烈）；
2. **可用性风险**：MySQL 单点故障会导致整个分布式锁不可用（需部署主从，但主从切换可能导致锁数据不一致）；
3. **锁粒度粗**：基于表 / 行级锁，无法实现更细粒度的锁控制（如分布式读写锁）；
4. **功能有限**：不支持可重入、锁超时自动续期等高级特性（需手动实现，复杂度高）；
5. **死锁风险**：即使有超时机制，仍可能因网络延迟、事务超时导致锁释放异常。

## 三、更好的分布式锁替代方案（按推荐度排序）

### 方案 1：Redis 分布式锁（推荐，高并发首选）

#### 核心原理：

基于 Redis 的「SET NX EX」原子命令（`SET lock_key holder NX EX 30`），利用 Redis 的单线程特性保证锁互斥，支持超时自动释放。

#### 实现方式：

- 原生 Redis 命令（推荐用 Redisson 框架，已封装完善）：








```java
// Redisson 实现（支持可重入、锁续期、公平锁等）
Config config = new Config();
config.useSingleServer().setAddress("redis://127.0.0.1:6379");
RedissonClient redisson = Redisson.create(config);

// 获取锁（可重入锁，30 秒过期，自动续期）
RLock lock = redisson.getLock("stock-1001");
lock.lock(30, TimeUnit.SECONDS); // 加锁
try {
    // 操作共享资源（如扣减库存）
} finally {
    lock.unlock(); // 释放锁
}
```

#### 优点：

- 性能极高（Redis 内存操作，每秒支持 10 万 + 并发）；
- 可用性高（支持主从、哨兵、集群部署，无单点故障）；
- 功能丰富（支持可重入锁、读写锁、公平锁、锁续期等）；
- 实现简单（Redisson 框架已封装所有细节，开箱即用）。

#### 缺点：

- 需额外部署 Redis 集群（运维成本略高）；
- 存在 “脑裂” 风险（主从切换时，可能出现双锁，需用 RedLock 算法缓解，但实际场景中概率极低）。

#### 适用场景：

- 高并发、低延迟的分布式场景（如电商库存扣减、秒杀活动）；
- 需要丰富锁特性（可重入、读写分离）的场景。

### 方案 2：ZooKeeper 分布式锁（强一致性首选）

#### 核心原理：

基于 ZooKeeper 的「临时有序节点 + Watcher 机制」：

1. 客户端在 `/lock` 节点下创建临时有序子节点（如 `/lock/lock-xxx`）；
2. 若当前节点是 `/lock` 下序号最小的节点，则获取锁；
3. 否则，监听前一个节点的删除事件，前一个节点释放锁后，当前节点竞争锁。

#### 实现方式：

- 推荐用 Curator 框架（ZooKeeper 客户端，已封装分布式锁）：





```java
// Curator 实现分布式锁
CuratorFramework client = CuratorFrameworkFactory.newClient(
    "127.0.0.1:2181",
    new ExponentialBackoffRetry(1000, 3)
);
client.start();

// 创建分布式锁（公平锁，临时节点自动释放）
InterProcessMutex lock = new InterProcessMutex(client, "/distributed/lock/stock-1001");
if (lock.acquire(10, TimeUnit.SECONDS)) { // 10 秒内尝试获取锁
    try {
        // 操作共享资源
    } finally {
        lock.release(); // 释放锁
    }
}
```

#### 优点：

- 强一致性（ZooKeeper 集群的一致性协议保证，无锁冲突）；
- 无死锁风险（临时节点随会话断开自动删除，锁释放）；
- 支持公平锁、读写锁、可重入锁等高级特性；
- 天然支持集群容错（ZooKeeper 集群部署，节点故障不影响）。

#### 缺点：

- 性能中等（ZooKeeper 是 CP 系统，一致性优先，写操作需集群同步）；
- 运维成本高（需部署 ZooKeeper 集群，监控节点状态）；
- 延迟略高（相比 Redis，锁竞争时的 Watcher 通知有一定延迟）。

#### 适用场景：

- 强一致性要求的分布式场景（如分布式事务、数据同步）；
- 并发量适中（每秒几千请求）、可接受一定延迟的场景；
- 不允许出现 “双锁” 的场景（如金融交易）。

### 方案 3：etcd 分布式锁（云原生场景首选）

#### 核心原理：

类似 ZooKeeper，基于 etcd 的「键值对存储 + 版本号 + Watch 机制」，利用 etcd 的 Raft 协议保证一致性，支持原子操作。

#### 实现方式：

- 使用 etcd Java 客户端（如 `etcd-java`）：






```java
// etcd 客户端配置
Client client = Client.builder().endpoints("http://127.0.0.1:2379").build();
KvClient kvClient = client.kvClient();

// 锁标识和持有者
String lockKey = "/lock/stock-1001";
String holder = "server-1-thread-1";

// 获取锁（基于 CAS 原子操作 + 版本号）
long leaseId = client.leaseClient().grant(30).join().getID(); // 30 秒租约（自动释放）
try {
    // 原子操作：仅当 key 不存在时插入（NX），并绑定租约（EX）
    PutResponse response = kvClient.put(
        PutRequest.newBuilder()
            .setKey(ByteString.copyFromUtf8(lockKey))
            .setValue(ByteString.copyFromUtf8(holder))
            .setLease(leaseId)
            .setPrevKv(false)
            .build()
    ).join();
    if (response.getHeader().getRevision() > 0) {
        // 获取锁成功，操作资源
    }
} finally {
    // 释放锁（删除 key）
    kvClient.delete(DeleteRequest.newBuilder().setKey(ByteString.copyFromUtf8(lockKey)).build()).join();
    client.leaseClient().revoke(leaseId).join();
}
```

#### 优点：

- 强一致性（Raft 协议保证）；
- 高性能（相比 ZooKeeper，etcd 读写性能更优，支持高并发）；
- 云原生友好（Kubernetes 生态默认使用 etcd，部署运维一体化）；
- 支持锁超时、自动续期、Watch 机制。

#### 缺点：

- 生态不如 Redis/ZooKeeper 成熟；
- 需部署 etcd 集群（适合已使用 Kubernetes 的场景）。

#### 适用场景：

- 云原生分布式系统（Kubernetes 环境）；
- 强一致性、高并发的云原生场景（如服务发现、配置中心 + 分布式锁）。

## 四、各方案对比与选型建议

| 方案               | 性能 | 一致性   | 可用性 | 运维成本         | 核心特性               | 适用场景                          |
| ------------------ | ---- | -------- | ------ | ---------------- | ---------------------- | --------------------------------- |
| MySQL 分布式锁     | 低   | 中       | 中     | 低（已有 MySQL） | 实现简单、无额外依赖   | 低并发、简单场景、已有 MySQL 集群 |
| Redis 分布式锁     | 高   | 最终一致 | 高     | 中（Redis 集群） | 高并发、支持多种锁类型 | 高并发、低延迟、电商秒杀等        |
| ZooKeeper 分布式锁 | 中   | 强一致   | 高     | 高（ZK 集群）    | 公平锁、无死锁、强一致 | 金融交易、分布式事务、强一致场景  |
| etcd 分布式锁      | 中高 | 强一致   | 高     | 中（K8s 环境）   | 云原生、高性能、强一致 | Kubernetes 生态、云原生分布式系统 |

### 选型核心建议：

1. **优先选 Redis**：若场景是高并发、低延迟，且能接受 “最终一致性”（如电商、秒杀），Redis 是最优解（Redisson 框架开箱即用）；
2. **强一致选 ZooKeeper/etcd**：若场景不允许 “双锁”（如金融、分布式事务），选 ZooKeeper；若已使用 Kubernetes，选 etcd；
3. **慎用 MySQL**：仅在低并发、无额外中间件、简单场景下使用，高并发场景避免使用；
4. **避免重复造轮子**：优先使用成熟框架（Redisson、Curator），不要手动实现复杂逻辑（如锁续期、重试机制）。

## 五、核心总结

分布式锁的选型本质是「**性能、一致性、可用性**」的权衡：

- MySQL：简单但性能差，适合低并发；
- Redis：高性能、高可用，适合高并发（推荐大多数场景）；
- ZooKeeper/etcd：强一致性，适合金融、分布式事务等核心场景；
- etcd：云原生场景的最优解（K8s 生态）。

实际开发中，**Redis 分布式锁（Redisson）** 是性价比最高的选择，既能满足高并发需求，又能通过框架解决锁续期、死锁、可重入等问题，运维成本也相对较低。