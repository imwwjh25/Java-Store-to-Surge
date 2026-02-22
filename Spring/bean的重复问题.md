### 一、核心概念：Bean 的 “名字” 是什么？

Spring 中 Bean 的唯一标识是「Bean Name」（默认是类名首字母小写，也可手动指定），比如：


```java
// 手动指定 Bean Name 为 "userService"
@Service("userService") 
public class UserServiceImpl implements UserService {}
```

“同名 Bean” 本质是「多个 Bean 的 Bean Name 完全相同」（如两个 Bean 都叫 "userService"）。

### 二、分场景：能否定义同名 Bean？

#### 场景 1：默认配置（未开启覆盖）—— 不允许，直接报错

Spring 容器的核心是「BeanDefinitionMap」（一个 HashMap），Key 是 Bean Name，Value 是 Bean 定义。默认情况下，若检测到同名 Bean 已存在，会抛出 `BeanDefinitionStoreException` 异常，提示「Bean name 'xxx' is already defined」。

**示例（报错场景）**：

```java
// 第一个同名 Bean
@Service("userService")
public class UserService1 implements UserService {}

// 第二个同名 Bean（默认会报错）
@Service("userService")
public class UserService2 implements UserService {}
```

#### 场景 2：开启 “覆盖模式”—— 允许，后定义的覆盖先定义的

Spring 提供了「允许 Bean 覆盖」的配置，开启后，后注册的同名 Bean 会覆盖先注册的，核心分 2 种配置方式：

##### 方式 1：XML 配置（传统方式）

在 Spring XML 配置文件中添加：
```xml
<!-- 开启 Bean 覆盖（默认 false，不允许） -->
<beans default-lazy-init="false" default-autowire="no" default-allow-bean-definition-overriding="true">
    <!-- 两个同名 Bean，后定义的覆盖先定义的 -->
    <bean id="userService" class="com.example.UserService1"/>
    <bean id="userService" class="com.example.UserService2"/>
</beans>
```

##### 方式 2：Spring Boot 配置（主流）

在 `application.properties/yaml` 中添加：


```properties
# Spring Boot 2.1+ 版本（核心配置）
spring.main.allow-bean-definition-overriding=true
```


```yaml
# YAML 格式
spring:
  main:
    allow-bean-definition-overriding: true
```

**示例（开启覆盖后）**：

```java
// 先注册的 Bean
@Service("userService")
public class UserService1 implements UserService {
    public String say() { return "Service1"; }
}

// 后注册的 Bean（会覆盖上面的）
@Service("userService")
public class UserService2 implements UserService {
    public String say() { return "Service2"; }
}

// 测试：注入的是 UserService2
@RestController
public class TestController {
    @Autowired
    private UserService userService;

    @GetMapping("/test")
    public String test() {
        return userService.say(); // 输出 "Service2"
    }
}
```

#### 场景 3：不同容器 / 不同上下文 —— 允许（互不影响）

如果是「父子容器」（如 Spring MVC 的 DispatcherServlet 容器 + 根容器），或「多个独立 Spring 容器」，同名 Bean 是允许的（因为各自维护自己的 BeanDefinitionMap）。比如：

- 根容器有一个 "userService"；

- MVC 子容器也有一个 "userService"；

  

  两者互不覆盖，子容器优先用自己的，找不到再找父容器的。

### 三、关键细节（避免踩坑）

#### 1. Spring 版本差异

- Spring Boot 2.1 之前：`allow-bean-definition-overriding` 默认是 `true`（允许覆盖，容易踩坑）；

- Spring Boot 2.1 及之后：默认改为

   

  ```
  false
  ```

  （禁止覆盖，更安全）；

  

  👉 这是最容易忘的点！面试 / 实战中一定要提版本差异。

#### 2. 覆盖的 “优先级”

Bean 注册的顺序决定覆盖关系：**后注册的覆盖先注册的**，注册顺序规则：

- 注解扫描（`@Component/@Service`）：按包扫描顺序（如 `com.example.a` 先于 `com.example.b`）；
- XML 配置：按配置文件中 Bean 的定义顺序；
- 手动注册（`@Bean` 方法）：按 `@Configuration` 类的加载顺序。

#### 3. 特殊情况：@Primary 注解（不是覆盖，是 “优先选择”）

`@Primary` 不是允许同名 Bean，而是解决「同类型多个不同名 Bean」的注入冲突：

```java
// 两个不同名、同类型的 Bean
@Service("userService1")
public class UserService1 implements UserService {}

@Service("userService2")
@Primary // 注入时优先选这个
public class UserService2 implements UserService {}

// 注入时不会报错，会选 UserService2
@Autowired
private UserService userService;
```

### 四、实战建议（什么时候用？）

1. **尽量避免同名 Bean**：覆盖逻辑容易导致线上问题（比如某个 Bean 被意外覆盖，排查困难）；

2. **必须用的场景**：比如多模块开发，不同模块定义了同名 Bean，且需要统一用最新的；

3. 替代方案

   ：

   - 用不同的 Bean Name（如 `userService-v1`/`userService-v2`）；

   - 用```@Qualifier```

    
     指定注入的 Bean 名称，避免依赖覆盖：
     ```java
     @Autowired
     @Qualifier("userService1") // 明确指定注入第一个
     private UserService userService;
     ```

     

### 五、核心总结

| 场景                         | 是否允许同名 Bean | 关键配置 / 注解                                   |
| ---------------------------- | ----------------- | ------------------------------------------------- |
| 默认配置（Spring Boot 2.1+） | 不允许            | 无（默认 allow=false）                            |
| 开启覆盖模式                 | 允许（后覆盖先）  | spring.main.allow-bean-definition-overriding=true |
| 父子容器 / 独立容器          | 允许（互不影响）  | 无                                                |
| 同类型不同名（@Primary）     | 允许（优先选择）  | @Primary + @Qualifier                             |

一句话记牢：**默认禁止同名 Bean，开启 allow-bean-definition-overriding 则允许后覆盖先，Spring Boot 2.1+ 默认关闭覆盖，2.1 前默认开启**。
