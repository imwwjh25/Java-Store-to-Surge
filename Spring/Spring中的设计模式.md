Spring 框架的设计贯穿了大量经典设计模式，核心是通过模式解耦、提高扩展性和复用性。以下是 Spring 中 **最常用、最核心的设计模式**，结合具体场景和源码示例说明，方便理解：

## 一、核心基础模式（Spring 架构基石）

### 1. 工厂模式（Creator.Factory Pattern）—— IOC 容器的核心

#### 模式定位：创建型模式，隐藏对象创建逻辑，通过工厂统一生成对象

#### Spring 中的应用：

- **核心场景**：Spring IOC 容器（`ApplicationContext`、`BeanFactory`）本质是「Bean 工厂」，负责 Bean 的创建、依赖注入、生命周期管理，替代手动 `new` 对象。

- 具体实现：

    - `BeanFactory`：基础 Bean 工厂接口，定义了 `getBean()` 等核心方法，是工厂模式的顶层接口；
    - `ApplicationContext`：继承 `BeanFactory`，提供更丰富的功能（如注解扫描、资源加载），是实际使用的工厂实现；
    - 开发者通过 `@Component`、`@Service` 等注解标记 Bean，容器（工厂）自动扫描并创建实例，无需关心对象创建细节。

- 源码示例：







  ```java
  // BeanFactory 顶层接口（工厂接口）
  public interface BeanFactory {
      Object getBean(String name) throws BeansException;
      <T> T getBean(Class<T> requiredType) throws BeansException;
  }
  
  // 应用上下文（工厂实现类）
  ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
  UserService userService = context.getBean(UserService.class); // 工厂创建 Bean
  ```



#### 模式价值：解耦对象创建与使用，统一管理 Bean 生命周期，支持依赖注入。

### 2. 单例模式（Creator.Singleton Pattern）—— 默认 Bean 作用域

#### 模式定位：创建型模式，确保一个类仅存在一个实例，提供全局访问点

#### Spring 中的应用：

- **核心场景**：Spring Bean 默认作用域是 `singleton`（单例），IOC 容器中一个 Bean 仅创建一个实例，后续所有 `getBean()` 都返回同一个对象。

- 具体实现：

    - 通过 `BeanDefinition` 中的 `scope` 属性控制（默认 `singleton`，可配置为 `prototype` 多例）；
    - 单例 Bean 的创建时机：默认「懒加载」（第一次获取时创建），可通过 `@Lazy(false)` 改为「预加载」（容器启动时创建）；
    - 线程安全：Spring 仅保证 Bean 实例的唯一性，Bean 本身的线程安全需开发者自行保证（如避免共享可变状态）。

- 源码示例：









  ```java
  // 单例 Bean（默认）
  @Service
  public class UserService {}
  
  // 多例 Bean（显式配置）
  @Service
  @Scope("prototype")
  public class OrderService {}
  ```



#### 模式价值：减少对象创建开销，节省内存，确保全局 Bean 状态一致（如配置类、工具类）。

### 3. 代理模式（Proxy Pattern）—— AOP 的底层实现

#### 模式定位：结构型模式，通过代理对象增强目标对象的功能，不修改原代码

#### Spring 中的应用：

- **核心场景**：Spring AOP 依赖「动态代理」实现切面织入，为目标 Bean 生成代理对象，在目标方法执行前后插入切面逻辑（如日志、事务）。

- 具体实现 ：

    - **JDK 动态代理**：目标对象实现接口时使用，通过 `java.lang.reflect.Proxy` 生成代理对象，代理对象实现目标接口；
    - **CGLIB 动态代理**：目标对象无接口时使用，通过 CGLIB 生成目标对象的子类（代理对象），重写目标方法织入切面；
    - 核心类：`ProxyFactory`（代理工厂）、`JdkDynamicAopProxy`（JDK 代理实现）、`CglibAopProxy`（CGLIB 代理实现）。

- 源码示例 ：

 








  ```java
  // AOP 切面（通过代理增强目标方法）
  @Aspect
  @Component
  public class LogAspect {
      @Before("execution(* com.example.service.*.*(..))")
      public void logBefore() {
          System.out.println("目标方法执行前日志");
      }
  }
  
  // 目标 Bean（会被 Spring 生成代理对象）
  @Service
  public class UserService {
      public void query() {
          System.out.println("查询用户");
      }
  }
  ```



