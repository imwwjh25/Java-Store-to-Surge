### 一、ZooKeeper 分布式锁核心功能实现

先针对你关心的**原子性、死锁、可重入、防误删、锁续期**这 5 个核心问题，讲清楚 ZooKeeper 的实现逻辑：

#### 1. 原子性实现

原子性指 “锁的获取 / 释放是不可分割的操作”，ZooKeeper 通过自身特性保证：

- **节点创建的原子性**：ZooKeeper 的`create()`方法是原子操作 —— 要么成功创建节点（拿到锁），要么失败（没拿到），不存在中间状态。
- **有序节点的唯一性**：临时有序节点（EPHEMERAL_SEQUENTIAL）的序号由 ZooKeeper 集群全局分配，确保同一时刻只有一个客户端能拿到最小序号的节点（即唯一持有锁）。
- 对比 Redis：Redis 需要用 SETNX+EX 的原子指令保证，而 ZooKeeper 无需额外指令，天然支持节点创建的原子性。

#### 2. 解决死锁问题

死锁的核心原因是 “持有锁的客户端崩溃，锁无法释放”，ZooKeeper 的解决方案：

- **临时节点特性**：客户端创建的锁节点是`EPHEMERAL`（临时）类型 —— 客户端与 ZooKeeper 的 session 断开（崩溃 / 网络中断）时，节点会被自动删除，锁随之释放。
- **Session 超时机制**：客户端与 ZooKeeper 的 session 有超时时间（默认几十秒），即使客户端异常，超时后 session 失效，节点也会被清理，从根本上避免死锁。
- 对比 Redis：Redis 的过期时间可能因时钟漂移、客户端崩溃导致锁提前 / 延迟释放，而 ZooKeeper 的临时节点是 “被动释放”，更可靠。

#### 3. 可重入实现

可重入指 “同一线程 / 客户端多次获取同一把锁不会阻塞”，实现逻辑（分手动 / Curator 内置）：

- 核心思路

  ：为每个客户端 / 线程维护 “锁持有计数”，并绑定客户端唯一标识。

    1. 客户端首次获取锁时，在 ZooKeeper 节点中存储自己的唯一标识（如`线程ID+UUID`）；
    2. 再次请求锁时，先检查当前最小节点的标识是否是自己：如果是，仅增加本地重入计数，不重新竞争；
    3. 释放锁时，减少计数，只有计数为 0 时才删除节点。

- **Curator 内置实现**：`InterProcessMutex`通过`ThreadLocal`存储每个线程的重入次数，结合节点数据中的客户端标识，天然支持可重入。

#### 4. 避免释放别人的锁

核心是 “锁的归属校验”，确保只有锁的持有者能释放锁：

- **节点数据绑定客户端标识**：创建锁节点时，将客户端唯一标识（如 UUID + 线程 ID）写入节点数据；

- **释放锁前校验**：删除节点前，先读取节点数据，确认标识是自己的 —— 只有匹配成功才执行删除，否则抛出异常。

- 代码层面示例：







  ```java
  // 释放锁前校验归属
  public void unlock() {
      String currentData = new String(client.getData().forPath(currentLockPath));
      if (!clientId.equals(currentData)) {
          throw new RuntimeException("不能释放别人的锁！");
      }
      client.delete().forPath(currentLockPath); // 校验通过才删除
  }
  ```



#### 5. 锁续期（Session 续期）

锁续期是为了 “防止客户端正常持有锁，但 session 超时导致锁被释放”，实现逻辑：

- **ZooKeeper 的 Session 续期机制**：客户端与 ZooKeeper 保持心跳（默认每 3 秒发一次 ping），只要心跳正常，session 就不会超时，临时节点就不会被删除 —— 这就是天然的 “锁续期”。
- **Curator 的自动续期**：Curator 客户端内置了`ConnectionStateListener`，会自动维护 session 心跳，即使客户端长时间持有锁，只要网络正常，锁就不会被释放。
- 对比 Redis：Redis 需要客户端手动开定时任务续期（如 Redisson 的 watch dog），而 ZooKeeper 无需手动操作，由集群和客户端心跳自动保证。

### 二、为什么有了 Redis 还需要 ZooKeeper 实现分布式锁

Redis 和 ZooKeeper 的分布式锁各有优劣，适用场景不同，并非替代关系，核心区别如下：

| 特性              | Redis 分布式锁                                            | ZooKeeper 分布式锁                         |
| ----------------- | --------------------------------------------------------- | ------------------------------------------ |
| **一致性**        | 最终一致性（主从复制可能丢锁）                            | 强一致性（ZAB 协议，集群数据同步后才返回） |
| **锁可靠性**      | 主从切换时可能出现 “锁丢失”（如主节点宕机，从节点未同步） | 几乎无锁丢失（临时节点 + 强一致性）        |
| **阻塞 / 非阻塞** | 非阻塞（需轮询）                                          | 阻塞（Watcher 机制，无需轮询，更高效）     |
| **死锁风险**      | 有（过期时间设置不当、客户端崩溃）                        | 无（临时节点 + Session 超时自动释放）      |
| **性能**          | 极高（内存操作，QPS 可达 10 万 +）                        | 中等（磁盘 + 网络，QPS 几千）              |
| **易用性**        | 需手动处理续期、原子性、防误删                            | Curator 封装完善，开箱即用                 |

**核心原因**：

1. 可靠性优先场景必须用 ZooKeeper ：



