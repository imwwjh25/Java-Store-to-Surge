Spring 事务的核心实现集中在 **AOP 代理** 和 **事务管理器** 两大模块，源码层面主要涉及 `@Transactional` 注解的解析、事务拦截器对方法的增强，以及事务的提交 / 回滚逻辑。以下从关键类和流程入手，拆解核心源码实现：

### 一、核心类与核心接口

1. **`@Transactional` 注解**：标记需要事务管理的方法 / 类，包含传播行为、隔离级别等属性。
2. **`TransactionInterceptor`**：事务拦截器，AOP 增强的核心，负责在目标方法执行前后嵌入事务逻辑（开启、提交、回滚）。
3. **`PlatformTransactionManager`**：事务管理器接口，定义了事务的核心操作（`getTransaction`、`commit`、`rollback`），常见实现如 `DataSourceTransactionManager`（JDBC 事务）、`JpaTransactionManager` 等。
4. **`TransactionStatus`**：表示当前事务的状态（是否新事务、是否已标记回滚等），是事务管理器操作的上下文。

### 二、核心流程源码解析

#### 1. 注解解析与 AOP 代理创建

Spring 启动时，会扫描 `@Transactional` 注解，通过 `TransactionAttributeSource` 解析注解属性（传播行为、隔离级别等），并为目标类创建 **AOP 代理**（JDK 动态代理或 CGLIB）。

- 关键逻辑在 `AbstractAutoProxyCreator` 中，通过 `postProcessAfterInitialization` 方法为符合条件的类生成代理。
- 代理对象的拦截器链中会加入 `TransactionInterceptor`，确保方法调用时触发事务逻辑。

#### 2. 事务拦截器的核心逻辑（`TransactionInterceptor.invoke`）

当调用代理对象的方法时，会触发 `TransactionInterceptor` 的 `invoke` 方法，这是事务管理的入口：



```java
// TransactionInterceptor 核心方法
public Object invoke(MethodInvocation invocation) throws Throwable {
    // 1. 获取目标类和方法
    Class<?> targetClass = invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null;
    Method method = invocation.getMethod();

    // 2. 执行事务增强逻辑（核心）
    return invokeWithinTransaction(method, targetClass, invocation::proceed);
}

// 事务增强的核心实现
protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
                                        final InvocationCallback invocation) throws Throwable {
    // 2.1 解析 @Transactional 注解属性（传播行为、隔离级别等）
    TransactionAttributeSource tas = getTransactionAttributeSource();
    final TransactionAttribute txAttr = tas.getTransactionAttribute(method, targetClass);
    // 2.2 获取事务管理器（如 DataSourceTransactionManager）
    final PlatformTransactionManager tm = determineTransactionManager(txAttr);
    final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

    // 2.3 根据传播行为处理事务
    TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
    Object retVal = null;
    try {
        // 3. 执行目标方法（业务逻辑）
        retVal = invocation.proceedWithInvocation();
    } catch (Throwable ex) {
        // 4. 异常时回滚事务
        completeTransactionAfterThrowing(txInfo, ex);
        throw ex;
    } finally {
        // 清理事务信息
        cleanupTransactionInfo(txInfo);
    }
    // 5. 正常结束时提交事务
    commitTransactionAfterReturning(txInfo);
    return retVal;
}
```

#### 3. 事务的开启（`createTransactionIfNecessary`）

该方法根据 `@Transactional` 的传播行为（如 `REQUIRED`、`REQUIRES_NEW`）决定是否创建新事务，核心调用事务管理器的 `getTransaction` 方法：


```java
// 事务管理器获取事务（以 DataSourceTransactionManager 为例）
public TransactionStatus getTransaction(@Nullable TransactionDefinition definition) throws TransactionException {
    // 1. 解析事务定义（默认传播行为、隔离级别等）
    TransactionDefinition def = (definition != null ? definition : TransactionDefinition.withDefaults());
    // 2. 获取数据库连接（从数据源）
    DataSourceConnectionHolder conHolder = obtainDataSourceHolder();
    // 3. 根据传播行为处理：若当前无事务且传播行为为 REQUIRED，则创建新事务
    if (conHolder == null || conHolder.isSynchronizedWithTransaction()) {
        Connection newCon = obtainDataSource().getConnection();
        conHolder = new DataSourceConnectionHolder(newCon);
        // 绑定连接到线程（ThreadLocal）
        TransactionSynchronizationManager.bindResource(obtainDataSource(), conHolder);
        // 标记为新事务
        status = new DefaultTransactionStatus(...);
    }
    // ... 其他传播行为处理
    return status;
}
```

- 事务开启时，会从数据源获取连接，并通过 `TransactionSynchronizationManager`（基于 `ThreadLocal`）将连接绑定到当前线程，确保同一事务内的操作使用同一连接。

#### 4. 事务的提交与回滚

- **提交**：`commitTransactionAfterReturning` 调用事务管理器的 `commit` 方法，核心是执行 `connection.commit()`，并释放连接。
- **回滚**：`completeTransactionAfterThrowing` 调用事务管理器的 `rollback` 方法，执行 `connection.rollback()`，并根据异常类型判断是否回滚（默认只回滚 `RuntimeException`）。

### 三、关键细节

1. **事务传播行为的实现**：通过 `TransactionSynchronizationManager` 中的 `ThreadLocal` 存储当前事务状态，不同传播行为（如 `REQUIRES_NEW`）会强制创建新连接并解绑旧事务。
2. **异常回滚判断**：`RuleBasedTransactionAttribute.rollbackOn` 方法根据 `rollbackFor` 等属性判断异常是否需要回滚。
3. **只读事务优化**：若 `readOnly=true`，会设置连接为只读（`connection.setReadOnly(true)`），数据库可针对性优化（如禁用写日志）。

### 总结

Spring 事务的源码核心是：

1. 通过 AOP 代理拦截 `@Transactional` 标记的方法；
2. 事务拦截器在方法执行前后，调用事务管理器完成事务的开启、提交 / 回滚；
3. 基于 `ThreadLocal` 绑定数据库连接，确保事务内操作的原子性。

关键类：`TransactionInterceptor`（拦截与增强）、`PlatformTransactionManager`（事务操作）、`TransactionSynchronizationManager`（线程绑定）。理解这些类的交互，就能掌握 Spring 事务的底层实现。