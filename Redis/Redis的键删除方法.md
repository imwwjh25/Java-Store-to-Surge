- 正确答案：Redis 通过惰性删除和定期删除两种策略结合的方式来实现键的过期删除。当一个键设置有过期时间，Redis 不会立即在过期时删除它，而是通过这两种机制协同判断并清理过期键，从而在内存使用和CPU消耗之间取得平衡。
- 解答思路：首先理解 Redis 是如何管理设置了过期时间的键的。Redis 并不会为每个过期键启动一个定时器（那样开销太大），而是采用一种更高效的方式——被动+主动结合的删除策略。具体来说：
  1. 惰性删除（Lazy Expiration）：每次访问某个键时，检查该键是否已过期，如果过期则立即删除并返回 null。
  2. 定期删除（Active Expiration）：Redis 在后台周期性地随机抽取一部分设置了过期时间的键进行检测，删除其中已经过期的键。

这种组合方式既避免了大量定时任务带来的性能损耗，也防止了长期不访问的过期键一直占用内存的问题。

- 深度知识讲解：

  1. **底层数据结构支持**： Redis 中每个数据库是一个 dict（哈希表），其中：

     - key 是键名
     - value 是对应的值对象（robj） 另外，对于设置了过期时间的键，Redis 维护了一个独立的过期字典（expires dict）：
     - key：指向键对象的指针（或直接是键名）
     - value：过期时间戳（毫秒精度，UNIX 时间戳）

     这个 expires 字典可以通过 `EXPIRE`、`SET key value EX seconds` 等命令添加条目。

  2. **惰性删除的实现逻辑**： 当客户端尝试访问一个键时（如 GET 命令），Redis 会先调用 `expireIfNeeded()` 函数来判断该键是否应该被删除。 示例伪代码如下：

     ```
     function expireIfNeeded(db, key):
         ttl = getExpireTime(db, key)  // 从 expires 字典获取过期时间
         if ttl is NULL:
             return false  // 没有过期时间，无需处理
         now = currentTimeInMilliseconds()
         if now >= ttl:
             deleteFromDb(db, key)   // 删除键值对
             signalEvent("expired", key)
             return true
         return false
     ```

     所以，只有在真正访问的时候才会触发删除动作。

  3. **定期删除的实现机制**： Redis 使用一个后台定时任务（serverCron 函数中的一部分）执行 activeExpireCycle() 来周期性地清理过期键。

     具体流程如下：

     - 每次运行时，从设置了过期时间的数据库中随机选取一定数量的键（默认每次取 20 个）。
     - 检查这些键是否过期，若过期则删除。
     - 统计过期键的比例，如果超过一定阈值（例如 25%），则重复此过程，继续采样删除，直到比例下降或达到最大运行时间限制。

     这种“渐进式扫描”避免了一次性扫描全部过期键造成阻塞。

     相关参数控制：

     - hz 配置项（默认 10）决定了 serverCron 每秒运行次数，影响定期删除频率。
     - 每次 activeExpireCycle 的执行时间受 CPU 时间限制，最多不超过规定时间片（通常为 25ms 左右），防止影响主线程响应。

  4. **为什么不用定时器？** 如果为每个过期键都创建一个定时器（比如使用最小堆 + 时间轮），虽然能精确删除，但当存在数百万个过期键时，内存和调度开销极大。而当前方案牺牲一点实时性（允许短暂延迟删除），换取整体性能和稳定性。

  5. **内存回收与复制积压缓冲区的影响**： 即使键被删除，其内存由操作系统或 Redis 自己的内存分配器（如 jemalloc）管理。此外，在主从复制场景下，过期键的删除操作也会生成 DEL 命令传播给从节点，保证一致性。

  6. **扩展知识点**：

     - AOF 和 RDB 持久化中如何处理过期键？
       - RDB：加载时不导入已过期的键；保存时跳过已过期的键。
       - AOF：写入 AOF 文件前会检查是否过期，不写入过期键；但如果键后来才过期，则追加一条 DEL 命令到 AOF。
     - 主从复制中的过期行为：
       - 从节点不会主动删除过期键，只接收来自主节点的 DEL 命令。这是为了确保复制的一致性和顺序性。

- 总结代码示意（简化版）：

  ```
  // 判断是否需要过期删除
  int expireIfNeeded(redisDb *db, robj *key) {
      long long ttl = getExpire(db, key);
      if (ttl == -1) return 0;  // 无过期时间
  
      long long now = mstime();
      if (now < ttl) return 0;  // 尚未过期
  
      // 执行删除
      dbDelete(db, key);
      notifyKeyspaceEvent(NOTIFY_EXPIRED, "expired", key, db->id);
      return 1;
  }
  
  // 定期删除函数片段逻辑
  void activeExpireCycle(int type) {
      int dbs_per_call = CRON_DBS_PER_CALL;  // 每次检查的数据库数量
      int checks_per_loop = ACTIVE_EXPIRE_CYCLE_LOOKUPS_PER_LOOP; // 默认20
  
      for (int i = 0; i < dbs_per_call; i++) {
          redisDb *db = &server.db[i];
          dict *expires = db->expires;
          int sample = dictSize(expires) > CHECKS_PER_LOOP ? CHECKS_PER_LOOP : dictSize(expires);
  
          while (sample--) {
              dictEntry *de = dictGetRandomKey(expires);
              if (expireIfNeeded(db, dictKey(de))) {
                  expired_count++;
              }
          }
          // 若过期比例高，继续循环清理
      }
  }
  ```

综上所述，Redis 的过期删除机制是基于“惰性+定期”的混合策略，兼顾性能与内存效率，适用于高并发、大规模键值存储的场景。这也是其设计哲学之一：在资源消耗与功能完整性之间做出合理折衷。
