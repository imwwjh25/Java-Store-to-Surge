### 2. ThreadLocal 在 Spring 当中的应用

Spring 大量使用 ThreadLocal 解决 **线程上下文数据隔离** 问题，核心场景：

#### （1）事务管理（TransactionSynchronizationManager）

- 原理：通过 ThreadLocal 存储当前线程的事务信息（如数据库连接、事务状态、隔离级别）；

- 作用：保证同一线程内的所有数据库操作复用同一个 Connection，确保事务的原子性；

- 核心代码（简化）：

  ```java
  public abstract class TransactionSynchronizationManager {
      private static final ThreadLocal<Map<Object, Object>> resources = new NamedThreadLocal<>("Transactional resources");
      // 获取当前线程的数据库连接
      public static Object getResource(Object key) {
          return resources.get().get(key);
      }
  }
  ```

  

#### （2）请求上下文（RequestContextHolder）

- 原理：基于 ThreadLocal/InheritableThreadLocal 存储当前请求的上下文（如 HttpServletRequest、HttpServletResponse、Locale）；
- 作用：在 Controller/Service/DAO 层无需传递请求对象，直接通过 `RequestContextHolder.getRequestAttributes()` 获取；
- 场景：拦截器、AOP 中获取请求参数、用户信息，无需方法参数透传。

#### （3）数据绑定（DataBinder）

- 原理：通过 ThreadLocal 存储数据绑定的上下文（如转换服务、校验器）；
- 作用：保证线程内数据转换 / 校验的隔离性，避免多线程干扰。

#### （4）国际化（LocaleContextHolder）

- 原理：通过 ThreadLocal 存储当前线程的 Locale（语言 / 地区）；
- 作用：支持多语言场景，同一请求内的所有操作使用统一的语言配置。

#### （5）Spring MVC 视图解析

- 原理：ThreadLocal 存储视图解析的上下文（如模型数据、视图名称）；
- 作用：保证请求线程内视图解析的独立性。
