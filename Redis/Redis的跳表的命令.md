Redis 中的跳表（Skip List）是 **Sorted Set（有序集合）** 的底层实现之一（当有序集合的元素数量较多或元素成员较长时，Redis 会使用跳表 + 字典的组合结构存储，小数据量时用压缩列表）。因此，Redis 中没有直接操作跳表的命令，所有对跳表的操作都通过 **Sorted Set 相关命令** 间接实现。

跳表的核心优势是 **高效的范围查询、有序遍历、插入 / 删除 / 查找的平均 O (logN) 时间复杂度**，对应的 Sorted Set 命令也围绕「有序性」「范围操作」「排名统计」设计。以下是与跳表功能强相关的核心命令，按使用场景分类整理：

## 一、核心操作（增删改查元素）

这类命令用于向有序集合中添加、删除、更新元素，底层通过跳表维护元素的有序性（按分数排序）。

| 命令语法                                                     | 功能说明                                                     | 示例                                                         | 跳表关联备注                                  |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | --------------------------------------------- |
| `ZADD key [NX|XX] [GT|LT] [CH] [INCR] score member [score member ...]` | 向有序集合添加元素（支持多元素批量添加），score 为排序依据，member 唯一。 | `ZADD student 95 Alice 88 Bob 92 Charlie`（添加 3 个学生及分数） | 跳表中插入元素，自动维护有序性，平均 O (logN) |
| `ZREM key member [member ...]`                               | 删除有序集合中的指定成员（支持批量删除）                     | `ZREM student Bob`（删除 Bob）                               | 跳表中删除元素，调整索引，平均 O (logN)       |
| `ZINCRBY key increment member`                               | 为指定成员的分数增加 / 减少指定值（increment 可正负）        | `ZINCRBY student 3 Charlie`（Charlie 分数 + 3，变为 95）     | 跳表中更新元素分数，可能调整位置，O (logN)    |
| `ZSCORE key member`                                          | 查询指定成员的分数                                           | `ZSCORE student Alice`（返回 "95"）                          | 跳表中查找成员，O (logN) 时间复杂度           |
| `ZPOPMIN key [count]`                                        | 移除并返回分数最小的 1 个或 count 个成员（Redis 5.0+）       | `ZPOPMIN student 2`（返回分数最小的 2 个成员：Bob、Charlie） | 跳表表头高效获取最小值，O (1) 定位表头        |
| `ZPOPMAX key [count]`                                        | 移除并返回分数最大的 1 个或 count 个成员（Redis 5.0+）       | `ZPOPMAX student`（返回分数最大的 Alice）                    | 跳表表尾高效获取最大值，O (1) 定位表尾        |
| `ZMPOP numkeys key [key ...] [MIN|MAX] [COUNT count]`        | 批量从多个有序集合中移除并返回最值元素（Redis 6.2+）         | `ZMPOP 2 student class1 MAX COUNT 1`（从 2 个集合各取 1 个最大值） | 多集合场景下复用跳表的最值定位能力            |
| `BZMPOP timeout numkeys key [key ...] [MIN|MAX] [COUNT count]` | 阻塞版 ZMPOP，集合为空时阻塞等待（Redis 6.2+）               | `BZMPOP 10 1 student MIN`（阻塞 10 秒，等待 student 集合的最小值） | 基于跳表的阻塞队列实现，依赖有序性监测        |

## 二、范围查询与遍历（跳表核心优势场景）

跳表的分层索引结构使其支持 **高效的范围查询**（O (logN + K)，K 为结果集大小），这类命令是跳表最核心的应用场景。

| 命令语法                                                     | 功能说明                                                     | 示例                                                         | 跳表关联备注                             |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ---------------------------------------- |
| `ZRANGE key start stop [WITHSCORES]`                         | 按 **分数升序** 遍历，返回索引 [start, stop] 的成员（0 起始，-1 表示末尾） | `ZRANGE student 0 2 WITHSCORES`（返回前 3 名：Bob (88)、Charlie (92)、Alice (95)） | 跳表分层索引快速定位起始位置，顺序遍历   |
| `ZREVRANGE key start stop [WITHSCORES]`                      | 按 **分数降序** 遍历，返回索引 [start, stop] 的成员          | `ZREVRANGE student 0 1 WITHSCORES`（返回分数前 2 名：Alice (95)、Charlie (92)） | 跳表从表尾反向遍历，高效逆序查询         |
| `ZRANGEBYSCORE key min max [WITHSCORES] [LIMIT offset count] [EXCLUSIVE]` | 按 **分数区间** 升序查询（支持开区间、分页）                 | `ZRANGEBYSCORE student 90 100 LIMIT 0 2`（分数 90-100 的前 2 人） | 跳表快速定位 min 分数节点，范围扫描      |
| `ZREVRANGEBYSCORE key max min [WITHSCORES] [LIMIT offset count]` | 按 **分数区间** 降序查询                                     | `ZREVRANGEBYSCORE student 95 90`（分数 90-95 的人，降序）    | 跳表定位 max 节点后反向扫描              |
| `ZRANGEBYLEX key min max [LIMIT offset count] [EXCLUSIVE]`   | 按 **成员字典序** 升序查询（仅当所有元素分数相同时有效）     | `ZADD dict 0 apple 0 banana 0 cherry; ZRANGEBYLEX dict [a [c]`（a 到 c 的成员） | 跳表支持按成员字典序排序，适用于字典场景 |
| `ZREVRANGEBYLEX key max min [LIMIT offset count]`            | 按 **成员字典序** 降序查询                                   | `ZREVRANGEBYLEX dict [c [a`（c 到 a 的成员，降序）           | 跳表反向字典序扫描                       |
| `ZSCAN key cursor [MATCH pattern] [COUNT count]`             | 迭代遍历有序集合（类似 SCAN，非阻塞）                        | `ZSCAN student 0 MATCH A*`（遍历以 A 开头的成员）            | 基于跳表的迭代器实现，支持模糊匹配       |

