- 正确答案：使用Redis实现库存扣减时，为避免并发超卖问题，通常采用加锁机制。但简单的SET key value EX seconds方式会面临锁超时导致的锁失效问题（例如业务未执行完锁已过期，其他线程可重复获取）。解决锁超时问题的核心方案是：使用Redis分布式锁的可重入、自动续期机制，如基于Redisson框架的Watchdog机制，或手动实现锁续期（守护线程），同时结合Lua脚本保证原子性操作。

- 解答思路：

    1. 首先明确库存扣减场景的关键点：高并发下防止超卖，必须保证“查询库存—判断是否足够—扣减”这一系列操作的原子性。
    2. 使用Redis作为分布式锁的存储介质，通过SET key value NX EX命令实现基本互斥。
    3. 问题在于如果锁设置的超时时间较短，而业务处理时间较长，锁会提前释放，导致其他线程误入，造成数据不一致。
    4. 因此需要动态延长锁的有效期，即“锁续期”。
    5. 最佳实践是引入Redisson等成熟框架，其内置Watchdog机制自动完成续期；或自行设计守护线程定时刷新TTL。
    6. 同时，库存扣减本身也应通过Lua脚本在Redis中原子执行，避免在客户端判断后再扣减带来的竞态条件。

- 深度知识讲解： 分布式锁的核心挑战包括：互斥性、避免死锁（通过超时）、容错性（单点故障）、可重入性、锁续期等。

    1. 锁超时问题的本质：

        - 若锁不设超时，客户端宕机后锁无法释放，造成死锁。
        - 若锁设固定超时，但业务执行时间不可控，可能锁提前释放，其他客户端获取锁，导致多个线程同时执行临界区代码。

    2. Redisson的Watchdog机制原理：

        - 当客户端成功获取锁后，Redisson会启动一个后台线程（Watchdog），默认每10秒检查一次该锁是否仍被当前客户端持有。
        - 如果持有，则将锁的过期时间重置为默认的30秒（可配置）。
        - 这样只要客户端还在运行，锁就不会因超时而被释放。
        - Watchdog仅在未显式指定leaseTime时触发，若指定了leaseTime，则按指定时间过期，不自动续期。

    3. 原子性库存扣减： 即使加了锁，若在客户端读取库存→判断→扣减，这个过程不是原子的，仍可能出错。正确做法是使用Redis的Lua脚本，将整个逻辑交给Redis执行，利用Redis单线程特性保证原子性。

       示例Lua脚本用于安全扣减库存：

       ```
       -- KEYS[1]: 库存key
       -- ARGV[1]: 请求扣减数量
       -- 返回值：1表示成功，0表示失败
       
       local stock = tonumber(redis.call('GET', KEYS[1]))
       if stock >= tonumber(ARGV[1]) then
           redis.call('DECRBY', KEYS[1], ARGV[1])
           return 1
       else
           return 0
       end
       ```

    4. 可重入锁实现： Redisson还支持可重入锁，通过Hash结构存储：key为锁名，field为客户端唯一标识（如UUID+threadId），value为重入次数。每次加锁时判断是否同一客户端，是则计数+1，并重置TTL。

    5. 安全性保障：

        - 使用SET命令时必须包含NX（不存在则设置）和EX（过期时间）选项，推荐使用SET key value NX EX seconds。
        - 为防止误删其他客户端的锁，删除锁时需校验value是否为当前客户端持有（使用Lua脚本保证比较和删除的原子性）。

- 扩展知识：

    1. Redlock算法：Redis官方提出的分布式锁算法，用于多节点Redis环境下的高可用锁，但存在争议（如网络延迟、时钟漂移问题），一般生产环境建议使用ZooKeeper或etcd实现更安全的分布式锁。
    2. Redis持久化与锁：RDB和AOF可能导致锁状态丢失或重复，因此分布式锁建议不开启持久化，或使用无持久化的Redis实例。
    3. 本地缓存+Redis：对于高频访问的库存，可结合本地缓存（如Caffeine）做预减库存，减少Redis压力，但需注意一致性问题。

- 伪代码示例（使用Redisson）：

  ```
  Config config = new Config();
  config.useSingleServer().setAddress("redis://127.0.0.1:6379");
  RedissonClient redisson = Redisson.create(config);
  
  RLock lock = redisson.getLock("stock_lock");
  
  try {
      // 加锁，使用默认看门狗机制（30秒过期，每10秒续期）
      lock.lock();
  
      // 执行库存扣减（通过Lua脚本保证原子性）
      Long result = (Long) redis.eval(luascript, 1, "stock_key", "1");
      if (result == 1) {
          System.out.println("扣减成功");
      } else {
          System.out.println("库存不足");
      }
  } finally {
      lock.unlock(); // 释放锁，同时取消续期
  }
  ```

- 总结： 解决Redis库存扣减锁超时问题，关键在于引入自动续期机制（如Redisson Watchdog），并配合Lua脚本实现原子操作。避免手动设置过短超时时间，同时确保锁的获取与释放具备原子性和安全性。在高并发电商系统中，这是防止超卖的核心技术之一。