#### 模式价值：无侵入式增强功能，实现横切逻辑复用（如事务、日志、权限），符合「开闭原则」。

### 4. 模板方法模式（Template Method Pattern）—— 统一流程模板

#### 模式定位：行为型模式，定义一个算法流程模板，将可变步骤延迟到子类实现

#### Spring 中的应用：

- **核心场景**：Spring 中大量用于「流程固定、细节可变」的场景，如 JDBC 操作、事务管理、Bean 初始化流程。

- 具体实现：

    - 顶层抽象类定义流程模板（如 `JdbcTemplate` 定义 JDBC 操作的「获取连接→创建语句→执行→关闭连接」流程）；
    - 可变步骤通过「回调接口」或「抽象方法」让用户实现（如 `JdbcTemplate` 的 `RowMapper` 接口，用于结果集映射）；

- **典型示例**：`JdbcTemplate`（JDBC 操作模板）、`TransactionTemplate`（事务操作模板）、`AbstractApplicationContext`（容器初始化模板）。

- 源码示例 （```JdbcTemplate```核心流程）：










  ```java
  // 模板类：定义 JDBC 查询流程（固定）
  public class JdbcTemplate {
      public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
          Connection conn = getConnection(); // 固定步骤1：获取连接
          PreparedStatement stmt = createStatement(conn, sql); // 固定步骤2：创建语句
          ResultSet rs = executeQuery(stmt); // 固定步骤3：执行查询
          List<T> result = rowMapper.mapRow(rs); // 可变步骤：结果集映射（用户实现）
          close(rs, stmt, conn); // 固定步骤4：关闭资源
          return result;
      }
  }
  
  // 回调接口：可变步骤的抽象
  public interface RowMapper<T> {
      List<T> mapRow(ResultSet rs) throws SQLException;
  }
  
  // 用户使用：实现可变步骤
  jdbcTemplate.query("SELECT * FROM user", rs -> {
      List<User> users = new ArrayList<>();
      while (rs.next()) {
          users.add(new User(rs.getInt("id"), rs.getString("name")));
      }
      return users;
  });
  ```



#### 模式价值：统一流程规范，减少重复代码（如资源释放、事务控制），用户仅需关注核心业务逻辑。

## 二、常用扩展模式（Spring 灵活性的关键）

### 5. 观察者模式（Observer Pattern）—— 事件驱动模型

#### 模式定位：行为型模式，定义「发布 - 订阅」关系，当被观察者状态变化时，通知所有观察者

#### Spring 中的应用：

- **核心场景**：Spring 事件驱动模型（`ApplicationEvent` + `ApplicationListener`），用于组件间解耦通信（如容器初始化完成、业务事件通知）。

- 具体实现：

    - 「被观察者」：`ApplicationEventPublisher`（事件发布者，由 `ApplicationContext` 实现）；
    - 「观察者」：`ApplicationListener`（事件监听器，开发者实现该接口监听特定事件）；
    - 「事件」：`ApplicationEvent`（事件基类，可自定义事件继承它，如 `UserCreatedEvent`）。

- **示例场景**：用户注册成功后，发布「用户创建事件」，通知邮件发送、积分初始化等组件执行后续操作。

- 源码示例：




  ```java
  // 1. 自定义事件（被观察的事件）
  public class UserCreatedEvent extends ApplicationEvent {
      private User user;
      public UserCreatedEvent(User user) {
          super(user);
          this.user = user;
      }
  }
  
  // 2. 事件监听器（观察者）
  @Component
  public class UserCreatedListener implements ApplicationListener<UserCreatedEvent> {
      @Override
      public void onApplicationEvent(UserCreatedEvent event) {
          User user = event.getUser();
          System.out.println("给用户 " + user.getName() + " 发送注册邮件");
      }
  }
  
  // 3. 事件发布者（被观察者）
  @Service
  public class UserService {
      @Autowired
      private ApplicationEventPublisher publisher;
  
      public void register(User user) {
          // 注册核心逻辑
          System.out.println("用户 " + user.getName() + " 注册成功");
          // 发布事件
          publisher.publishEvent(new UserCreatedEvent(user));
      }
  }
  ```



