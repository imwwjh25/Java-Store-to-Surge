### 二、Spring AOP 失效的常见场景及原因

AOP 失效的本质是：**调用方法时，没有走代理对象，而是直接调用了原始目标对象的方法**。以下是最常见的失效场景：

#### 1. 内部方法调用（最常见）

**场景**：目标对象的方法 A 调用自身的方法 B，而方法 B 是切入点（有增强逻辑），此时方法 B 的增强不会生效。**原因**：内部调用是`this.methodB()`，`this`指向原始目标对象，而非代理对象。**示例代码**：



```java
@Service
public class UserService {
    // 方法A
    public void addUser() {
        System.out.println("添加用户");
        // 内部调用方法B，AOP失效
        this.updateUserStatus(); 
    }

    // 方法B（切入点，配置了日志增强）
    @LogAnnotation // 自定义的切面注解
    public void updateUserStatus() {
        System.out.println("更新用户状态");
    }
}
```

**验证**：调用`userService.addUser()`时，`updateUserStatus()`的日志增强不会执行。

#### 2. 目标对象不是 Spring 容器管理的 Bean

**场景**：手动`new`创建的对象（而非通过`@Autowired`/`@Resource`注入，或`ApplicationContext.getBean()`获取），AOP 不会生效。**原因**：Spring AOP 只对容器内的 Bean 生成代理对象，手动 new 的对象不受容器管理，自然不会被代理。**示例**：





```java
// 错误方式：手动new，AOP失效
UserService userService = new UserService();
userService.updateUserStatus();

// 正确方式：从容器获取，AOP生效
@Autowired
private UserService userService;
```

#### 3. 切入点表达式错误

**场景**：切面的`@Pointcut`表达式写错（如包名、方法名、注解匹配错误），导致无法匹配到目标方法。**原因**：切入点匹配失败，增强逻辑不会被触发。**示例**：





```java
// 错误：目标方法在com.example.service包，但切入点写了com.example.dao
@Pointcut("execution(* com.example.dao.*.*(..))")
// 正确：匹配com.example.service包下所有类的所有方法
@Pointcut("execution(* com.example.service.*.*(..))")
```

#### 4. 目标方法是 final/static/private 修饰

**场景**：目标方法被`final`/`static`/`private`修饰，AOP 增强失效。**原因**：

- `final`方法：CGLIB 基于继承实现代理，无法覆盖 final 方法；
- `static`方法：不属于对象实例，动态代理无法代理静态方法；
- `private`方法：无法被外部访问，代理对象无法调用（也无法被覆盖）。

#### 5. 代理对象被手动解包（获取原始对象）

**场景**：通过`AopContext.currentProxy()`或其他方式强行获取原始目标对象，调用其方法。**示例**：




```java
@Service
public class UserService {
    @LogAnnotation
    public void updateUserStatus() {
        // 手动获取原始对象，调用方法导致AOP失效
        UserService rawService = (UserService) AopContext.currentProxy();
        rawService.doSomething(); 
    }
}
```

#### 6. 异步 / 事务等特殊场景的代理冲突

**场景**：同一目标对象同时被多个代理（如`@Transactional`和自定义 AOP），可能导致其中一个 AOP 失效。**原因**：Spring 的代理是 “嵌套代理”，如果代理顺序配置不当，可能导致内层代理的增强不生效。

### 三、AOP 失效的解决方案

针对最常见的失效场景，给出具体解决方案：

#### 1. 解决内部方法调用失效（核心方案）

**方案 1：通过 Spring 容器获取自身代理对象**





```java
@Service
public class UserService {
    // 注入自身（容器返回的是代理对象）
    @Autowired
    private UserService selfProxy;

    public void addUser() {
        System.out.println("添加用户");
        // 调用代理对象的方法，AOP生效
        selfProxy.updateUserStatus();
    }

    @LogAnnotation
    public void updateUserStatus() {
        System.out.println("更新用户状态");
    }
}
```

**方案 2：使用 AopContext 获取当前代理对象**需要先在启动类添加`@EnableAspectJAutoProxy(exposeProxy = true)`：





```java
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true) // 暴露代理对象
public class AppApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }
}

@Service
public class UserService {
    public void addUser() {
        System.out.println("添加用户");
        // 获取当前代理对象
        UserService proxy = (UserService) AopContext.currentProxy();
        proxy.updateUserStatus();
    }

    @LogAnnotation
    public void updateUserStatus() {
        System.out.println("更新用户状态");
    }
}
```

#### 2. 其他场景的解决方案

- **非容器 Bean**：确保所有目标对象都通过`@Service`/`@Component`等注解交给 Spring 管理，避免手动`new`；
- **切入点错误**：使用`@Pointcut`时，通过 IDE 的语法提示校验，或打印切入点匹配日志排查；
- **final/static/private 方法**：避免对这类方法配置 AOP，改为对 public 非 final 方法增强；
- **代理冲突**：通过`@Order`注解指定切面的执行顺序（数值越小，优先级越高）。

### 总结

1. **Spring AOP 核心原理**：基于动态代理（JDK/CGLIB），对容器内的 Bean 生成代理对象，在调用目标方法时插入增强逻辑；
2. **AOP 失效核心原因**：调用方法时未走代理对象，直接调用原始对象；
3. **最常见失效场景**：内部方法调用、目标对象非 Spring Bean、目标方法为 final/static/private 修饰，其中内部调用可通过注入自身代理对象或`AopContext`解决。