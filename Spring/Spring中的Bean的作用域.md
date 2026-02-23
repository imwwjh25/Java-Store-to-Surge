### Bean 的作用域（Spring 核心概念）

Bean 的作用域指 **Spring 容器中 Bean 实例的创建次数、生命周期及可见范围**，Spring 提供 6 种核心作用域（基于 Spring 5.x+，支持 Web 环境），默认作用域为 `singleton`（单例）。

| 作用域名称      | 说明（Bean 实例的生命周期）                                  | 适用场景                                  |
| --------------- | ------------------------------------------------------------ | ----------------------------------------- |
| **singleton**   | 单例（默认）：Spring 容器启动时创建（懒加载除外），容器销毁前一直存在，全局唯一实例。 | 无状态 Bean（如工具类、服务层）           |
| **prototype**   | 原型：每次请求（`getBean()` 或依赖注入）都创建新实例，Spring 不管理销毁（由 JVM 垃圾回收）。 | 有状态 Bean（如命令对象、请求级数据载体） |
| **request**     | Web 环境：每个 HTTP 请求创建一个实例，请求结束后销毁。       | Spring MVC 中请求级数据（如请求上下文）   |
| **session**     | Web 环境：每个 HTTP Session 创建一个实例，Session 失效后销毁。 | 会话级数据（如用户登录态、购物车）        |
| **application** | Web 环境：整个 Web 应用（ServletContext）共享一个实例，应用启动时创建，停止时销毁。 | 应用级全局数据（如配置信息）              |
| **websocket**   | Web 环境：每个 WebSocket 连接创建一个实例，连接关闭后销毁。  | WebSocket 会话级数据                      |

#### 关键补充：

- 作用域通过注解 `@Scope` 或 XML 标签 `<scope>` 配置（如 `@Scope("prototype")`）；
- 非 Web 环境（如普通 Java 应用）仅支持 `singleton` 和 `prototype`，其他作用域需依赖 Spring Web 模块（如 `spring-webmvc`）。

### 18. 什么情况下用单例 Bean，什么情况下用非单例 Bean？

核心判断标准：**Bean 是否有状态（是否存储可变数据）**，结合作用域特性选择：

#### （1）用单例 Bean（`singleton`，默认）的场景

单例 Bean 是 Spring 的默认选择，优势是 **创建开销小、复用性高、无线程安全问题（无状态）**，适用：

- 无状态 Bean ：Bean 不存储可变数据，仅提供固定逻辑（方法无副作用），如：

    - 服务层（Service）：业务逻辑处理（如 `UserService`），不存储请求级数据；
    - 工具类（Util）：如 `DateUtils`、`StringUtil`，仅提供静态 / 实例方法；
    - 数据访问层（DAO/Repository）：如 `UserMapper`（MyBatis 代理类），无状态；
    - 配置类（Configuration）：如 `RedisConfig`，全局唯一配置。

#### （2）用非单例 Bean 的场景

非单例 Bean 核心是 `prototype`（原型），其次是 `request`/`session` 等 Web 作用域，适用：

- 有状态 Bean ：Bean 需存储可变数据，且数据隔离（不同请求 / 线程不共享），如：

    - 命令对象 / 数据载体：如 `UserForm`（接收前端表单参数）、`OrderDTO`（订单临时数据），每次请求需新实例避免数据污染；
    - 线程相关 Bean：如 `ThreadLocal` 载体（若 `ThreadLocal` 存储在单例 Bean 中，需注意线程安全，但原型 Bean 天然隔离）；
    - 动态配置 Bean：需根据请求动态生成不同属性的实例（如不同数据源的 `DataSource` 实例）；
    - Web 作用域场景：`request` 作用域存储当前请求数据（如 `RequestContextHolder` 载体），`session` 作用域存储用户会话数据（如购物车 `Cart`）。

#### 反例（避免踩坑）：

- 单例 Bean 中存储可变成员变量（如 `private String userId`）：多线程 / 多请求共享该变量，导致数据错乱（线程安全问题）；
- 原型 Bean 用于无状态场景：频繁创建 / 销毁实例，增加 JVM 垃圾回收开销，性能下降。

