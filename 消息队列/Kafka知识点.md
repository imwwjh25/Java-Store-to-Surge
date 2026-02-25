### 一、 为什么通过 Kafka 实现服务通信？选型时不选 RabbitMQ/RocketMQ 的核心原因

Kafka 成为高并发服务通信首选，核心优势是 **高吞吐、低延迟、持久化能力强、扩容灵活**，与 RabbitMQ/RocketMQ 的选型对比可从**核心场景、性能、架构**三个维度拆解：

|    特性维度    |                            Kafka                             |                         RabbitMQ                          |                  RocketMQ                   |
| :------------: | :----------------------------------------------------------: | :-------------------------------------------------------: | :-----------------------------------------: |
|  **核心定位**  |   高吞吐的日志 / 消息管道，适合大数据量、高并发的消息传输    |  基于 AMQP 协议的通用消息队列，适合低延迟、多协议的场景   | 阿里开源的分布式消息队列，兼顾吞吐与实时性  |
|  **吞吐性能**  | 百万级 TPS，支持批量读写，磁盘顺序 IO 优化，适合海量数据传输 |       万级 TPS，基于内存存储，高并发下性能瓶颈明显        | 十万级 TPS，性能介于 Kafka 和 RabbitMQ 之间 |
| **持久化能力** | 消息持久化到磁盘，支持多副本，数据可靠性高，可作为数据存储使用 |     支持持久化，但默认内存存储，持久化后性能下降明显      |     支持持久化，多副本机制，可靠性较高      |
| **扩容灵活性** | 支持 Partition 水平扩容，扩容后 Rebalance 自动重新分配消费任务 |              队列扩容需要手动配置，灵活性差               |   支持 Topic 扩容，扩容流程比 Kafka 复杂    |
|  **消息模型**  | Topic-Partition 模型，消息按 Partition 有序存储，支持分区内有序 | Exchange-Queue 模型，支持多种路由模式（直连、扇形、主题） |  Topic-Queue 模型，支持事务消息、延时消息   |
|  **适用场景**  | 高并发服务通信（如电商订单、直播送礼）、日志采集、大数据流处理 |  轻量级服务通信（如通知推送、任务调度）、多协议集成场景   | 金融级事务消息（如订单支付）、延时消息场景  |

**选型结论**：

- 当业务是 **高并发、海量数据传输**（如票务秒杀、直播送礼的消息同步），优先选 Kafka —— 其批量读写、顺序 IO 能支撑百万级 TPS，且持久化能力保证消息不丢失。
- 不选 RabbitMQ：高并发场景下，RabbitMQ 的内存存储会导致性能瓶颈，且扩容灵活性差，无法应对流量突增。
- 不选 RocketMQ：RocketMQ 的优势在**事务消息、延时消息**，但高吞吐场景下性能不如 Kafka，且生态（如大数据集成）不如 Kafka 成熟。

### 二、 Kafka 的 Rebalance 机制

#### 1. 核心定义

Rebalance 是 **消费组内的消费者与 Partition 重新分配的过程**，目的是保证 **Partition 与消费者的一对一绑定**（一个 Partition 只能被一个消费者消费），实现消费负载均衡。

#### 2. 触发条件

- 消费组内**新增消费者**（如扩容消费实例）；
- 消费组内**消费者下线**（如实例宕机、心跳超时）；
- Topic **新增 Partition**（如扩容 Topic 的 Partition 数量）；
- 消费者主动发起 **Rebalance 请求**（如调用 `consumer.rebalance()`）。

#### 3. 执行流程

Kafka 的 Rebalance 由 **Coordinator（协调器）** 主导，流程分为 3 步：

1. **Join 阶段**：消费者向 Coordinator 发送 `JoinGroup` 请求，申请加入消费组；
2. **Sync 阶段**：Coordinator 选举一个 **Leader 消费者**，由 Leader 制定 Partition 分配方案（如 Range/RoundRobin 策略），并同步给所有消费者；
3. **确认阶段**：所有消费者接收分配方案，开始消费对应的 Partition。

#### 4. 关键问题 & 优化

- Rebalance 期间消费暂停

  ：Rebalance 过程中，所有消费者停止消费，直到分配完成。



→ 优化：设置合理的

  ```
  session.timeout.ms
  ```

（会话超时时间）、

  ```
  heartbeat.interval.ms
  ```

（心跳间隔），减少不必要的 Rebalance。

- 分配策略选择

  ：

    - `RangeAssignor`：按 Partition 序号范围分配，适合 Partition 数量固定的场景；
    - `RoundRobinAssignor`：轮询分配，适合 Partition 数量动态变化的场景；
    - `StickyAssignor`：粘性分配，尽量保持原有分配方案，减少 Partition 迁移成本。



### 三、 什么是零拷贝？

#### 1. 核心定义

零拷贝（Zero-Copy）是 **一种数据传输优化技术**，指数据在 **内核空间与用户空间之间无需拷贝**，直接通过内核缓冲区完成传输，减少 CPU 开销和内存拷贝次数。

#### 2. 传统数据传输的 4 次拷贝、2 次上下文切换

以 “Kafka 从磁盘读取消息发送给消费者” 为例，传统流程：

1. 磁盘 → 内核缓冲区（DMA 拷贝，CPU 不参与）；