## 三、排名与统计（基于跳表的有序性）

利用跳表的有序性，可快速获取成员的排名、集合大小、分数分布等统计信息。

| 命令语法                | 功能说明                                                     | 示例                                                         | 跳表关联备注                                |
| ----------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------- |
| `ZRANK key member`      | 返回成员按 **分数升序** 的排名（0 起始，即最小分为第 0 名）  | `ZRANK student Alice`（返回 2，因为 Alice 是第 3 名，0 起始） | 跳表从表头遍历计数，O (logN) 定位成员后统计 |
| `ZREVRANK key member`   | 返回成员按 **分数降序** 的排名（0 起始，即最大分为第 0 名）  | `ZREVRANK student Alice`（返回 0，Alice 是第一名）           | 跳表从表尾遍历计数，高效逆序排名            |
| `ZCARD key`             | 返回有序集合的成员总数                                       | `ZCARD student`（返回 3）                                    | 跳表维护了集合大小计数器，O (1) 直接获取    |
| `ZSIZE key`             | ZCARD 的别名，功能完全一致                                   | `ZSIZE student`（返回 3）                                    | 同 ZCARD，O (1) 操作                        |
| `ZCOUNT key min max`    | 统计分数在 [min, max] 区间内的成员数量                       | `ZCOUNT student 90 100`（返回 2，Alice 和 Charlie）          | 跳表定位 min 和 max 节点，统计区间内数量    |
| `ZLEXCOUNT key min max` | 统计成员字典序在 [min, max] 区间内的数量（仅分数相同时有效） | `ZLEXCOUNT dict [a [c]`（返回 2，apple 和 banana）           | 跳表按字典序定位区间，统计数量              |

## 四、批量操作与交集 / 并集（基于跳表的聚合计算）

这类命令用于多有序集合的合并或批量删除，底层依赖跳表的有序性提升聚合效率。

| 命令语法                                                     | 功能说明                                                   | 示例                                                         | 跳表关联备注                           |
| ------------------------------------------------------------ | ---------------------------------------------------------- | ------------------------------------------------------------ | -------------------------------------- |
| `ZUNIONSTORE destination numkeys key [key ...] [WEIGHTS weight ...] [AGGREGATE SUM|MIN|MAX]` | 计算多个有序集合的并集，结果存入 destination（按分数聚合） | `ZUNIONSTORE all_student 2 student class1 WEIGHTS 1 1 AGGREGATE MAX`（取两个集合的并集，分数取最大值） | 利用跳表的有序性，高效合并多个有序集合 |
| `ZINTERSTORE destination numkeys key [key ...] [WEIGHTS weight ...] [AGGREGATE SUM|MIN|MAX]` | 计算多个有序集合的交集，结果存入 destination               | `ZINTERSTORE common_student 2 student class1 AGGREGATE MIN`（取两个集合的交集，分数取最小值） | 基于跳表的有序性，快速匹配交集成员     |
| `ZREMRANGEBYRANK key start stop`                             | 按排名范围删除成员（升序排名 [start, stop]）               | `ZREMRANGEBYRANK student 0 0`（删除排名第 0 名的 Bob）       | 跳表定位排名区间，批量删除             |
| `ZREMRANGEBYSCORE key min max`                               | 按分数范围删除成员                                         | `ZREMRANGEBYSCORE student 90 100`（删除分数 90-100 的成员）  | 跳表定位分数区间，批量删除             |
| `ZREMRANGEBYLEX key min max`                                 | 按字典序范围删除成员（仅分数相同时有效）                   | `ZREMRANGEBYLEX dict [a [b]`（删除字典序 a 到 b 的成员）     | 跳表定位字典序区间，批量删除           |

## 五、关键说明与注意事项

1. **跳表与 Sorted Set 的关系**：Redis 中跳表不单独暴露，仅作为 Sorted Set 的底层存储（当 `zset-max-ziplist-entries`（默认 128）或 `zset-max-ziplist-value`（默认 64）超出阈值时，自动从压缩列表切换为跳表）。
2. **命令效率**：跳表相关命令的平均时间复杂度为 O (logN)（插入、删除、查找、排名），范围查询为 O (logN + K)（K 为结果集大小），远高于普通集合的 O (N)。
3. **分数与排序**：Sorted Set 的排序优先级为「分数升序」，分数相同时按「成员字典序升序」；降序命令（如 ZREVRANGE）则反向排序。
4. **版本差异**：部分命令为 Redis 高版本新增（如 ZMPOP、BZMPOP 需 6.2+，ZPOPMIN/ZPOPMAX 需 5.0+），使用前需确认 Redis 版本。

## 总结

Redis 中操作跳表的核心是 **Sorted Set 系列命令**，其中：

- 跳表的「有序性」支撑了排名（ZRANK）、范围查询（ZRANGEBYSCORE）、字典序操作（ZRANGEBYLEX）等核心功能；
- 跳表的「高效插入 / 删除」支撑了 ZADD、ZREM、ZINCRBY 等命令的性能；
- 范围查询和排名统计是跳表最具优势的场景，也是 Sorted Set 与普通集合的核心区别。