## 一、核心基础场景（利用 “排序 + 范围查询” 特性）



### 1. 排行榜系统（最经典场景）



ZSet 的核心优势是 “实时排序”，适合各类排行榜（如积分、销量、热度、点击量），支持实时更新和快速查询。

#### 应用示例：



- 游戏排行榜：玩家积分排名、等级排名；
- 电商排行榜：商品销量榜、好评榜、热度榜；
- 内容平台：文章阅读量榜、视频点赞榜、评论热榜。

#### 实操步骤：



1. **添加 / 更新数据**：用 `ZADD` 存储 “成员（用户 ID / 商品 ID）+ 分值（积分 / 销量）”，分值更新时直接覆盖；
2. **查询排行榜**：用 `ZRANGE`（升序）/ `ZREVRANGE`（降序）按排名范围查询，支持返回分值；
3. **查询单个成员排名**：用 `ZRANK`（升序排名）/ `ZREVRANK`（降序排名）获取成员的实时排名。

#### 代码示例（商品销量榜）：

```redis
# 1. 添加/更新商品销量（member=商品ID，score=销量）
ZADD product_sales_rank 100 1001  # 商品1001销量100
ZADD product_sales_rank 250 1002  # 商品1002销量250
ZADD product_sales_rank 180 1003  # 商品1003销量180

# 2. 查询销量TOP3（降序，返回商品ID和销量）
ZREVRANGE product_sales_rank 0 2 WITHSCORES
# 输出：1) "1002" 2) "250" 3) "1003" 4) "180" 5) "1001" 6) "100"

# 3. 查询商品1003的销量排名（降序，排名从0开始）
ZREVRANK product_sales_rank 1003  # 输出：1（第2名）

# 4. 销量增加（商品1001销量+50）
ZINCRBY product_sales_rank 50 1001  # 输出："150"（更新后的销量）
```



#### 扩展优化：



- 按时间分榜：如 “日销量榜”`product_sales_rank:20251114`、“周销量榜”`product_sales_rank:2025W46`，定期清理过期榜单；
- 热点数据缓存：结合 `EXPIRE` 给临时榜单设置过期时间（如小时榜过期 1 小时）。

### 2. 范围统计与筛选（按分值区间查询）



ZSet 支持按分值范围查询（`ZRANGEBYSCORE`），适合需要 “筛选符合分值条件的成员” 的场景，如成绩统计、权限等级筛选、价格区间筛选。

#### 应用示例：



- 学生成绩统计：查询 80 分以上的学生、60-80 分的学生；
- 会员等级筛选：筛选 VIP3 及以上的会员（分值 = 会员等级）；
- 商品价格区间：筛选 100-500 元的商品（分值 = 价格）。

#### 代码示例（学生成绩统计）：


```redis
# 1. 存储学生成绩（member=学生ID，score=成绩）
ZADD student_score 95 2001 88 2002 72 2003 58 2004 92 2005

# 2. 查询80分以上的学生（降序，返回成绩）
ZRANGEBYSCORE student_score 80 +inf WITHSCORES REV
# 输出：1) "2001" 2) "95" 3) "2005" 4) "92" 5) "2002" 6) "88"

# 3. 查询60-80分的学生（升序）
ZRANGEBYSCORE student_score 60 80 WITHSCORES
# 输出：1) "2003" 2) "72"

# 4. 统计80分以上的学生人数
ZCARD student_score  # 总人数：5
ZCOUNT student_score 80 +inf  # 80分以上人数：3
```



### 3. 延时任务队列（利用分值 = 时间戳）



ZSet 可通过 “分值 = 任务执行时间戳” 实现延时任务，核心是：将任务 ID 作为 member，执行时间戳作为 score，定期查询 “当前时间≥score” 的任务，触发执行。

#### 应用场景：



- 订单超时取消（下单后 30 分钟未支付）；
- 短信 / 邮件延时发送（5 分钟后发送验证码）；
- 定时提醒（明天 10 点提醒会议）。

#### 实操步骤：



1. **添加延时任务**：`ZADD delay_queue {执行时间戳} {任务ID}`；
2. **消费任务**：定期用 `ZRANGEBYSCORE` 查询 “score ≤ 当前时间戳” 的任务，执行后用 `ZREM` 删除；
3. **优化**：用 Redis 发布订阅（PUB/SUB）或定时任务框架（如 Quartz）触发查询，避免轮询过于频繁。

#### 代码示例（订单超时取消）：



