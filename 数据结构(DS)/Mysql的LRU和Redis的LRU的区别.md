这个问题洞察很敏锐！Redis 和 MySQL 的 LRU 核心目标一致（淘汰最近最少使用数据），但因应用场景、存储介质、性能要求不同，设计差异极大 ——**Redis 是 “近似 LRU”，追求高并发下的低开销；MySQL 是 “严格 LRU + 优化变种”，兼顾缓存命中率和读写性能**。

### 核心区别总览

| 对比维度     | Redis 的 LRU 设计                            | MySQL 的 LRU 设计（InnoDB 缓冲池）                  |
| ------------ | -------------------------------------------- | --------------------------------------------------- |
| 设计目标     | 内存淘汰（键值对），支持高并发（单线程模型） | 缓存数据页（磁盘页），提升磁盘 I/O 命中率，平衡读写 |
| 实现方式     | 近似 LRU（随机采样 + 候选池）                | 严格 LRU 链表 + 冷热数据分离（midpoint 优化）       |
| 数据粒度     | 键（key）                                    | 数据页（page，默认 16KB）                           |
| 时间复杂度   | 淘汰时 O (1)（采样 + 筛选）                  | 访问 / 淘汰时 O (1)（链表操作）                     |
| 核心优化     | 候选池（提升近似准确性）                     | 冷热分离（避免预读页污染）、LRU 老化（应对长事务）  |
| 触发时机     | 内存达 maxmemory 时，新命令占用内存前触发    | 缓冲池满时，加载新页前触发                          |
| 存储介质依赖 | 纯内存，无磁盘 I/O 开销                      | 缓存磁盘页，核心是减少磁盘随机读                    |

### 一、Redis 的 LRU 设计：近似实现，适配高并发

Redis 是单线程模型，核心诉求是 “低开销、高吞吐”，无法承受严格 LRU 的链表操作开销，因此采用 **近似 LRU**。

#### 1. 核心实现逻辑

- **记录访问时间**：每个键的元数据（`redisObject`）包含 24 位 `lru` 字段，存储 “最后访问时间戳的低 24 位”（秒级精度，足够区分顺序）。

- 淘汰流程 ：

    1. 内存达 `maxmemory` 阈值时，触发淘汰；
    2. 从键空间随机采样 N 个键（默认 N=5，可通过 `maxmemory-samples` 配置）；
    3. 从采样键中淘汰 `lru` 字段最小（最久未访问）的键；
    4. 重复步骤 2-3，直到内存低于阈值。

- **优化（Redis 4.0+）**：引入大小为 16 的 “候选池”，采样键若比候选池中的键更久未访问，则加入池中，淘汰时从候选池选最优，提升准确性（接近严格 LRU）。

#### 2. 设计取舍

- 优点：无额外数据结构开销（无需维护链表），单线程下性能无损耗，适配 Redis 百万级并发。
- 缺点：非严格 LRU，可能淘汰 “较近使用” 的键，但实际场景中准确性足够，性价比极高。

### 二、MySQL（InnoDB）的 LRU 设计：严格 LRU + 变种，优化缓存命中率

MySQL InnoDB 的缓冲池（Buffer Pool）是核心缓存，存储磁盘数据页，目标是 “最大化缓存命中率，减少磁盘 I/O”。因磁盘 I/O 开销极大（毫秒级 vs 内存微秒级），InnoDB 采用 **严格 LRU 为基础，搭配冷热分离、老化机制** 的优化设计。

#### 1. 核心实现逻辑（基础严格 LRU）

- **数据结构**：用双向链表维护缓冲池中的数据页，链表头部（MRU 端）是 “最近使用” 的热数据，尾部（LRU 端）是 “最近最少使用” 的冷数据。

- 访问流程 ：

    1. 访问数据页时，若页已在缓冲池，将其移到链表头部（标记为热数据）；
    2. 若页不在缓冲池，加载新页到链表头部，若缓冲池满，淘汰尾部冷数据页。

#### 2. 关键优化：解决 “预读页污染” 和 “长事务占用”

InnoDB 的 LRU 不是纯严格 LRU，而是做了两处核心优化，否则会严重影响命中率：

##### （1）冷热数据分离（midpoint 插入策略）

