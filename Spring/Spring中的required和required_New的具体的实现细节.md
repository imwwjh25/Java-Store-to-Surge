### 一、先铺垫：Spring 事务的核心底层机制

Spring 事务是「声明式事务」，本质是通过 AOP 动态代理，在目标方法执行前后调用 **事务管理器** 的 `getTransaction()`（获取事务）、`commit()`（提交）、`rollback()`（回滚）方法。

关键支撑组件：

1. TransactionSynchronizationManager（TSM）：线程级别的「事务状态容器」（ThreadLocal 存储），核心存储：

   - 当前事务的「事务对象（TransactionStatus）」：包含事务是否活跃、隔离级别、是否是新事务等信息；
   - 资源与事务的绑定关系：比如数据库连接（Connection）与当前事务绑定，确保同一事务内使用同一连接。

2. TransactionStatus

   ：事务的「状态描述符」，核心属性：

   - `isNewTransaction()`：标记当前事务是否是 “新创建” 的；
   - `getSuspendedResources()`：存储被挂起的外部事务资源（如旧连接、旧事务状态）；
   - `isCompleted()`：标记事务是否已提交 / 回滚。

3. PlatformTransactionManager（事务管理器）：核心实现是```DataSourceTransactionManager```（JDBC 事务），负责：

   - 事务的创建、提交、回滚；
   - 事务的挂起（suspend）与恢复（resume）；
   - 数据库连接的获取、绑定、释放。

### 二、传播行为 REQUIRED：当前事务如何知道外部事务是否存在？

#### 1. REQUIRED 传播行为定义

- 若「当前线程已存在活跃事务」：加入外部事务（共用同一个事务，同生共死）；
- 若「当前线程无活跃事务」：创建新事务。

#### 2. 核心逻辑：通过 TSM 检查「当前事务状态」

Spring 执行目标方法时，会先调用事务管理器的 `getTransaction(TransactionDefinition definition)` 方法，核心步骤如下：


```java
// 简化的 getTransaction 逻辑（DataSourceTransactionManager）
public TransactionStatus getTransaction(TransactionDefinition definition) {
    // 步骤1：从 TSM 中获取当前线程绑定的「事务对象」
    Object currentTransaction = TransactionSynchronizationManager.getResource(dataSource);
    
    // 步骤2：判断外部事务是否存在（核心！）
    boolean existingTransaction = (currentTransaction != null);
    
    if (existingTransaction) {
        // 情况1：存在外部事务 → 加入外部事务
        return handleExistingTransaction(definition, currentTransaction);
    } else {
        // 情况2：无外部事务 → 创建新事务
        return createNewTransaction(definition);
    }
}
```

#### 3. 关键细节：“活跃事务” 的判断标准

- 不是看 “是否有方法加了 `@Transactional`”，而是看「TSM 中是否绑定了有效的事务资源」（比如数据库连接 + 事务状态）；
- 外部事务存在的前提：外部方法的事务已创建（`begin()` 执行完毕），且未提交 / 回滚（事务状态为 `active`）。

#### 4. 加入外部事务的本质：共用数据库连接

- 外部事务创建时，会获取数据库连接 `conn`，调用 `conn.setAutoCommit(false)`，并将 `conn` 绑定到 TSM；
- 内部方法（REQUIRED）执行时，从 TSM 中直接获取已绑定的 `conn`，不再创建新连接，因此所有操作都在同一个事务内。

### 三、传播行为 REQUIRES_NEW：事务的挂起和切换是如何实现的？

#### 1. REQUIRES_NEW 传播行为定义

- 无论「当前线程是否存在活跃事务」：都创建 **全新的独立事务**；
- 若存在外部事务：先将外部事务「挂起」，新事务执行完毕后，再「恢复」外部事务继续执行；
- 新事务与外部事务完全独立：各自提交 / 回滚，互不影响。

#### 2. 核心流程：挂起（Suspend）→ 新建 → 恢复（Resume）

以「外部事务存在」为例，完整步骤拆解（基于 `DataSourceTransactionManager`）：

##### 步骤 1：检查外部事务，触发挂起

当执行 `getTransaction()` 时，发现 TSM 中存在外部事务（`existingTransaction = true`），且传播行为是 `REQUIRES_NEW`，则先执行「挂起外部事务」：

```java
// 简化的挂起逻辑
private TransactionStatus handleExistingTransaction(TransactionDefinition definition, Object currentTransaction) {
    if (definition.getPropagationBehavior() == Propagation.REQUIRES_NEW) {
        // 挂起外部事务，返回挂起的资源
        SuspendedResourcesHolder suspendedResources = suspend(currentTransaction);
        // 创建新事务
        TransactionStatus newTransaction = createNewTransaction(definition);
        // 将挂起的资源存入新事务的状态中，方便后续恢复
        newTransaction.setSuspendedResources(suspendedResources);
        return newTransaction;
    }
}
```