redis

```redis
# 1. 添加订单延时任务（执行时间=当前时间+30分钟，任务ID=order_1001）
SET current_ts `date +%s`  # 假设当前时间戳=1755000000
ZADD order_delay_queue 1755001800 order_1001  # 1755000000+1800=30分钟后

# 2. 消费任务（查询当前时间前的任务）
ZRANGEBYSCORE order_delay_queue -inf `date +%s` LIMIT 0 10  # 每次取10个任务
# 执行订单取消逻辑后，删除任务
ZREM order_delay_queue order_1001
```



#### 优势：



- 实现简单，无需依赖 RabbitMQ、RocketMQ 等消息队列；
- 支持任务优先级（通过 score 调整执行时间）；
- 缺点：轮询有轻微延迟（可通过缩短轮询间隔优化），高并发场景需结合分布式锁避免重复消费。

## 二、进阶应用场景（结合 ZSet 高级特性）



### 1. 滑动窗口限流（利用 “分值 = 时间戳 + 范围删除”）



ZSet 可实现滑动窗口限流（如 “1 分钟内最多允许 10 次请求”），核心是：将每次请求的时间戳作为 score，member 为 “用户 ID + 接口 ID”（确保唯一），通过 `ZCOUNT` 统计窗口内请求数，用 `ZREMRANGEBYSCORE` 删除窗口外的历史请求。

#### 应用场景：



- 接口限流（如单用户 1 分钟最多 100 次请求）；
- 高频操作限制（如手机号 1 小时最多发送 5 条验证码）。

#### 代码示例（单用户接口限流：1 分钟最多 10 次）：


```redis
# 限流key：user:limit:{用户ID}:{接口ID}
SET key "user:limit:3001:api_login"
SET window 60  # 窗口大小60秒
SET max_req 10  # 最大请求数

# 每次请求执行：
1. 添加当前请求时间戳到ZSet（member=UUID确保唯一）
ZADD ${key} `date +%s` ${uuid}

2. 删除60秒前的历史请求（滑动窗口清理）
ZREMRANGEBYSCORE ${key} -inf `date +%s - ${window}`

3. 统计当前窗口内请求数
SET current_req `ZCOUNT ${key} -inf +inf`

4. 判断是否限流
IF ${current_req} > ${max_req} THEN 拒绝请求 ELSE 允许请求
```



#### 优势：



- 精准控制滑动窗口（无固定窗口的 “临界问题”）；
- 支持分布式限流（Redis 集群部署）；
- 对比 Redis 字符串限流（如 `INCR + EXPIRE`）：滑动窗口更灵活，限流更精准。

### 2. 社交关系：好友 / 关注列表（按分值排序）



ZSet 可存储社交关系，用 score 表示 “关注时间”“亲密度” 等，支持按时间排序、按亲密度筛选。

#### 应用场景：



- 关注列表：按关注时间降序显示（score = 关注时间戳）；
- 好友亲密度：按互动次数排序（score = 亲密度值，互动一次 `ZINCRBY +1`）；
- 粉丝列表：按粉丝等级排序（score = 粉丝等级）。

#### 代码示例（用户关注列表）：


```redis
# 1. 用户3001关注用户1001、1002（score=关注时间戳）
ZADD user_follow:3001 1755000000 1001 1755000500 1002

# 2. 查询用户3001的关注列表（按关注时间降序）
ZREVRANGE user_follow:3001 0 -1 WITHSCORES
# 输出：1) "1002" 2) "1755000500" 3) "1001" 4) "1755000000"

# 3. 取消关注用户1001
ZREM user_follow:3001 1001

# 4. 好友亲密度：用户3001与1002互动，亲密度+5
ZINCRBY user_intimacy:3001 5 1002
```



### 3. 地理信息查询（Geo 底层依赖 ZSet）



Redis 的 Geo 功能（如 `GEOADD`、`GEORADIUS`）底层是基于 ZSet 实现的：将经纬度编码为 score（GeoHash 算法），通过 ZSet 的范围查询实现 “附近的人 / 地点” 功能。

#### 应用场景：



- 附近的商家：查询 1 公里内的餐厅；
- 附近的人：社交软件 “附近的用户”；
- 地理位置排序：按距离从近到远显示。

#### 代码示例（附近的商家）：


