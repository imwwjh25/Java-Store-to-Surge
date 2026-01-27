# 2. 仅用 MySQL 实现高性能 + 高并发库存扣减（防超卖）方案

仅依赖 MySQL 解决高并发库存扣减的核心思路：**“先保数据一致性，再提性能”**—— 通过 MySQL 原生特性（事务、锁、索引）解决超卖，再用批量处理、SQL 优化提升并发承载能力，完全不依赖 Redis 等中间件。

## 一、基础准备：高性能库存表设计（核心前提）




```sql
CREATE TABLE `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` bigint NOT NULL COMMENT '商品ID（核心查询条件）',
  `stock` int NOT NULL DEFAULT 0 COMMENT '当前库存',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_product_id` (`product_id`) COMMENT '商品ID唯一索引（确保锁粒度到单品）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '库存表';
```

### 关键设计说明：

1. **唯一索引 `idx_product_id`**：确保扣减库存时，锁仅作用于单个商品（行级锁），避免表锁导致并发阻塞；
2. **乐观锁 `version`**：用于无锁竞争场景，提升高并发读多写少的性能；
3. **InnoDB 引擎**：必须使用支持事务和行级锁的引擎，MyISAM 不支持事务，直接排除。

## 二、高并发多批库存扣减：防超卖核心实现（分场景）

高并发下 “多批请求” 的核心问题是：**多个请求同时读取到相同库存，导致扣减后库存为负（超卖）**。MySQL 解决超卖的本质是 “保证库存扣减的原子性和可见性”，按 “是否允许部分超卖” 分为两类方案：

### 场景 1：完全不允许超卖（核心场景，如电商下单、秒杀）

必须确保库存≥扣减数量后才执行扣减，核心是 “原子校验 + 扣减”，避免并发下的 “读取 - 扣减” 两步操作拆分。

#### 方案 1.1：悲观锁（行级锁，适合库存紧张、并发冲突高场景）

利用 InnoDB 的行级锁，通过 `SELECT ... FOR UPDATE` 锁定目标商品行，确保同一时间只有一个请求能修改该商品库存，完全杜绝超卖。

##### 实现逻辑：

1. 开启事务；
2. 用 `SELECT ... FOR UPDATE` 锁定商品库存行（带唯一索引，仅锁当前商品，不影响其他商品）；
3. 校验库存是否≥扣减数量，满足则扣减，不满足则回滚；
4. 提交事务（释放锁）。

##### 代码示例（Java + MyBatis）：



```java
// 1. 事务注解（确保原子性）
@Transactional(isolation = Isolation.READ_COMMITTED)
public boolean deductStock(Long productId, int deductNum) {
    // 2. 悲观锁锁定商品行（必须通过唯一索引product_id查询，否则可能升级为表锁）
    Inventory inventory = inventoryMapper.lockByProductId(productId);
    if (inventory == null) {
        throw new RuntimeException("商品不存在");
    }
    // 3. 校验库存
    if (inventory.getStock() < deductNum) {
        return false; // 库存不足，扣减失败
    }
    // 4. 原子扣减库存
    int affectRows = inventoryMapper.deductStock(productId, deductNum);
    return affectRows > 0;
}
```

##### Mapper SQL：



```xml
<!-- 悲观锁查询：FOR UPDATE 锁定行 -->
<select id="lockByProductId" resultType="Inventory">
    SELECT id, product_id, stock FROM inventory 
    WHERE product_id = #{productId} 
    FOR UPDATE; <!-- 行级锁，仅锁定当前product_id的行 -->
</select>

<!-- 库存扣减 -->
<update id="deductStock">
    UPDATE inventory 
    SET stock = stock - #{deductNum}, update_time = NOW() 
    WHERE product_id = #{productId};
</update>
```

##### 优点 & 缺点：

- 优点：100% 防超卖，逻辑简单，无需额外处理冲突；
- 缺点：并发高时，请求会排队等待锁（锁竞争），但因是行级锁，仅影响同一商品，不同商品可并行扣减，性能可控。

#### 方案 1.2：乐观锁（无锁竞争，适合并发高、冲突低场景）

利用库存表的 `version` 字段，实现 “无锁扣减”—— 扣减时校验版本号，只有版本号匹配（说明期间无其他请求修改），才执行扣减，否则重试或失败。

##### 实现逻辑：

1. 读取商品库存和版本号；
2. 扣减时，WHERE 条件同时包含 `product_id` 和 `version`（确保原子校验 + 扣减）；
3. 若影响行数 = 1，扣减成功；若 = 0，说明版本号已变更（被其他请求修改），重试或返回失败。

##### 代码示例：


```java
public boolean deductStock(Long productId, int deductNum) {
    int retryCount = 3; // 重试次数（避免无限重试）
    while (retryCount-- > 0) {
        // 1. 读取库存和版本号
        Inventory inventory = inventoryMapper.selectByProductId(productId);
        if (inventory == null || inventory.getStock() < deductNum) {
            return false; // 商品不存在或库存不足
        }
        // 2. 乐观锁扣减（WHERE条件包含version，原子校验）
        int affectRows = inventoryMapper.deductStockWithVersion(
            productId, deductNum, inventory.getVersion()
        );
        if (affectRows > 0) {
            return true; // 扣减成功
        }
        // 3. 版本号不匹配，重试（短暂休眠避免CPU空转）
        try { Thread.sleep(10); } catch (InterruptedException e) {}
    }
    return false; // 重试3次失败，返回
}
```

##### Mapper SQL：


```xml
<!-- 乐观锁扣减：version匹配才执行 -->
<update id="deductStockWithVersion">
    UPDATE inventory 
    SET stock = stock - #{deductNum}, 
        version = version + 1, 
        update_time = NOW() 
    WHERE product_id = #{productId} 
      AND version = #{version} 
      AND stock >= #{deductNum}; <!-- 双重保险，避免库存为负 -->
