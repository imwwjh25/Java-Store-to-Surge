### 2. ThreadLocal 在 Spring 当中的应用

Spring 大量使用 ThreadLocal 实现线程隔离，核心场景：

1. 事务管理（TransactionSynchronizationManager） ：

    - 存储当前线程的事务状态（如是否开启事务、事务连接），确保同一线程内的数据库操作使用同一个 Connection，保证事务原子性；

2. 请求上下文（RequestContextHolder） ：

    - 基于 `InheritableThreadLocal` 存储 HttpServletRequest、Locale 等上下文信息，使 Controller/Service 层可无感知获取请求数据；

3. 数据绑定（DataBinder） ：

    - 线程内缓存数据绑定的元信息，避免重复创建；

4. 国际化（LocaleContextHolder） ：

    - 存储当前线程的 Locale 信息，支持多语言切换；

5. AOP 上下文 ：

    - 存储切面执行过程中的临时数据（如切点信息、增强器），保证线程安全。