#### 模式价值：组件间解耦，发布者无需知道观察者存在，观察者可灵活增减（如新增「短信通知」观察者，无需修改注册逻辑）。

### 6. 策略模式（Strategy Pattern）—— 灵活切换算法 / 逻辑

#### 模式定位：行为型模式，定义多个算法策略，让算法可动态切换，客户端按需选择

#### Spring 中的应用：

- **核心场景**：Spring 中多个可替换的逻辑（如事务传播机制、Bean 实例化策略、AOP 代理策略），通过策略模式支持灵活切换。

- 具体实现：

    - 「策略接口」：定义算法规范（如 `TransactionPropagation` 定义事务传播机制接口）；
    - 「策略实现」：多个不同的算法实现（如 `REQUIRED`、`REQUIRES_NEW` 等事务传播机制实现）；
    - 「策略上下文」：持有策略接口引用，负责策略的选择和执行（如 `TransactionManager` 作为上下文，根据配置选择传播机制）。

- 典型示例：

    - 事务传播机制（`Propagation` 枚举，不同传播行为对应不同策略）；
    - AOP 代理策略（`ProxyConfig` 中 `proxyTargetClass` 属性，切换 JDK/CGLIB 代理）；
    - 资源加载策略（`ResourceLoader` 接口，不同实现加载不同资源：文件、ClassPath、URL）。

- 源码示例 （事务传播机制）：







  ```java
  // 策略接口（事务传播机制规范）
  public enum Propagation {
      REQUIRED, // 如果当前有事务，加入；否则创建新事务
      REQUIRES_NEW, // 无论当前是否有事务，都创建新事务
      SUPPORTS, // 支持当前事务，无则以非事务方式执行
      // 其他策略...
  }
  
  // 策略上下文（事务管理器，使用策略）
  @Service
  public class TransactionManager {
      public void doTransaction(Propagation propagation, Runnable task) {
          if (propagation == Propagation.REQUIRED) {
              System.out.println("执行 REQUIRED 传播机制：加入当前事务或创建新事务");
          } else if (propagation == Propagation.REQUIRES_NEW) {
              System.out.println("执行 REQUIRES_NEW 传播机制：创建新事务");
          }
          task.run();
      }
  }
  
  // 客户端使用：选择策略
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void createOrder() {
      // 订单创建逻辑
  }
  ```



#### 模式价值：算法 / 逻辑可灵活切换，无需修改上下文代码，符合「开闭原则」（新增策略只需实现接口，无需改动现有逻辑）。

### 7. 适配器模式（Adapter Pattern）—— 兼容不同接口

#### 模式定位：结构型模式，将一个类的接口转换成客户端期望的另一个接口，解决接口不兼容问题

#### Spring 中的应用：

- **核心场景**：Spring 中整合不同组件、兼容不同接口的场景（如 AOP 通知适配、Spring MVC 处理器适配）。

- 具体实现 ：

    - 「目标接口」：客户端期望的接口（如 `MethodInterceptor` 是 AOP 通知的目标接口）；
    - 「适配者」：现有接口不兼容的类（如 `BeforeAdvice` 前置通知，接口与 `MethodInterceptor` 不兼容）；
    - 「适配器」：实现目标接口，内部持有适配者引用，将适配者的方法转换为目标接口方法。

- 典型示例：

    - AOP 通知适配器（`AdviceAdapter`）：将 `BeforeAdvice`、`AfterAdvice` 等不同通知类型，适配为统一的 `MethodInterceptor` 接口，方便 AOP 织入；
    - Spring MVC 处理器适配器（`HandlerAdapter`）：将 `Controller`、`HttpRequestHandler` 等不同处理器，适配为统一的接口，让 DispatcherServlet 无需关心处理器类型。