</update>
```

##### 优点 & 缺点：

- 优点：无锁竞争，并发性能极高（支持每秒万级扣减），不阻塞请求；
- 缺点：冲突高时（如秒杀），重试会失败，需配合 “重试机制” 或 “队列削峰”，适合库存充足、冲突率低的场景。

#### 方案 1.3：SQL 原子扣减（最简方案，适合无需重试场景）

直接用一条 UPDATE 语句完成 “校验 + 扣减”，利用 MySQL 的事务原子性，避免多步操作的并发问题，是最简洁的防超卖方案。

##### 实现逻辑：

UPDATE 语句的 WHERE 条件同时包含 `product_id` 和 `stock >= deductNum`，确保只有库存充足时才执行扣减。

##### 代码示例：

java

```java
@Transactional
public boolean deductStock(Long productId, int deductNum) {
    int affectRows = inventoryMapper.deductStockAtomic(productId, deductNum);
    return affectRows > 0; // 影响行数>0说明扣减成功，否则库存不足
}
```

##### Mapper SQL：

```xml
<!-- 原子扣减：WHERE条件包含库存校验 -->
<update id="deductStockAtomic">
    UPDATE inventory 
    SET stock = stock - #{deductNum}, update_time = NOW() 
    WHERE product_id = #{productId} 
      AND stock >= #{deductNum}; <!-- 原子校验库存，避免超卖 -->
</update>
```

##### 优点 & 缺点：

- 优点：极简，无锁，性能高，100% 防超卖（MySQL 单条 UPDATE 是原子操作）；
- 缺点：不支持重试（库存不足直接失败），适合 “不允许排队、库存充足” 的场景（如普通商品下单）。

### 场景 2：允许部分超卖（特殊业务场景，如促销容忍少量超卖）

部分业务允许 “短暂超卖”（如促销活动为了用户体验，允许超卖 10%），核心是 “松校验 + 事后校准”，兼顾并发和业务灵活性。

#### 方案 2.1：松弛库存校验（允许超卖阈值）

在扣减时，允许库存短暂低于 0，但设置超卖阈值（如最多超卖 10 件），避免无限制超卖。

##### 实现 SQL：

```xml
<!-- 允许超卖阈值：stock >= -10（最多超卖10件） -->
<update id="deductStockWithOverdraft">
    UPDATE inventory 
    SET stock = stock - #{deductNum}, update_time = NOW() 
    WHERE product_id = #{productId} 
      AND stock - #{deductNum} >= -10; <!-- 超卖阈值：-10 -->
</update>
```

##### 事后校准：

- 定时任务（如每 5 分钟）查询库存为负的商品，触发补货或人工处理；
- 若超卖数量超过阈值，自动关闭该商品的下单接口，避免进一步超卖。

#### 方案 2.2：异步扣减（优先返回成功，后台异步确认）

高并发场景下，先返回用户 “下单成功”，将库存扣减请求放入 MySQL 的异步队列（如使用 `INSERT INTO ... SELECT` 写入任务表），后台线程批量处理扣减，允许短暂超卖，事后校准。

##### 实现逻辑：

1. 用户下单时，先写入 “库存扣减任务表”；
2. 立即返回用户 “下单成功”；
3. 后台线程批量查询任务表，执行库存扣减（用方案 1.3 的原子扣减）；
4. 若扣减失败（库存不足），通过短信 / APP 推送通知用户 “订单因库存不足取消”。

##### 核心表设计（任务表）：

```sql
CREATE TABLE `inventory_deduct_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `deduct_num` int NOT NULL COMMENT '扣减数量',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待处理，1-成功，2-失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_status_create_time` (`status`, `create_time`) COMMENT '任务查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '库存扣减任务表';
```

##### 优点：

- 并发极高（写入任务表比直接扣减库存快 10 倍以上），用户体验好；
- 缺点：存在超卖风险，需做好用户沟通（如订单取消通知），适合促销、秒杀等 “体验优先” 的场景。

## 三、批量接口优化：提升高并发批量扣减性能

高并发下 “多批请求” 的性能瓶颈，除了锁竞争，还有 “单条 SQL 执行次数过多”，需通过批量处理减少 SQL 交互次数：

### 1. 批量扣减同一商品（多用户同时扣减同一商品）

用 `CASE WHEN` 实现批量扣减，一次 SQL 处理多个扣减请求（如 100 个用户同时扣减商品 A 的库存），减少 SQL 执行次数。

##### 批量扣减 SQL：

```xml
<!-- 批量扣减同一商品：多个扣减请求合并为一条SQL -->
<update id="batchDeductSameProduct">
    UPDATE inventory 
    SET stock = stock - 
        CASE product_id 
            WHEN #{productId} THEN #{totalDeductNum} 
            ELSE 0 
        END,
        update_time = NOW() 
    WHERE product_id = #{productId} 
      AND stock >= #{totalDeductNum}; <!-- 校验总扣减数量不超过库存 -->
