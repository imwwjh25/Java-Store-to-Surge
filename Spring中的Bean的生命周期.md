## Spring Bean 的生命周期（核心流程 + 关键节点）

Spring Bean 的生命周期是「从对象创建到销毁的完整过程」，核心是 **“通过容器管理对象的初始化、依赖注入、销毁”**，最终生成可直接使用的 Bean 实例。

#### 核心流程（按执行顺序）：

1. **实例化（Instantiation）**

   - 容器通过 BeanDefinition（存储 Bean 元信息，如类名、属性、scope 等），调用 Bean 的构造方法（无参 / 有参）创建原始对象（此时仅完成对象实例化，属性未赋值，依赖未注入）。
   - 关键：仅创建对象，未进行任何初始化操作。

2. **属性赋值（Populate）**

   - 容器根据 BeanDefinition 中的配置（如 `@Autowired`、`@Value`、XML 中的 `<property>`），为 Bean 的属性赋值（注入依赖的其他 Bean 或基本类型值）。
   - 关键：完成 “依赖注入”，此时 Bean 的属性已被赋值，但初始化逻辑未执行。

3. **初始化（Initialization）**这是 Bean 生命周期中最核心的 “自定义增强” 阶段，按顺序执行以下操作：

   - 3.1 执行```Aware```接口回调（若 Bean 实现了对应接口）：Spring 会将容器相关的资源注入 Bean，常见 Aware 接口：

     - `BeanNameAware`：注入当前 Bean 的名称（`setBeanName(String name)`）；
     - `BeanFactoryAware`：注入 BeanFactory 容器（`setBeanFactory(BeanFactory factory)`）；
     - `ApplicationContextAware`：注入 ApplicationContext 容器（`setApplicationContext(ApplicationContext ctx)`）；
     - 目的：让 Bean 主动获取容器资源。

   - 3.2 执行 BeanPostProcessor 前置处理（```postProcessBeforeInitialization```）： 容器中的所有```BeanPostProcessor```会对当前 Bean 进行 “前置增强”（如修改 Bean 属性、替换 Bean 实例），典型应用：Spring AOP 动态代理（为加了```@Transactional```
   - ```@Aspect```的 Bean 生成代理对象）。

   - 3.3 执行自定义初始化方法：按优先级：先执行```@PostConstruct```注解的方法（JSR-250 标准）→ 再执行```init-method```配置的方法（XML 或 ```@Bean(initMethod = "xxx")```）→ 若实现了```InitializingBean ```接口，执行```afterPropertiesSet()```方法。

     - 目的：让开发者在 Bean 初始化完成后执行自定义逻辑（如初始化连接池、加载配置）。

   - 3.4 执行 BeanPostProcessor 后置处理（```postProcessAfterInitialization```）：
     对初始化后的 Bean 进行 “后置增强”，最终生成「可使用的 Bean 实例」（若有代理，此时返回的是代理对象而非原始对象）。

4. **使用（In Use）**Bean 被存入 Spring 容器（单例 Bean 存入单例池 `singletonObjects`），供其他 Bean 注入或外部调用（如 Controller 调用 Service）。

   - 关键：单例 Bean 在此阶段会一直存在，直到容器关闭；原型（prototype）Bean 每次被获取时都会重复执行 “实例化→属性赋值→初始化” 流程，使用后由 JVM 垃圾回收（Spring 不管理原型 Bean 的销毁）。

5. **销毁（Destruction）**容器关闭时（如应用停止），对 Bean 执行销毁操作，按优先级：

   - 先执行 `@PreDestroy` 注解的方法（JSR-250 标准）；
   - 再执行 `destroy-method` 配置的方法（XML 或 `@Bean(destroyMethod = "xxx")`）；
   - 若实现了 `DisposableBean` 接口，执行 `destroy()` 方法。
   - 目的：释放资源（如关闭连接池、销毁线程池）。

#### 简化记忆口诀：

**实例化 → 赋属性 → Aware 回调 → BeanPostProcessor 前置 → 自定义初始化 → BeanPostProcessor 后置 → 使用 → 销毁**

#### 关键注意点：

- 单例 Bean 的生命周期与 Spring 容器一致（容器启动创建，容器关闭销毁）；
- 原型 Bean 容器仅负责 “创建→初始化”，使用后不管理销毁，由 JVM 回收；
- `BeanPostProcessor` 是 Spring 扩展的核心（如 AOP、注解解析），作用于所有 Bean 的初始化前后；
- 初始化方法（`@PostConstruct`、`afterPropertiesSet` 等）的执行时机是 “属性赋值完成后”，确保依赖已注入。