- 问题：InnoDB 会 “预读”（提前加载可能需要的数据页，如顺序读时），但很多预读页可能从未被访问，若直接插入头部，会把真正的热数据挤到尾部淘汰，导致 “预读污染”。
- 优化方案：
    1. 将 LRU 链表分为 “热数据区”（前 5/8）和 “冷数据区”（后 3/8），中间有一个 midpoint 节点；
    2. 预读页或首次访问的页，不插入头部，而是插入 midpoint 节点之后（冷数据区头部）；
    3. 只有该页被 “第二次访问” 时，才移到链表头部（热数据区），避免未被使用的预读页污染热数据。

##### （2）LRU 老化机制（应对长事务）

- 问题：长事务会占用数据页，长时间不释放，即使这些页已不是热数据，也会一直停留在热数据区，导致其他热数据被淘汰。
- 优化方案：
    1. 后台线程（Page Cleaner Thread）定期扫描 LRU 链表；
    2. 对热数据区中 “长时间未被访问” 的页，逐步移到冷数据区，避免其长期占用热区资源。

#### 3. 设计取舍

- 优点：严格 LRU 保证核心热数据不被淘汰，搭配冷热分离、老化机制，适配数据库 “预读、长事务” 场景，缓存命中率极高。
- 缺点：链表操作有一定开销，但因数据粒度是 16KB 的数据页（而非 Redis 的单个键），操作频率远低于 Redis，开销可接受。

### 三、核心差异的本质原因

1. 应用场景不同 ：

    - Redis 是内存数据库，LRU 用于 “键值对淘汰”，核心约束是 “单线程高并发”，需最小化操作开销；
    - MySQL 是磁盘数据库，LRU 用于 “数据页缓存”，核心约束是 “减少磁盘 I/O”，需最大化缓存命中率，开销是次要的。

2. 数据粒度不同 ：

    - Redis 键的粒度小（字节到 MB 级），数量多（百万级），无法维护百万级节点的链表（单线程下操作耗时）；
    - MySQL 数据页粒度大（16KB），缓冲池大小通常为 GB 级，节点数远少于 Redis（如 10GB 缓冲池仅 655360 个页），维护链表开销可控。

3. 存储介质依赖不同 ：

    - Redis 纯内存操作，淘汰错误的代价低（大不了重新读取键，无 I/O 开销）；
    - MySQL 缓存的是磁盘页，淘汰错误的代价极高（需重新从磁盘读取，耗时毫秒级），因此必须追求 “尽可能准确的 LRU”。

### 总结

- Redis 的 LRU：**近似实现，以 “低开销” 为核心**，适配单线程高并发，牺牲少量准确性换取吞吐；
- MySQL 的 LRU：**严格实现 + 场景优化，以 “高命中率” 为核心**，适配数据库预读、长事务场景，牺牲少量开销换取稳定性能。



| 对比维度         | Redis 的 LRU 设计                                            | MySQL（InnoDB）的 LRU 设计                                   |
| ---------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 核心目标         | 内存淘汰（键值对），适配单线程高并发                         | 缓存磁盘数据页，最大化 I/O 命中率                            |
| 实现方式         | 近似 LRU（随机采样 + 候选池优化）                            | 严格双向链表 LRU + 冷热分离 + 老化机制                       |
| 数据粒度         | 单个键（key），粒度小（字节～MB 级）                         | 数据页（page），默认 16KB，粒度大                            |
| 时间戳记录       | 键元数据（redisObject）的 24 位 lru 字段（存储最后访问时间戳低 24 位） | 链表节点自带访问状态，通过链表位置标记 “最近使用”            |
| 淘汰触发时机     | 内存达到 maxmemory 阈值，新命令占用内存前                    | 缓冲池满，加载新数据页前                                     |
| 淘汰逻辑         | 1. 随机采样 N 个键（默认 5 个）；2. 从候选池（size=16）选 lru 最小的键淘汰；3. 重复至内存达标 | 1. 淘汰 LRU 链表尾部（冷数据区）数据页；2. 后台线程定期老化热数据区长期未访问的页 |
| 核心优化点       | 候选池（提升近似 LRU 准确性）                                | 1. 冷热分离（midpoint 插入，解决预读污染）；2. 老化机制（应对长事务占用） |
| 并发适配         | 单线程无额外开销，适配百万级并发                             | 链表操作有开销，但数据页数量少，开销可控                     |
| 错误淘汰代价     | 低（纯内存读取，无 I/O 开销）                                | 极高（需从磁盘重新读页，耗时毫秒级）                         |
| 配置参数（核心） | maxmemory（内存阈值）、maxmemory-samples（采样个数）         | innodb_buffer_pool_size（缓冲池大小）、innodb_lru_scan_depth（老化扫描深度） |



