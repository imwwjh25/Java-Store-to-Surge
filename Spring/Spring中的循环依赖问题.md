## 一、先明确：SpringBoot 对循环依赖的默认支持

SpringBoot 底层依赖 Spring 容器，默认仅解决 **单例（`@Scope("singleton")`，默认作用域）** Bean 的循环依赖，核心依赖「三级缓存机制」：

- 三级缓存：`singletonObjects`（一级，存储完全初始化的单例 Bean）、`earlySingletonObjects`（二级，存储提前暴露的未完全初始化 Bean）、`singletonFactories`（三级，存储 Bean 工厂，用于提前暴露 Bean 引用）；
- 工作原理：当 A 依赖 B、B 依赖 A 时，Spring 会先创建 A 的半成品实例，通过三级缓存提前暴露其引用，再创建 B 时注入 A 的引用，B 初始化完成后注入 A，最终 A 完成初始化，打破循环。

**注意**：非单例 Bean（如 `@Scope("prototype")`）、构造器注入的单例 Bean，Spring 无法默认解决，需手动处理。

## 二、循环依赖的 6 种解决方法（按优先级 + 实用性排序）

### 1. 方案 1：使用 @Lazy 延迟加载（最简单，优先推荐）

通过 `@Lazy` 注解延迟其中一个 Bean 的初始化，打破 “互相即时依赖” 的链条 —— 被注解的 Bean 会在第一次被使用时才初始化，而非启动时。

#### 适用场景：单例 Bean，循环依赖由 “即时初始化” 导致。

#### 代码示例（A 依赖 B，B 依赖 A）：

```java
// A 类
@Component
public class AService {
    private final BService bService;

    // 给依赖的 B 加 @Lazy，延迟 B 的初始化
    @Autowired
    public AService(@Lazy BService bService) {
        this.bService = bService;
    }
}

// B 类
@Component
public class BService {
    private final AService aService;

    @Autowired
    public BService(AService aService) {
        this.aService = aService;
    }
}
```

#### 原理：`@Lazy` 会让 Spring 为 B 创建一个代理对象，注入 A 中，A 初始化时无需等待 B 完全初始化，打破循环。

### 2. 方案 2：改用 Setter / 字段注入（替换构造器注入）

Spring 无法默认解决「构造器注入的单例循环依赖」（构造器需参数完全初始化，无法提前暴露引用），但支持 Setter 注入或字段注入的循环依赖（依赖可后续注入）。

#### 适用场景：循环依赖由 “构造器注入” 导致。

#### 代码示例（将构造器注入改为 Setter 注入）：

```java
// A 类
@Component
public class AService {
    private BService bService;

    // Setter 注入（无构造器注入）
    @Autowired
    public void setBService(BService bService) {
        this.bService = bService;
    }
}

// B 类
@Component
public class BService {
    private AService aService;

    @Autowired
    public void setAService(AService aService) {
        this.aService = aService;
    }
}
```

#### 原理：Setter 注入允许 Bean 先初始化（无参构造），再后续注入依赖，契合 Spring 三级缓存的 “提前暴露引用” 逻辑。

### 3. 方案 3：抽取公共依赖到第三方 Bean（最彻底，推荐复杂场景）

将循环依赖的 “公共逻辑 / 依赖项” 抽取到一个新的第三方 Bean 中，让原本循环的两个 Bean 都依赖这个新 Bean，打破直接循环。

#### 适用场景：循环依赖是因为 “双方都需要同一部分逻辑”，而非核心依赖。

#### 代码示例（A 和 B 都依赖公共逻辑，抽取为 CommonService）：

```java
// 抽取的公共 Bean
@Component
public class CommonService {
    // 原本 A 和 B 共享的逻辑（如数据查询、工具方法）
    public void commonMethod() {
        // 业务逻辑
    }
}

// A 类：依赖 CommonService，不再直接依赖 B
@Component
public class AService {
    private final CommonService commonService;

    @Autowired
    public AService(CommonService commonService) {
        this.commonService = commonService;
    }
}

// B 类：依赖 CommonService，不再直接依赖 A
@Component
public class BService {
    private final CommonService commonService;

    @Autowired
    public BService(CommonService commonService) {
        this.commonService = commonService;
    }
}
```

#### 原理：从设计上消除循环依赖，是最根本的解决方案，同时提升代码复用性。

### 4. 方案 4：使用 @PostConstruct 手动注入（灵活，适配特殊场景）

通过 `@PostConstruct` 注解，在 Bean 初始化完成后手动注入依赖，避免构造器或自动注入的循环。

#### 适用场景：无法修改构造器 / Setter，或依赖注入逻辑复杂。

#### 代码示例：