### 19. singleton/prototype 作用域底层实现原理（Spring 容器核心逻辑）

Spring 容器通过 **Bean 定义（BeanDefinition）+ 实例缓存 + 实例创建策略** 实现不同作用域，核心逻辑在 `AbstractBeanFactory` 和 `DefaultSingletonBeanRegistry` 中。

#### 核心前提：Spring 容器的 Bean 生命周期流程

无论哪种作用域，Bean 都需经历「BeanDefinition 解析 → 实例化 → 依赖注入 → 初始化 → 就绪」，差异仅在「实例化时机」和「是否缓存实例」。

------

### （1）singleton（单例）底层实现：缓存复用 + 全局唯一

单例 Bean 的核心是「创建一次，缓存复用」，底层依赖 Spring 的 **单例缓存池** 和「双重检查锁定」保证全局唯一。

#### 关键步骤：

1. **BeanDefinition 标记作用域**：Spring 解析配置（注解 / XML）时，将 Bean 的 `scope` 属性设为 `singleton`（默认），存储在 `BeanDefinition` 中。

2. 首次获取 Bean：创建并缓存 ：

    - 调用 `getBean(beanName)` 时，容器先从「单例缓存池」查询（缓存池是 `DefaultSingletonBeanRegistry` 中的 `ConcurrentHashMap`，key 为 Bean 名称，value 为 Bean 实例）；
    - 缓存未命中（首次获取）：执行「实例化 → 依赖注入 → 初始化」流程，创建 Bean 实例；
    - 将创建好的实例存入单例缓存池（`singletonObjects`），同时记录「正在创建的 Bean」（`singletonsCurrentlyInCreation`），避免循环依赖和重复创建。

3. 后续获取 Bean：直接复用缓存 ：

    - 再次调用 `getBean(beanName)` 时，直接从 `singletonObjects` 缓存中取出实例返回，无需重复创建。

4. 容器销毁：统一销毁单例 Bean ：

    - Spring 容器关闭时（如 `ApplicationContext.close()`），遍历单例缓存池，调用 Bean 的 `destroy()` 方法（或 `@PreDestroy` 注解方法），释放资源。

#### 核心源码片段（简化）：



```java
// DefaultSingletonBeanRegistry（单例缓存池核心类）
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {
    // 单例 Bean 缓存池（key：beanName，value：bean实例）
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
    // 正在创建的 Bean 名称集合（避免重复创建和循环依赖）
    private final Set<String> singletonsCurrentlyInCreation = Collections.newSetFromMap(new ConcurrentHashMap<>(16));

    @Override
    public Object getSingleton(String beanName) {
        return getSingleton(beanName, true);
    }

    protected Object getSingleton(String beanName, boolean allowEarlyReference) {
        // 1. 先查单例缓存池（已创建完成的 Bean）
        Object singletonObject = this.singletonObjects.get(beanName);
        if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
            // 2. 若正在创建，处理循环依赖（提前暴露半成品 Bean）
            synchronized (this.singletonObjects) {
                // 省略循环依赖处理逻辑...
            }
        }
        return singletonObject;
    }

    // 创建单例 Bean 并缓存
    protected <T> T doGetBean(...) {
        // 省略其他逻辑...
        if (mbd.isSingleton()) { // 判断作用域为 singleton
            return (T) getSingleton(beanName, () -> {
                try {
                    // 3. 创建 Bean 实例（实例化+依赖注入+初始化）
                    return createBean(beanName, mbd, args);
                } catch (BeansException ex) {
                    // 异常处理：移除缓存中的无效实例
                    destroySingleton(beanName);
                    throw ex;
                }
            });
        }
        // 其他作用域处理（如 prototype）...
    }
}
```

#### 关键优化：

- 懒加载（`@Lazy`）：默认单例 Bean 在容器启动时创建，加 `@Lazy` 后，首次 `getBean()` 时才创建，缓存逻辑不变；
- 循环依赖处理：通过「三级缓存」（`singletonObjects`、`earlySingletonObjects`、`singletonFactories`）解决单例 Bean 之间的循环依赖。

------

### （2）prototype（原型）底层实现：每次请求创建新实例 + 不缓存