```redis
# 1. 添加商家地理信息（member=商家ID，经纬度=纬度,经度）
GEOADD merchant_geo 116.403963 39.915119 2001  # 商家2001（北京天安门附近）
GEOADD merchant_geo 116.404963 39.916119 2002  # 商家2002（附近）

# 2. 查询当前位置（116.403963,39.915119）1公里内的商家（按距离升序）
GEORADIUS merchant_geo 116.403963 39.915119 1000 m WITHDIST WITHLATLNG ASC
# 输出包含商家ID、距离、经纬度

# 底层原理：GEOADD 会将经纬度编码为 score 存入 ZSet，GEORADIUS 本质是 ZRANGEBYSCORE
ZRANGE merchant_geo 0 -1 WITHSCORES  # 可查看编码后的 score
```



### 4. 带权重的消息队列（按分值 = 优先级排序）



普通队列是 “先进先出”，ZSet 可实现 “带优先级的队列”：用 score 表示消息优先级（数值越大优先级越高），消费时按 score 降序获取消息，确保高优先级消息先执行。

#### 应用场景：



- 工单处理：紧急工单（score=100）优先于普通工单（score=50）；
- 任务调度：核心业务任务（score=200）优先于非核心任务（score=100）。

#### 代码示例（带优先级的工单队列）：


```redis
# 1. 添加工单（member=工单ID，score=优先级）
ZADD work_order_queue 100 W1001  # 普通工单（优先级100）
ZADD work_order_queue 200 W1002  # 紧急工单（优先级200）
ZADD work_order_queue 150 W1003  # 重要工单（优先级150）

# 2. 消费高优先级工单（降序获取，每次取1个）
ZREVRANGE work_order_queue 0 0 WITHSCORES  # 获取优先级最高的工单（W1002）
# 执行工单处理逻辑后，删除工单
ZREM work_order_queue W1002
```



## 三、ZSet 应用的关键注意事项（避坑指南）



1. **成员唯一性**：ZSet 的 member 必须唯一，若重复添加相同 member，会覆盖其 score（适合更新排序依据）；

2. **score 精度问题**：score 是浮点数（double 类型），存在精度丢失风险（如存储超大整数时），建议用整数表示（如时间戳、积分），避免小数；

3. 性能优化：

   - 大批量数据查询：用 `LIMIT` 限制返回数量（如 `ZREVRANGE 0 9` 只取 TOP10），避免全量扫描；
   - 过期数据清理：定期用 `ZREMRANGEBYSCORE` 删除过期数据（如滑动窗口限流、延时队列），避免 ZSet 过大；

4. 分布式场景：

   - 延时任务 / 限流：需加分布式锁（如 Redis `SETNX`），避免重复消费 / 统计；
   - 集群部署：Redis 集群支持 ZSet，但需注意 key 分片（避免热点 key 集中在单个节点）；

5. **数据量限制**：ZSet 适合中大规模数据（百万级成员无压力），若数据量达千万级，需考虑分片或分库分表（如按用户 ID 哈希分片）。

## 四、核心总结（场景 - 特性对应表）



| 应用场景                       | 核心依赖 ZSet 特性                        | 关键命令                           |
| ------------------------------ | ----------------------------------------- | ---------------------------------- |
| 排行榜（积分 / 销量 / 热度）   | 按 score 排序、范围查询、score 原子更新   | ZADD、ZREVRANGE、ZINCRBY、ZREVRANK |
| 范围统计（成绩 / 价格 / 等级） | 按 score 区间查询、计数                   | ZRANGEBYSCORE、ZCOUNT、ZCARD       |
| 延时任务队列                   | score = 时间戳、范围查询过期任务          | ZADD、ZRANGEBYSCORE、ZREM          |
| 滑动窗口限流                   | score = 时间戳、范围删除、计数            | ZADD、ZREMRANGEBYSCORE、ZCOUNT     |
| 社交关系（关注 / 亲密度）      | 按 score 排序（时间 / 亲密度）、成员唯一  | ZADD、ZREVRANGE、ZINCRBY、ZREM     |
| 地理信息查询（附近的人）       | Geo 底层依赖 ZSet 的 score 编码与范围查询 | GEOADD、GEORADIUS、GEODIST         |
| 带优先级消息队列               | 按 score 降序获取、成员唯一               | ZADD、ZREVRANGE、ZREM              |

ZSet 的核心价值是 **“有序性 + 高效查询”**，只要业务场景需要 “排序、范围筛选、优先级”，且需要实时更新和快速查询，ZSet 都是最优选择之一，也是 Redis 最具竞争力的数据结构之一。