以下是 Redis 近似 LRU 和 MySQL（InnoDB）优化版 LRU 的 **具体实现细节**，包含核心数据结构、关键流程代码（伪代码 / 核心逻辑），兼顾原理和落地性：

### 一、Redis 近似 LRU 的具体实现

Redis 核心诉求是 “低开销、适配单线程高并发”，因此没有维护复杂链表，而是基于「键元数据记录 + 随机采样 + 候选池」实现，以下是分步拆解：

#### 1. 核心数据结构

Redis 中每个键对应的 `redisObject` 元数据，是 LRU 实现的基础：



```c
// Redis 键的元数据结构（简化版）
typedef struct redisObject {
    unsigned type:4;        // 键类型（string/hash/zset 等）
    unsigned encoding:4;    // 编码方式（raw/int/ziplist 等）
    unsigned lru:24;        // 核心：存储最后访问时间戳（低 24 位）
    int refcount;           // 引用计数（用于内存回收）
    void *ptr;              // 指向实际数据的指针（如字符串内容、哈希表等）
} robj;
```

- **lru 字段**：24 位足够存储 “秒级时间戳”（2^24 秒 ≈ 194 天），能覆盖绝大多数键的生命周期；

- 访问键时（如```GET```、```SET```），Redis 会更新该字段为当前服务器时间戳的低 24 位：
```c
  // 访问键时更新 lru 字段
  void updateLRUClock(robj *o) {
      o->lru = (unsigned int)(server.lruclock & 0xFFFFFF); // 取低 24 位
  }
 ```
- `server.lruclock` 是 Redis 维护的全局时钟，每秒更新一次，避免频繁调用系统时间函数。

#### 2. 候选池结构（Redis 4.0+ 优化）

为提升近似 LRU 的准确性，引入固定大小（默认 16）的候选池，存储 “候选淘汰键”：








```c
// 候选池结构（简化）
typedef struct {
    robj *obj;       // 候选键的元数据指针
    unsigned int lru; // 候选键的 lru 字段值（缓存，避免重复读取）
} evictionPoolEntry;

evictionPoolEntry eviction_pool[EVICTION_POOL_SIZE]; // EVICTION_POOL_SIZE=16
```

#### 3. 核心淘汰流程（触发时机：内存达 maxmemory 阈值）





```c
// Redis 近似 LRU 淘汰核心逻辑（简化伪代码）
void evictKeys(void) {
    int samples = server.maxmemory_samples; // 采样个数（默认 5，可配置）
    evictionPoolEntry *pool = eviction_pool;

    // 1. 初始化候选池（若为空）
    if (pool[0].obj == NULL) {
        for (int i=0; i<EVICTION_POOL_SIZE; i++) {
            pool[i].obj = NULL;
            pool[i].lru = 0;
        }
    }

    // 2. 随机采样 samples 个键，填充候选池
    while (samples-- > 0) {
        // 从 Redis 键空间随机选一个键（遍历所有数据库的哈希表）
        robj *random_obj = getRandomKey();
        if (random_obj == NULL) break;

        unsigned int obj_lru = random_obj->lru;
        int added = 0;

        // 3. 候选池排序：按 lru 升序排列（越小越久未访问）
        for (int i=0; i<EVICTION_POOL_SIZE; i++) {
            if (pool[i].obj == NULL) {
                // 填充空位置
                pool[i].obj = random_obj;
                pool[i].lru = obj_lru;
                added = 1;
                break;
            } else if (obj_lru < pool[i].lru) {
                // 插入到合适位置，保持升序
                memmove(&pool[i+1], &pool[i], sizeof(evictionPoolEntry)*(EVICTION_POOL_SIZE-i-1));
                pool[i].obj = random_obj;
                pool[i].lru = obj_lru;
                added = 1;
                break;
            }
        }
        // 若候选池满且当前键更久未访问，替换最后一个元素
        if (!added && obj_lru < pool[EVICTION_POOL_SIZE-1].lru) {
            pool[EVICTION_POOL_SIZE-1].obj = random_obj;
            pool[EVICTION_POOL_SIZE-1].lru = obj_lru;
        }
    }

    // 4. 淘汰候选池中 lru 最小的键（最久未访问）
    robj *victim = pool[0].obj;
    if (victim != NULL) {
        deleteKey(victim); // 删除键（同时更新哈希表、跳表等结构）
        // 清空候选池对应位置
        memmove(&pool[0], &pool[1], sizeof(evictionPoolEntry)*(EVICTION_POOL_SIZE-1));
        pool[EVICTION_POOL_SIZE-1].obj = NULL;
    }

    // 5. 重复上述步骤，直到内存低于 maxmemory 阈值
    if (usedMemory() > server.maxmemory) {
        evictKeys();
    }
}
```