</update>
```

##### 说明：

- 前端 / 网关将同一商品的多批扣减请求合并（如 1 秒内收集 100 个扣减请求，汇总总扣减数量）；
- 一次 SQL 处理批量请求，减少数据库连接开销和锁竞争频率。

### 2. 批量扣减不同商品（多用户扣减不同商品）

用 `ON DUPLICATE KEY UPDATE` 实现批量扣减，一次 SQL 处理多个不同商品的扣减请求。

##### 批量扣减 SQL：

```xml
<!-- 批量扣减不同商品：一次处理多个商品 -->
<insert id="batchDeductDifferentProducts">
    INSERT INTO inventory (product_id, stock)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.productId}, -#{item.deductNum})
    </foreach>
    ON DUPLICATE KEY UPDATE
    stock = stock + VALUES(stock), <!-- 存在则累加（此处为减法，因为VALUES(stock)是负数） -->
    update_time = NOW();
</insert>
```

##### 说明：

- 利用 `product_id` 的唯一索引，实现 “存在则更新，不存在则插入”；
- 批量处理不同商品的扣减，避免单条 SQL 循环执行，提升并发性能。

## 四、关键优化：仅用 MySQL 提升并发承载能力

除了上述方案，还需通过 MySQL 自身优化，支撑高并发库存扣减：

### 1. 索引优化

- 库存表仅保留 `product_id` 唯一索引，避免冗余索引（如联合索引）导致锁冲突或索引维护开销；
- 任务表（异步扣减场景）添加 `status + create_time` 联合索引，确保后台线程快速查询待处理任务。

### 2. 事务隔离级别优化

- 悲观锁场景使用 `READ COMMITTED` 隔离级别（默认是 `REPEATABLE READ`），减少锁持有时间和幻读问题；
- 避免使用 `SERIALIZABLE` 隔离级别（会升级为表锁，并发性能极差）。

### 3. 连接池优化

- 配置合理的 MySQL 连接池大小（如 `max_connections = 2000`），避免高并发时连接耗尽；
- 应用层连接池（如 HikariCP）设置 `maximum-pool-size = 200`（根据 CPU 核心数调整，一般是核心数 * 2+1），避免连接池溢出。

### 4. 分库分表（超大规模场景）

- 若商品数量极多（如千万级商品），按 `product_id` 分库分表（如分 10 个库，每个库 100 万商品），分散库存扣减压力；
- 分表后，同一商品的库存扣减仍在单个分表中，不影响防超卖逻辑。

## 五、总结：方案选型指南

| 业务场景               | 推荐方案                   | 核心优势                              | 并发承载能力（单商品） |
| ---------------------- | -------------------------- | ------------------------------------- | ---------------------- |
| 完全不允许超卖、冲突高 | 悲观锁（行级锁）           | 100% 防超卖，逻辑简单，无重试成本     | 每秒 500-1000 QPS      |
| 完全不允许超卖、冲突低 | 乐观锁 / 原子扣减          | 无锁竞争，性能极高，无需阻塞          | 每秒 1-5 万 QPS        |
| 允许部分超卖、体验优先 | 异步扣减 + 事后校准        | 并发极高，用户体验好，适合促销 / 秒杀 | 每秒 10 万 + QPS       |
| 多批同一商品扣减       | 批量 CASE WHEN 扣减        | 减少 SQL 交互，提升批量处理效率       | 每秒 5000+ QPS         |
| 多批不同商品扣减       | 批量 ON DUPLICATE KEY 扣减 | 一次 SQL 处理多商品，减少连接开销     | 每秒 1 万 + QPS        |

核心原则：仅用 MySQL 时，**防超卖的核心是 “原子性”**（要么全成，要么全败），性能优化的核心是 “减少 SQL 交互、缩小锁粒度”，根据业务场景（是否允许超卖、冲突率高低）选择对应方案即可。
