### 3. 对 Spring AOP 的理解

Spring AOP（Aspect-Oriented Programming，面向切面编程）是 Spring 框架的核心特性之一，旨在通过**横切关注点分离**解决传统 OOP 中 “代码冗余” 和 “业务逻辑与非业务逻辑耦合” 的问题（如日志、事务、权限校验等）。

#### （1）核心概念

- **切面（Aspect）**：封装横切逻辑的类（如日志切面、事务切面）；
- **连接点（JoinPoint）**：程序执行过程中的特定点（如方法调用、异常抛出）；
- **切入点（Pointcut）**：匹配连接点的规则（如指定哪些方法需要被切面增强）；
- **通知（Advice）**：切面的具体增强逻辑（如 @Before、@After、@Around 等）；
- **目标对象（Target）**：被切面增强的原始对象；
- **代理（Proxy）**：Spring AOP 通过动态代理生成的增强后的对象。

#### （2）实现方式

Spring AOP 基于**动态代理**实现，分为两种：

- **JDK 动态代理**：针对实现接口的类，通过反射生成接口的代理类；
- **CGLIB 代理**：针对未实现接口的类，通过继承生成子类代理（需开启`proxyTargetClass=true`）。

#### （3）与 AspectJ 的关系

Spring AOP**集成了 AspectJ 的注解（如 @Aspect、@Pointcut）和 API**，但底层实现与原生 AspectJ 完全不同：

- **Spring AOP**：运行时动态代理，仅支持方法级别的连接点，轻量但功能有限；

- 原生 AspectJ

  ：编译期 / 加载期织入（Weaving），支持字段、构造器等更多连接点，织入时机更早，效率更高（无运行时代理开销）。



Spring AOP 并非 “替代” AspectJ，而是简化了 AOP 的使用，若需更强大的 AOP 能力（如编译期增强），可直接使用原生 AspectJ。

### 4. 如何控制切面执行顺序

当存在多个切面同时作用于同一个连接点时，需通过以下方式控制切面的执行顺序，以及切面内部通知的执行顺序：

#### （1）控制不同切面的执行顺序

##### 方式 1：使用`@Order`注解

在切面类上添加`@Order(n)`，`n`为整数（**数值越小，切面越先执行**）。示例：





```java
// 日志切面（Order=1，先执行）
@Aspect
@Order(1)
@Component
public class LogAspect {
    @Before("execution(* com.service.*.*(..))")
    public void before(JoinPoint joinPoint) {
        System.out.println("日志切面：方法执行前");
    }
}

// 权限切面（Order=2，后执行）
@Aspect
@Order(2)
@Component
public class AuthAspect {
    @Before("execution(* com.service.*.*(..))")
    public void before(JoinPoint joinPoint) {
        System.out.println("权限切面：方法执行前");
    }
}
```

执行结果：先打印 “日志切面”，再打印 “权限切面”。

##### 方式 2：实现`Ordered`接口

让切面类实现`Ordered`接口，重写`getOrder()`方法返回顺序值（同样数值越小越先执行）。示例：





```java
@Aspect
@Component
public class LogAspect implements Ordered {
    @Override
    public int getOrder() {
        return 1; // 优先级更高
    }
    
    @Before("execution(* com.service.*.*(..))")
    public void before(JoinPoint joinPoint) {
        // ...
    }
}
```

##### 方式 3：XML 配置（Spring XML 时代）

在 XML 中通过``指定`order`属性。

#### （2）控制切面内部通知的执行顺序

同一切面内不同类型通知的执行顺序是固定的，遵循以下规则：

1. **@Around**：优先于其他通知执行（`proceed()`方法前的逻辑→@Before→目标方法→`proceed()`后的逻辑→@After→@AfterReturning/@AfterThrowing）；
2. **@Before**：按定义顺序执行（若同一切面内有多个 @Before，写在前面的先执行）；
3. **@After**：最终执行（无论目标方法是否异常）；
4. **@AfterReturning**和 **@AfterThrowing**：互斥（正常返回执行前者，异常执行后者），且在 @After 之后。

示例（同一切面内）：






```java
@Aspect
@Component
public class MyAspect {
    @Before("execution(* com.service.*.*(..))")
    public void before1() { System.out.println("Before1"); }

    @Before("execution(* com.service.*.*(..))")
    public void before2() { System.out.println("Before2"); }

    @Around("execution(* com.service.*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("Around前");
        Object result = pjp.proceed();
        System.out.println("Around后");
        return result;
    }

    @After("execution(* com.service.*.*(..))")
    public void after() { System.out.println("After"); }

    @AfterReturning("execution(* com.service.*.*(..))")
    public void afterReturning() { System.out.println("AfterReturning"); }
}
```

执行结果：




```plaintext
Around前 → Before1 → Before2 → 目标方法 → Around后 → After → AfterReturning
```

#### （3）特殊场景：嵌套切面的执行顺序

若切面 A 的 Order=1，切面 B 的 Order=2，执行顺序为：







```plaintext
A的@Around前 → A的@Before → B的@Around前 → B的@Before → 目标方法 → B的@Around后 → B的@After → A的@Around后 → A的@After → A/B的AfterReturning
```

（即 “外层切面先执行前置逻辑，后执行后置逻辑”，类似 “洋葱模型”）

### 总结

控制切面执行顺序的核心是通过`@Order`或`Ordered`接口指定优先级（数值越小越先执行）；同一切面内通知的顺序由 Spring AOP 的生命周期固定，无需手动控制。实际项目中，建议通过`@Order`明确切面优先级，避免因切面顺序混乱导致的逻辑错误（如事务切面需晚于日志切面执行）。