#### 4. 关键细节

- 采样个数 `maxmemory_samples` 越大，近似度越高，但开销越大（默认 5 是性能和准确性的平衡）；
- 候选池始终保持升序排列，每次淘汰头部元素，无需重复排序；
- 淘汰时仅删除 “最久未访问” 的键，不涉及复杂链表操作，单线程下开销极低。

### 二、MySQL（InnoDB）优化版 LRU 的具体实现

InnoDB 缓冲池（Buffer Pool）的 LRU 核心是 “严格双向链表 + 冷热分离 + 老化机制”，目标是 “最大化缓存命中率，减少磁盘 I/O”，以下是分步拆解：

#### 1. 核心数据结构

InnoDB 缓冲池中的每个数据页，对应一个 `buf_page_t` 结构，链表节点通过该结构维护：




```c
// InnoDB 数据页结构（简化版，聚焦 LRU 相关字段）
typedef struct buf_page_t {
    // LRU 链表指针（双向链表）
    buf_page_t *lru_prev;   // 前驱节点
    buf_page_t *lru_next;   // 后继节点

    // LRU 相关状态
    unsigned int lru_position; // 标记：热数据区/冷数据区
    unsigned long access_time; // 最后访问时间戳（用于老化机制）
    unsigned int is_preloaded; // 是否是预读页

    // 数据页核心信息
    page_id_t page_id;      // 数据页 ID（表空间+页号）
    unsigned char *frame;   // 数据页在缓冲池中的内存地址
    // ... 其他字段（如锁信息、脏页标记等）
} buf_page_t;
```

#### 2. LRU 链表全局管理结构




```c
// InnoDB LRU 链表管理结构（简化版）
typedef struct buf_pool_t {
    // LRU 链表头尾指针
    buf_page_t *lru_list_head; // 热数据区头部（MRU 端）
    buf_page_t *lru_list_tail; // 冷数据区尾部（LRU 端）
    buf_page_t *lru_midpoint;  // 冷热数据区分界点（midpoint）

    // 配置参数
    unsigned long size;        // 缓冲池总页数
    unsigned long hot_size;    // 热数据区页数（默认 5/8 * 总页数）
    unsigned long cold_size;   // 冷数据区页数（默认 3/8 * 总页数）

    // 后台线程
    os_thread_t lru_age_thread; // LRU 老化线程（Page Cleaner Thread 兼职）
} buf_pool_t;
```

#### 3. 核心优化 1：冷热分离（midpoint 插入策略）

解决 “预读页污染” 问题，是 InnoDB LRU 最关键的优化：