2. 内核缓冲区 → 用户缓冲区（CPU 拷贝）；

3. 用户缓冲区 → Socket 缓冲区（CPU 拷贝）；

4. Socket 缓冲区 → 网卡（DMA 拷贝，CPU 不参与）。



→ 缺点：2 次 CPU 拷贝 + 2 次上下文切换，CPU 开销大。

#### 3. Kafka 零拷贝的实现（Linux `sendfile` 系统调用）

Kafka 利用 `sendfile` 实现零拷贝，流程简化为：

1. 磁盘 → 内核缓冲区（DMA 拷贝）；

2. 内核缓冲区 → 网卡（DMA 拷贝，

   无需经过用户缓冲区

   ）。



→ 优势：

0 次 CPU 拷贝 + 1 次上下文切换

，大幅提升高并发下的消息传输性能。

#### 4. 零拷贝的适用场景

- 海量数据传输（如 Kafka 消息分发、日志采集）；
- 大文件下载（如 CDN 加速、文件服务器）。

### 四、 Kafka 怎么防止脑裂？

脑裂（Split-Brain）是指 **Kafka 集群的 Controller 节点或 Partition 的 Leader 节点出现多个 “主节点”**，导致数据不一致。Kafka 主要通过 **ZooKeeper（旧版）/ KRaft（新版）的分布式锁机制 + 选举规则** 防止脑裂。

#### 1. Controller 节点脑裂防止

Controller 是 Kafka 集群的核心节点，负责 Partition 副本分配、Leader 选举。

- 旧版（依赖 ZK） ： Controller 通过 ZK 的临时节点实现选举 —— 只有一个节点能成功创建```/controller```临时节点，成为 Controller。其他节点监听该节点，若 Controller 宕机，临时节点自动删除，触发重新选举。



→ 脑裂防止：ZK 保证同一时间只有一个 Controller 节点，避免多个 Controller 同时工作。

- 新版（KRaft 模式） ： 移除 ZK 依赖，通过 Raft 协议 选举 Controller —— 只有获得 过半节点投票 的节点才能成为 Controller。若集群网络分区，少数派节点无法获得过半投票，无法成为 Controller。

#### 2. Partition Leader 节点脑裂防止

Partition 的 Leader 负责处理读写请求，Follower 同步数据。

- ISR 机制（In-Sync Replicas） ： Kafka 维护一个 ISR 列表 （与 Leader 保持数据同步的 Follower 集合）。只有 ISR 内的副本才有资格参与 Leader 选举。

- 过半投票机制 ： Leader 选举时，必须获得 ISR 内过半副本的确认 才能成为 Leader。若网络分区导致副本失联，失联副本会被移出 ISR，无法参与选举，避免出现多个 Leader。

- min.insync.replicas 参数 ： 设置 Topic 的最小同步副本数（如``` min.insync.replicas=2```），只有当 Leader 与至少 2 个副本同步时，才允许写入消息。防止 Leader 孤立后写入数据，导致脑裂后数据丢失。

### 五、 Kafka 用什么维护 offset？最新版本是否依赖 ZK？

#### 1. Kafka 维护 offset 的两种方式

Offset 是消费者的消费位置标识，记录消费者已消费到 Partition 的哪个位置。Kafka 维护 offset 的方式分为 **旧版** 和 **新版**：

|   版本 / 模式    |            offset 存储位置            |                           核心机制                           |
| :--------------: | :-----------------------------------: | :----------------------------------------------------------: |
| 旧版（依赖 ZK）  |          ZooKeeper 临时节点           | 消费者每隔一段时间，将 offset 提交到 ZK 的 `/consumers/[group]/offsets/[topic]/[partition]` 节点。→ 缺点：ZK 不适合高频率写入，高并发下性能瓶颈明显。 |
| 新版（独立模式） | Kafka 内部 Topic `__consumer_offsets` | 消费者将 offset 提交到 Kafka 内置的 `__consumer_offsets` Topic（Partition 数量默认 50），该 Topic 支持高吞吐、持久化。→ 优势：性能更高，支持 offset 回溯、事务消息。 |

**核心结论**：

- 无论是否依赖 ZK，**新版本 Kafka 都优先使用 `__consumer_offsets` Topic 维护 offset** —— 从 Kafka 0.9 版本开始，就已经支持将 offset 存储在 Kafka 内部。

#### 2. 最新版本 Kafka 是否依赖 ZK？原因是什么？

- **最新版本（Kafka 2.8+）**：支持两种模式



1. **ZK 模式**（兼容旧版）：依赖 ZooKeeper 管理集群元数据（如 Controller 选举、Topic 配置）；
2. **KRaft 模式**（推荐，无 ZK 依赖）：移除 ZK，采用 **Raft 协议** 管理集群元数据。



- **移除 ZK 的核心原因**：



1. **架构简化**：ZK 是额外的分布式系统，增加了集群部署、维护成本。KRaft 模式让 Kafka 成为独立的分布式系统，降低运维复杂度。
2. **性能提升**：ZK 的写性能较低，无法支撑 Kafka 高并发的元数据操作（如 Topic 扩容、Partition 迁移）。KRaft 模式的元数据操作性能更高，支持更大规模的集群。
3. **可扩展性增强**：ZK 集群的扩容和性能优化较为复杂，KRaft 模式的 Kafka 集群可更灵活地水平扩容。