原型 Bean 的核心是「不缓存实例，每次 `getBean()` 都触发完整的创建流程」，Spring 不管理其生命周期（创建后交给用户，销毁由 JVM 负责）。

#### 关键步骤：

1. **BeanDefinition 标记作用域**：Bean 的 `scope` 属性设为 `prototype`。

2. 每次获取 Bean：创建新实例：

    - 调用 `getBean(beanName)` 时，容器先检查 `BeanDefinition` 的 `scope` 为 `prototype`；
    - 直接执行「实例化 → 依赖注入 → 初始化」流程，创建新的 Bean 实例；
    - **不缓存实例**：创建后直接返回给用户，不在任何缓存池中存储。

3. 后续获取 Bean：重复创建 ：

    - 再次调用 `getBean(beanName)` 时，重复步骤 2，生成新的实例（与之前的实例完全独立）。

4. 生命周期管理：Spring 不负责销毁 ：

    - 原型 Bean 创建后，Spring 不再持有引用，当用户代码不再使用时，由 JVM 垃圾回收（GC）销毁；
    - 原型 Bean 的 `@PreDestroy` 注解或 `destroy-method` 不生效（Spring 不触发销毁流程）。

#### 核心源码片段（简化）：






```java
// AbstractBeanFactory（Bean 创建核心类）
protected <T> T doGetBean(...) {
    BeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
    // 检查 BeanDefinition 的作用域
    if (mbd.isPrototype()) { // 作用域为 prototype
        // 1. 创建新实例（每次都调用 createBean，生成新对象）
        Object prototypeInstance = createBean(beanName, mbd, args);
        // 2. 初始化后直接返回，不缓存
        return (T) prototypeInstance;
    }
    // 其他作用域处理（如 singleton）...
}

// AbstractAutowireCapableBeanFactory（创建 Bean 实例的核心类）
protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
    // 省略实例化、依赖注入、初始化逻辑（与单例 Bean 相同，但不缓存）
    // 实例化：通过构造函数/工厂方法创建对象；
    // 依赖注入：填充 Bean 的属性（如 @Autowired 字段）；
    // 初始化：调用 InitializingBean.afterPropertiesSet() 或 @PostConstruct 方法。
    return doCreateBean(beanName, mbd, args);
}
```

#### 关键注意：

- 依赖注入场景：若单例 Bean 依赖原型 Bean，默认情况下，单例 Bean 初始化时会创建一个原型 Bean 实例并注入，后续不会自动创建新实例（即「单例依赖原型」时，原型 Bean 实际表现为 “单例”）；
- 解决上述问题：需使用「方法注入」（`@Lookup` 注解），让单例 Bean 每次调用方法时都获取新的原型 Bean 实例。

示例（@Lookup 解决单例依赖原型）：






```java
// 单例 Bean
@Service
@Scope("singleton")
public class UserService {
    // 方法注入：每次调用 getOrderForm() 都获取新的原型 Bean 实例
    @Lookup
    public OrderForm getOrderForm() {
        return null; // Spring 会动态生成子类，重写该方法，返回新的 OrderForm 实例
    }

    public void createOrder() {
        OrderForm form1 = getOrderForm();
        OrderForm form2 = getOrderForm();
        System.out.println(form1 == form2); // false（两个不同实例）
    }
}

// 原型 Bean
@Component
@Scope("prototype")
public class OrderForm {
    // 有状态字段（如订单号、用户ID）
}
```

------

### 总结

1. **Bean 作用域**：Spring 提供 6 种作用域，核心是 `singleton`（单例）和 `prototype`（原型），Web 环境扩展 `request`/`session` 等；

2. **作用域选择**：无状态 Bean 用 `singleton`（性能优），有状态 Bean 用 `prototype`（数据隔离），Web 场景用 `request`/`session`；

3. 底层实现差异 ：

    - `singleton`：通过「单例缓存池（ConcurrentHashMap）」实现全局唯一，创建后缓存复用，容器管理生命周期；
    - `prototype`：不缓存实例，每次 `getBean()` 都触发完整创建流程，Spring 不管理销毁，依赖 JVM GC。