比如金融交易、库存扣减、分布式事务等，要求锁 100% 可靠，不允许 “锁丢失”——Redis 的最终一致性可能导致多客户端同时拿到锁，而 ZooKeeper 的强一致性能避免这个问题。

2. 阻塞式锁场景更适合 ZooKeeper ：



ZooKeeper 的 Watcher 机制是 “事件驱动”，客户端等待锁时无需轮询，只需监听前一个节点的删除事件，资源消耗更低；而 Redis 需要客户端定时轮询（如每隔 100ms 查一次锁），浪费 CPU。

3. 无需手动处理续期 ：



Redis 的锁续期需要客户端开线程（如 Redisson 的 watch dog），而 ZooKeeper 的 Session 心跳自动续期，无需额外代码，更稳定。

### 三、ZooKeeper 分布式锁 vs Redisson 分布式锁

Redisson 是 Redis 的 Java 客户端，封装了多种分布式锁（可重入、公平、红锁等），和 ZooKeeper 锁的核心区别：

| 维度             | ZooKeeper 分布式锁（Curator）            | Redisson 分布式锁（Redis）                 |
| ---------------- | ---------------------------------------- | ------------------------------------------ |
| **底层依赖**     | ZooKeeper 集群（临时有序节点 + Watcher） | Redis 集群（String/Hash+SETNX/EX）         |
| **一致性模型**   | 强一致性（ZAB 协议）                     | 最终一致性（主从复制）                     |
| **锁类型支持**   | 公平锁（天然支持，有序节点）、可重入锁   | 公平锁、可重入锁、红锁、读写锁等（更丰富） |
| **性能**         | 低（毫秒级延迟，QPS 几千）               | 高（微秒级延迟，QPS 几万～几十万）         |
| **死锁防护**     | 自动（临时节点 + Session 超时）          | 依赖过期时间 + watch dog 续期（可能失效）  |
| **网络分区影响** | 集群过半节点存活即可工作（ZAB 特性）     | 主从分区可能导致锁丢失（脑裂）             |
| **适用场景**     | 高可靠性、低并发、阻塞式锁场景           | 高并发、高性能、对可靠性要求不极致的场景   |

**典型使用场景对比**：

- 用 Redisson：电商秒杀、接口限流、缓存更新（追求高性能，允许极偶尔的锁异常）；
- 用 ZooKeeper：分布式任务调度、分布式锁管理器、数据库主从切换（追求 100% 可靠，低并发也能接受）。

### 四、核心代码示例（关键功能强化）

#### 1. ZooKeeper 锁防误删 + 续期（Curator 版）








```java
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class ZkLockDemo {
    public static void main(String[] args) throws Exception {
        // 1. 创建ZooKeeper客户端（自动续期Session）
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString("127.0.0.1:2181")
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .sessionTimeoutMs(30000) // Session超时30秒，心跳自动续期
                .build();
        client.start();

        // 2. 创建可重入锁（内置防误删、可重入、原子性）
        InterProcessMutex lock = new InterProcessMutex(client, "/zk_lock");

        // 3. 获取锁（可重入）
        lock.acquire();
        System.out.println("第一次获取锁");
        lock.acquire(); // 重入，无需重新竞争
        System.out.println("第二次获取锁（重入）");

        // 4. 长时间持有锁（Session心跳自动续期，不会超时释放）
        Thread.sleep(60000);

        // 5. 释放锁（只有持有者能释放，内置校验）
        lock.release();
        System.out.println("第一次释放锁");
        lock.release();
        System.out.println("第二次释放锁（真正释放）");

        client.close();
    }
}
```

#### 2. Redisson 锁对比（需手动续期）











```java
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedissonLockDemo {
    public static void main(String[] args) throws Exception {
        // 1. 创建Redisson客户端
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        RedissonClient redisson = Redisson.create(config);

        // 2. 创建可重入锁（watch dog自动续期，默认30秒）
        RLock lock = redisson.getLock("redis_lock");

        // 3. 获取锁（可重入，watch dog自动续期）
        lock.lock(); // 无参：默认30秒过期，watch dog每10秒续期
        System.out.println("第一次获取锁");
        lock.lock();
        System.out.println("第二次获取锁（重入）");

        // 4. 长时间持有锁（watch dog续期，需客户端正常运行）
        Thread.sleep(60000);

        // 5. 释放锁（内置归属校验，防误删）
        lock.unlock();
        System.out.println("第一次释放锁");
        lock.unlock();
        System.out.println("第二次释放锁（真正释放）");

        redisson.shutdown();
    }
}
```

### 总结

1. ZooKeeper 锁核心功能实现：

    - 原子性：节点创建天然原子；死锁：临时节点 + Session 超时自动释放；
    - 可重入：客户端标识 + 本地计数；防误删：节点数据校验归属；
    - 续期：Session 心跳自动续期，无需手动操作。

2. Redis vs ZooKeeper 锁 ：

    - Redis 胜在性能，适合高并发、对可靠性要求不极致的场景；
    - ZooKeeper 胜在可靠性，适合强一致性、低并发、阻塞式锁场景。

3. ZooKeeper vs Redisson ：

    - Redisson 功能更丰富、性能更高，但依赖过期时间 + watch dog 续期，有锁丢失风险；
    - ZooKeeper 锁更可靠，无需手动续期，但性能较低，功能相对单一。

核心选型原则：**性能优先选 Redisson，可靠性优先选 ZooKeeper**。