- 源码示例 （AOP 前置通知适配器）：








  ```java
  // 目标接口（AOP 拦截器接口）
  public interface MethodInterceptor {
      Object invoke(MethodInvocation invocation) throws Throwable;
  }
  
  // 适配者（现有前置通知接口，与目标接口不兼容）
  public interface BeforeAdvice {
      void before(Method method, Object[] args, Object target) throws Throwable;
  }
  
  // 适配器：将 BeforeAdvice 适配为 MethodInterceptor
  public class BeforeAdviceAdapter implements MethodInterceptor {
      private BeforeAdvice advice;
  
      public BeforeAdviceAdapter(BeforeAdvice advice) {
          this.advice = advice;
      }
  
      @Override
      public Object invoke(MethodInvocation invocation) throws Throwable {
          // 适配：调用适配者的 before 方法
          advice.before(invocation.getMethod(), invocation.getArguments(), invocation.getThis());
          // 执行目标方法
          return invocation.proceed();
      }
  }
  ```



#### 模式价值：解决接口不兼容问题，让不同组件无缝整合，无需修改现有代码（如 Spring MVC 可兼容各种类型的处理器，无需处理器修改接口）。

## 三、其他常用模式（辅助性设计）

### 8. 装饰器模式（Decorator Pattern）—— 动态增强对象功能

- **应用场景**：Spring 中对 Bean 的「动态增强」（如 `BeanWrapper` 对 Bean 的属性访问增强、`TransactionAwareInvocationHandler` 对事务的增强）。
- **核心逻辑**：通过装饰器类包装目标 Bean，在不修改目标类的前提下，添加额外功能（如属性类型转换、事务控制）。

### 9. 原型模式（Creator.Prototype Pattern）—— 复制对象

- **应用场景**：Spring 中 `prototype` 作用域的 Bean，容器通过「原型模式」创建 Bean 实例（每次 `getBean()` 复制一个新实例）。
- **核心逻辑**：通过 `BeanUtils.copyProperties()` 等工具复制对象属性，快速创建新实例，避免重复初始化。

### 10. 责任链模式（Chain of Responsibility Pattern）—— 链式处理请求

- **应用场景**：Spring MVC 的拦截器链（`HandlerInterceptor`）、Spring 事务的拦截器链、AOP 的通知链。
- **核心逻辑**：将多个处理器（拦截器 / 通知）组成一条链，请求依次经过每个处理器，每个处理器可决定是否继续传递请求或直接处理。

## 四、核心模式总结表

| 设计模式     | 核心作用               | Spring 中的典型应用场景                              |
| ------------ | ---------------------- | ---------------------------------------------------- |
| 工厂模式     | 统一创建对象           | IOC 容器（BeanFactory、ApplicationContext）          |
| 单例模式     | 确保对象唯一           | 默认 singleton 作用域的 Bean                         |
| 代理模式     | 无侵入增强功能         | AOP 动态代理（JDK/CGLIB）                            |
| 模板方法模式 | 统一流程，延迟可变步骤 | JdbcTemplate、TransactionTemplate、容器初始化        |
| 观察者模式   | 发布 - 订阅，组件解耦  | 事件驱动模型（ApplicationEvent/ApplicationListener） |
| 策略模式     | 灵活切换算法 / 逻辑    | 事务传播机制、AOP 代理策略、资源加载策略             |
| 适配器模式   | 兼容不同接口           | AOP 通知适配、Spring MVC 处理器适配                  |
| 装饰器模式   | 动态增强对象功能       | BeanWrapper、事务增强、拦截器增强                    |
| 责任链模式   | 链式处理请求           | Spring MVC 拦截器链、AOP 通知链                      |
| 原型模式     | 复制对象，快速创建实例 | prototype 作用域的 Bean                              |

## 五、关键 takeaway

1. Spring 的核心设计思想是「解耦」，所有模式都围绕这个目标：
    - 工厂模式 / 单例模式：解耦对象创建与使用；
    - 代理模式 / AOP：解耦核心业务与横切逻辑；
    - 观察者模式 / 策略模式：解耦组件间依赖，支持灵活扩展。
2. 最核心的 3 个模式：**工厂模式（IOC 基础）、代理模式（AOP 基础）、模板方法模式（流程复用）**，是理解 Spring 架构的关键。
3. 模式不是孤立的：Spring 常组合使用模式（如 AOP 同时用「代理模式 + 责任链模式 + 适配器模式」，IOC 同时用「工厂模式 + 单例模式 + 原型模式」）。