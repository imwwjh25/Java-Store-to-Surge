MySQL 的`SHOW PROCESSLIST`（或`INFORMATION_SCHEMA.PROCESSLIST`）用于查看当前数据库的**连接会话、执行中的 SQL 语句、线程状态**等关键信息，是排查数据库性能问题（如慢查询、锁等待、连接数过多）的核心工具。以下是`processlist`中各字段的详细解析：

### 一、`processlist`的核心字段

| 字段名    | 含义                                                         |
| --------- | ------------------------------------------------------------ |
| `Id`      | 线程 ID（对应`CONNECTION_ID()`，可通过`KILL Id`终止线程）    |
| `User`    | 执行该线程的 MySQL 用户名（如`root`、应用程序账号）          |
| `Host`    | 客户端的 IP 地址 + 端口（格式：`ip:port`，本地连接显示`localhost`） |
| `db`      | 当前线程关联的数据库名（NULL 表示未指定数据库）              |
| `Command` | 线程正在执行的命令类型（如`Query`、`Sleep`、`Connect`等）    |
| `Time`    | 线程处于当前状态的持续时间（单位：秒）                       |
| `State`   | 线程的具体状态（如`Sending data`、`Locked`、`Waiting for table metadata lock`） |
| `Info`    | 线程正在执行的 SQL 语句（NULL 表示无执行语句，`SHOW FULL PROCESSLIST`显示完整 SQL） |

### 二、关键字段详解

#### 1. `Command`：线程命令类型

常见值及含义：

- **Sleep**：线程处于空闲状态（等待客户端发送新请求），是最常见的状态。
- **Query**：线程正在执行 SQL 查询（如`SELECT`、`UPDATE`）。
- **Connect**：客户端正在连接数据库。
- **Binlog Dump**：主库线程正在向从库发送 binlog（主从复制场景）。
- **Execute**：执行预处理语句（`PREPARE`/`EXECUTE`）。
- **Close stmt**：关闭预处理语句。
- **Quit**：线程正在退出。
- **Kill**：线程正在执行`KILL`命令终止其他线程。

#### 2. `State`：线程具体状态

`State`描述了`Command`的细分状态，常见值：

- **Sending data**：查询正在处理并向客户端返回数据（非仅发送数据，可能包含复杂计算）。
- **Waiting for table lock**：线程等待表级锁（如 MyISAM 表锁、DDL 锁）。
- **Waiting for row lock**：线程等待 InnoDB 行级锁（如事务冲突）。
- **Locked**：线程被其他查询阻塞（如锁等待）。
- **Analyzing**：分析查询语句（如执行计划生成）。
- **Copying to tmp table**：查询需要创建临时表（如`GROUP BY`/`DISTINCT`）。
- **Sorting result**：对结果集进行排序（如`ORDER BY`）。
- **Sleeping**：同`Command=Sleep`，线程空闲。

#### 3. `Time`：持续时间

- 若`Command=Sleep`且`Time`值过大（如超过 300 秒），说明存在长时间空闲的连接，可能导致连接数耗尽。
- 若`Command=Query`且`Time`值过大，说明 SQL 执行缓慢（慢查询），需优化。

#### 4. `Info`：SQL 语句

- `SHOW PROCESSLIST`默认显示 SQL 前 100 字符，`SHOW FULL PROCESSLIST`显示完整 SQL。
- NULL 表示线程无执行语句（如 Sleep 状态）。

### 三、常见场景分析

#### 1. 排查慢查询

- 筛选`Command=Query`且`Time`较大的线程，查看`Info`字段的 SQL，分析执行计划（`EXPLAIN`）优化。

#### 2. 排查锁等待

- 若`State=Waiting for table lock`：检查是否有长时间运行的 MyISAM 查询或未提交的 DDL。
- 若`State=Waiting for row lock`：检查事务隔离级别、是否有长事务占用行锁。

#### 3. 排查连接数过多

- 若大量线程`Command=Sleep`且`Time`过大：调整`wait_timeout`（空闲连接超时时间，默认 8 小时），或优化应用程序的连接池配置。

#### 4. 主从复制异常

- 若主库`Command=Binlog Dump`状态异常：检查主从复制的 IO 线程是否正常，binlog 是否损坏。

### 四、使用示例

1. 查看所有进程（含完整 SQL）：








   ```sql
   SHOW FULL PROCESSLIST;
   ```



2. 筛选非 Sleep 状态的进程：

 








   ```sql
   SELECT * FROM INFORMATION_SCHEMA.PROCESSLIST WHERE Command != 'Sleep';
   ```



3. 终止长时间运行的线程：









   ```sql
   KILL 123; -- 123为线程Id
   ```



### 总结

`processlist`是 MySQL 的 “监控仪表盘”，核心关注**线程状态（Command/State）、执行时间（Time）、SQL 语句（Info）**，可快速定位慢查询、锁冲突、连接泄漏等问题。日常运维中需结合业务场景分析各字段，尤其是`State`字段的细分状态，是排查性能瓶颈的关键。