```c
// 数据页加载到缓冲池的插入逻辑（简化伪代码）
void buf_page_load(buf_pool_t *buf_pool, page_id_t page_id, bool is_preload) {
    // 1. 分配缓冲池页帧（frame），创建 buf_page_t 结构
    buf_page_t *new_page = buf_page_alloc(buf_pool);
    new_page->page_id = page_id;
    new_page->is_preloaded = is_preload;

    // 2. 读取磁盘数据页到 frame 中
    disk_read(page_id, new_page->frame);

    // 3. 确定插入位置（核心：冷热分离）
    if (is_preload || !buf_page_was_accessed(new_page)) {
        // 预读页/首次访问页：插入到 midpoint 之后（冷数据区头部）
        buf_page_insert_after(buf_pool, new_page, buf_pool->lru_midpoint);
        new_page->lru_position = LRU_COLD; // 标记为冷数据
    } else {
        // 非预读页+已访问：插入到 LRU 头部（热数据区）
        buf_page_insert_head(buf_pool, new_page);
        new_page->lru_position = LRU_HOT; // 标记为热数据
    }

    // 4. 若缓冲池满，淘汰 LRU 尾部（冷数据区最后一个页）
    if (buf_pool->used_pages >= buf_pool->size) {
        buf_page_evict_tail(buf_pool); // 淘汰尾部页
    }
}

// 数据页被访问时的处理逻辑（触发二次访问移到热区）
void buf_page_access(buf_pool_t *buf_pool, buf_page_t *page) {
    if (page->lru_position == LRU_HOT) {
        // 已在热数据区：移到头部（更新为最近使用）
        buf_page_move_to_head(buf_pool, page);
    } else {
        // 冷数据区：判断是否是二次访问
        if (page->access_time != 0 && current_time - page->access_time > 1) {
            // 二次访问：移到热数据区头部
            buf_page_move_to_head(buf_pool, page);
            page->lru_position = LRU_HOT;
        }
    }
    page->access_time = current_time; // 更新访问时间戳
}
```

#### 4. 核心优化 2：LRU 老化机制（应对长事务）


```c
// 后台线程的 LRU 老化逻辑（简化伪代码）
void buf_lru_age_thread(void *arg) {
    buf_pool_t *buf_pool = (buf_pool_t *)arg;
    while (1) {
        os_thread_sleep(1000); // 每秒扫描一次

        // 遍历热数据区，老化长期未访问的页
        buf_page_t *curr = buf_pool->lru_list_head->lru_next;
        while (curr != buf_pool->lru_midpoint) { // 只遍历热数据区
            if (current_time - curr->access_time > LRU_AGE_THRESHOLD) {
                // 长期未访问：移到冷数据区头部
                buf_page_remove_from_list(buf_pool, curr);
                buf_page_insert_after(buf_pool, curr, buf_pool->lru_midpoint);
                curr->lru_position = LRU_COLD;
            }
            curr = curr->lru_next;
        }
    }
}
```

- `LRU_AGE_THRESHOLD` 是老化阈值（默认可配置），超过该时间未访问的热数据页，被移到冷数据区；
- 老化线程是后台低优先级线程，不影响前台查询性能。

#### 5. 淘汰流程（缓冲池满时触发）




```c
// 淘汰 LRU 尾部冷数据页（简化伪代码）
void buf_page_evict_tail(buf_pool_t *buf_pool) {
    buf_page_t *victim = buf_pool->lru_list_tail; // 冷数据区尾部

    // 1. 若为脏页（数据修改未刷盘），先刷盘（由 Page Cleaner Thread 异步处理）
    if (victim->is_dirty) {
        buf_flush_page(victim);
    }

    // 2. 从 LRU 链表中移除该页
    buf_page_remove_from_list(buf_pool, victim);

    // 3. 释放页帧内存（标记为空闲）
    buf_page_free(victim);

    buf_pool->used_pages--;
}
```

### 三、实现核心差异总结

| 维度         | Redis 实现特点                       | MySQL InnoDB 实现特点                           |
| ------------ | ------------------------------------ | ----------------------------------------------- |
| 数据结构依赖 | 键元数据的 lru 字段 + 固定大小候选池 | 双向链表 + 数据页状态字段（冷热标记、访问时间） |
| 核心操作     | 随机采样 + 候选池排序 + 淘汰头部     | 链表插入 / 移动 / 删除 + 后台老化扫描           |
| 并发适配     | 无锁操作（单线程），开销极低         | 链表操作加轻量级锁（如 LRU 链表锁），开销可控   |
| 场景优化     | 候选池提升近似准确性                 | 冷热分离（防预读污染）+ 老化机制（防长事务）    |