```java
// A 类
@Component
public class AService {
    // 不直接自动注入，先声明
    private BService bService;

    // 注入 ApplicationContext（用于手动获取 Bean）
    @Autowired
    private ApplicationContext applicationContext;

    // Bean 初始化完成后执行（此时 A 已初始化，可安全获取 B）
    @PostConstruct
    public void init() {
        this.bService = applicationContext.getBean(BService.class);
    }
}

// B 类
@Component
public class BService {
    private AService aService;

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        this.aService = applicationContext.getBean(AService.class);
    }
}
```

#### 原理：`@PostConstruct` 方法在 Bean 初始化（构造器执行后）触发，此时 Bean 已被 Spring 管理，可通过 `ApplicationContext` 安全获取依赖的 Bean。

### 5. 方案 5：使用 @DependsOn 指定初始化顺序（适配顺序敏感场景）

通过 `@DependsOn` 注解强制指定 Bean 的初始化顺序，让其中一个 Bean 先完全初始化，再初始化另一个。

#### 适用场景：循环依赖是因为 “初始化顺序导致”，且其中一个 Bean 可独立初始化。

#### 代码示例（指定 A 先于 B 初始化）：


```java
// A 类：无特殊注解，正常初始化
@Component
public class AService {
    @Autowired
    private BService bService; // 此时 B 已被 @DependsOn 触发初始化
}

// B 类：指定依赖 A，A 先初始化
@Component
@DependsOn("aService") // 括号内是 A 的 Bean 名称（默认类名首字母小写）
public class BService {
    @Autowired
    private AService aService;
}
```

#### 注意：需确保被依赖的 Bean 可独立初始化（无反向依赖的初始化依赖），否则可能失效。

### 6. 方案 6：手动暴露 Bean 到三级缓存（进阶，适配自定义 Bean）

通过实现 `BeanFactoryPostProcessor` 接口，手动将其中一个 Bean 暴露到 Spring 三级缓存，适用于自定义 Bean 或特殊场景。

#### 代码示例：

```java
// 自定义 Bean 工厂后置处理器，手动暴露 AService 到三级缓存
@Component
public class CycleDependencyResolver implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 获取 AService 的 Bean 定义
        BeanDefinition aDefinition = beanFactory.getBeanDefinition("aService");
        // 手动设置 Bean 的暴露方式，支持循环依赖
        ((AbstractBeanDefinition) aDefinition).setAllowCircularReferences(true);
    }
}
```

#### 注意：仅适用于高级场景，一般不推荐（Spring 已默认支持单例 Bean 的暴露）。

## 三、特殊场景：非单例 Bean 的循环依赖解决

非单例 Bean（`@Scope("prototype")`）Spring 无法默认解决（每次获取都是新实例，三级缓存无效），需额外处理：

1. 优先将非单例 Bean 改为单例（多数业务场景下，Bean 无需非单例）；
2. 若必须非单例：通过 `ApplicationContext.getBean()` 手动获取依赖（而非自动注入），在使用时才获取，避免初始化时循环。

#### 代码示例（非单例 Bean 手动获取依赖）：

```java
@Component
@Scope("prototype")
public class AService {
    private BService bService;

    @Autowired
    private ApplicationContext applicationContext;

    // 使用时才获取 B（非单例），避免初始化循环
    public void doSomething() {
        this.bService = applicationContext.getBean(BService.class);
        bService.execute();
    }
}

@Component
@Scope("prototype")
public class BService {
    // 同理，使用时手动获取 A
}
```

## 四、避坑指南：常见错误与最佳实践

1. **避免构造器注入循环依赖**：Spring 无法默认解决，优先用 Setter / 字段注入，或加 `@Lazy`；
2. **非单例 Bean 尽量避免循环依赖**：设计上优先拆分，或手动获取依赖；
3. **不要过度依赖 @Lazy**：`@Lazy` 是 “治标”，复杂场景优先用 “抽取公共 Bean” 从设计上解决；
4. **排查工具**：若不确定循环依赖来源，可开启 Spring debug 日志（`logging.level.org.springframework=DEBUG`），日志会打印循环依赖的 Bean 名称和依赖链。

## 面试总结（直接背）

SpringBoot 循环依赖解决核心：

1. 单例 Bean 循环依赖：Spring 默认通过三级缓存解决；构造器注入的单例用 `@Lazy` 或改 Setter 注入；
2. 非单例 Bean 循环依赖：改为单例，或手动通过 `ApplicationContext` 获取依赖；
3. 最佳实践：优先用 `@Lazy`（简单场景）或抽取公共 Bean（复杂场景），从设计上避免循环依赖。

简单说：解决循环依赖的核心是 “打破循环链”—— 要么延迟初始化，要么改变依赖方式，要么从设计上消除循环。
