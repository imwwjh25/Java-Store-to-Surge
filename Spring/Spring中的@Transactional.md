在 Spring Boot 中，`@Transactional` 注解实现事务的核心原理是基于 **AOP（面向切面编程）** 和 **动态代理**，结合底层持久化框架（如 JDBC、Hibernate 等）的事务支持，实现对方法的事务管理。具体流程和关键机制如下：

### 1. 核心原理：AOP 动态代理

`@Transactional` 的本质是通过 AOP 生成**代理对象**，在目标方法执行前后插入事务管理的逻辑（开启、提交、回滚等），而非直接修改目标方法的代码。

- 代理对象的创建：
  Spring 容器启动时，会扫描带有```@Transactional```注解的 Bean，为其创建动态代理（JDK 动态代理或 CGLIB 代理，取决于目标类是否实现接口）。

- 事务切面的织入：代理对象在调用目标方法时，会先执行事务切面（``` TransactionInterceptor```）的逻辑，完成事务的开启、提交或回滚，再调用目标方法。

### 2. 事务管理的具体流程

当调用被 `@Transactional` 注解的方法时，代理对象的执行步骤如下：

#### （1）获取事务属性

解析 `@Transactional` 注解的属性（如 `propagation` 事务传播行为、`isolation` 隔离级别、`timeout` 超时时间等），确定事务的具体规则。

#### （2）开启事务

- 通过```PlatformTransactionManager```（事务管理器）根据事务属性创建或加入一个事务。

  - 底层会通过数据源（如 `DataSource`）获取数据库连接，并设置连接的事务属性（如 `setAutoCommit(false)` 关闭自动提交）。
  - 如果当前线程已存在事务，根据**传播行为**（如 `REQUIRED`、`REQUIRES_NEW` 等）决定是加入现有事务还是创建新事务。

#### （3）执行目标方法

调用被代理的目标方法（如数据库 CRUD 操作），执行具体业务逻辑。

#### （4）提交或回滚事务

- **正常执行**：目标方法无异常抛出时，通过事务管理器提交事务（`commit()`），底层会调用连接的 `commit()` 方法。
- **异常发生**：若抛出 `@Transactional` 注解中 `rollbackFor` 指定的异常（默认是 `RuntimeException` 和 `Error`），则事务管理器回滚事务（`rollback()`），底层调用连接的 `rollback()` 方法。

### 3. 关键组件

- **`@Transactional` 注解**：标记需要事务管理的方法，指定事务属性（传播行为、隔离级别等）。

- **`TransactionInterceptor`**：AOP 切面的核心拦截器，负责在方法执行前后嵌入事务管理逻辑（开启、提交、回滚）。

- `PlatformTransactionManager`：事务管理器接口，是 Spring 事务的核心抽象，具体实现由底层持久化技术决定：

  - `DataSourceTransactionManager`：用于 JDBC 或 MyBatis（基于 `DataSource` 管理连接）。
  - `JpaTransactionManager`：用于 JPA（基于 JPA 的 `EntityManager`）。

- **`TransactionStatus`**：代表当前事务的状态（如是否为新事务、是否已标记回滚等），由事务管理器创建和维护。

### 4. 注意事项（避免事务失效）

- **非 public 方法**：`@Transactional` 默认只对 public 方法生效（代理机制限制），非 public 方法的注解会被忽略。
- **自调用问题**：同一类中方法调用自身带 `@Transactional` 的方法时，代理对象不会被触发，事务失效（需通过代理对象调用）。
- **异常被捕获**：若方法内部捕获了异常且未重新抛出，事务管理器无法感知异常，不会回滚。
- **错误的异常类型**：默认只对 `RuntimeException` 和 `Error` 回滚， checked 异常（如 `IOException`）需通过 `rollbackFor` 指定才会回滚。

### 总结

`@Transactional` 的本质是通过 AOP 动态代理，在目标方法执行前后插入事务管理逻辑，借助事务管理器与底层数据库连接的交互，实现事务的开启、提交和回滚。其核心是将事务管理从业务逻辑中剥离，通过声明式注解简化开发，同时保证事务的 ACID 特性。