##### 步骤 2：事务挂起（Suspend）的核心操作

“挂起” 不是终止外部事务，而是「将外部事务的状态和资源暂时移出当前线程的 TSM」，避免与新事务冲突。具体做 3 件事：

1. **解绑外部事务资源**：从 TSM 中移除外部事务的数据库连接 `oldConn`、事务状态 `oldTransactionStatus`；
2. **保存挂起资源**：将 `oldConn`、`oldTransactionStatus` 封装到 `SuspendedResourcesHolder`（挂起资源持有者），并存入新事务的 `TransactionStatus` 中；
3. **保持外部事务的活跃状态**：`oldConn` 并未关闭，事务也未提交 / 回滚，只是暂时不与当前线程绑定。

##### 步骤 3：创建新事务（独立于外部事务）

挂起后，Spring 会为 `REQUIRES_NEW` 方法创建全新事务：

1. 从数据源获取 **新的数据库连接 `newConn`**；
2. 调用 `newConn.setAutoCommit(false)`，开启新事务；
3. 将 `newConn` 和新事务的 `TransactionStatus` 绑定到 TSM；
4. 执行目标方法的业务逻辑（所有操作都使用 `newConn`，与外部事务隔离）。

##### 步骤 4：新事务执行完毕，恢复外部事务

新事务提交 / 回滚后（`commit()` 或 `rollback()` 执行），Spring 会触发「恢复外部事务」：

1. **释放新事务资源**：提交 / 回滚 `newConn`，关闭连接，从 TSM 中解绑；
2. **恢复外部事务资源**：从 `SuspendedResourcesHolder` 中取出 `oldConn` 和 `oldTransactionStatus`；
3. **重新绑定到 TSM**：将 `oldConn` 和 `oldTransactionStatus` 重新绑定到当前线程的 TSM；
4. **外部事务继续执行**：回到外部事务的方法，继续后续逻辑（外部事务可正常提交 / 回滚，不受新事务影响）。

#### 3. 关键细节：挂起 / 恢复的底层保障

- 数据库连接的独立性：新事务使用全新连接，与外部事务的连接互不干扰，因此事务隔离级别生效（比如新事务提交后，外部事务未提交前，查询不到新事务的数据）；
- 线程安全：挂起的资源存储在 `TransactionStatus` 中，而 `TransactionStatus` 是线程私有的（通过 TSM 的 ThreadLocal 存储），不会出现多线程资源冲突；
- 异常处理：若新事务执行失败回滚，仅回滚自身操作，外部事务可选择继续执行或回滚；若外部事务回滚，新事务已提交的操作不会被回滚（完全独立）。

### 四、常见疑问澄清

#### 1. 挂起的外部事务会阻塞吗？

不会。挂起只是 “资源解绑”，外部事务的线程会暂停执行（等待 `REQUIRES_NEW` 方法执行完毕），但外部事务的数据库连接并未释放，事务状态仍为活跃。当 `REQUIRES_NEW` 执行完恢复后，外部事务会继续执行，不会阻塞其他线程（除非有数据库锁竞争）。

#### 2. REQUIRES_NEW 如何保证事务隔离？

核心是「独立的数据库连接」。每个事务使用自己的连接，数据库层面通过事务隔离级别（如 `ISOLATION_READ_COMMITTED`）保证隔离，比如：

- 外部事务未提交的数据，新事务看不到；
- 新事务提交的数据，外部事务未恢复前也看不到（恢复后若外部事务未提交，是否能看到取决于隔离级别）。

#### 3. 什么情况下挂起会失败？

若外部事务使用的资源不支持挂起（比如某些非 JDBC 事务管理器），会抛出 `CannotSuspendTransactionException`。但主流的 `DataSourceTransactionManager` 完全支持挂起 / 恢复。

### 总结

1. **REQUIRED 识别外部事务**：通过 `TransactionSynchronizationManager` 检查当前线程是否绑定了事务资源（如数据库连接），有则加入，无则新建；
2. **REQUIRES_NEW 挂起切换**：核心是「资源解绑 - 新建事务 - 资源恢复」，通过 `SuspendedResourcesHolder` 暂存外部事务资源，新事务使用独立连接，确保与外部事务完全隔离。

本质上，Spring 事务传播行为的实现，都是围绕「事务状态的线程级存储（TSM）」和「数据库连接的绑定与切换」展开的，事务管理器是执行这些操作的核心引擎。
