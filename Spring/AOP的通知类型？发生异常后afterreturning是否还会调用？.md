AOP 的通知类型是 Spring AOP 的核心考点，而 “异常后`afterReturning`是否调用” 则是高频易错点，我会结合 “通知类型定义 + 执行顺序 + 异常影响” 清晰梳理，帮你彻底搞懂：

### 一、AOP 的 5 种核心通知类型（Spring AOP 规范）



AOP 的 “通知（Advice）” 是指 “切面中要执行的增强逻辑”，5 种类型对应不同的执行时机，核心区别在于**何时触发增强代码**：

| 通知类型                        | 执行时机                                                     | 核心作用                                                     |
| ------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| `@Before`（前置通知）           | 目标方法**执行之前**触发                                     | 做准备工作（比如参数校验、日志打印、权限检查）               |
| `@AfterReturning`（返回后通知） | 目标方法**正常执行完成后**触发（无异常抛出）                 | 处理返回结果（比如结果格式化、日志记录返回值）               |
| `@AfterThrowing`（异常后通知）  | 目标方法**抛出异常后**触发（执行失败）                       | 异常处理（比如记录异常日志、告警、数据回滚）                 |
| `@After`（最终通知）            | 目标方法**无论是否正常执行**（成功 / 失败 / 抛异常），最终都会触发 | 资源清理（比如关闭流、释放锁、关闭数据库连接）—— 类似`try-catch-finally`的`finally` |
| `@Around`（环绕通知）           | 包裹目标方法，**在目标方法执行前后都能触发**，还能控制目标方法是否执行 | 全能通知（可替代其他 4 种），比如性能监控（统计方法执行时间）、事务管理 |

#### 关键补充：



- 除了注解方式，Spring 也支持 XML 配置通知，但面试 / 工作中**注解方式（`@AspectJ`）是主流**；
- 环绕通知是最灵活的，因为它能直接获取目标方法的`ProceedingJoinPoint`对象，通过`proceed()`方法触发目标方法执行，甚至可以不调用`proceed()`直接返回结果（跳过目标方法）。

### 二、核心问题：发生异常后，`afterReturning`是否还会调用？



#### 结论：不会调用！



#### 底层逻辑：`afterReturning`的触发条件是 “目标方法正常返回”，异常会直接阻断其执行，具体可结合 “通知执行顺序 + 异常影响” 理解：



#### 1. 先明确：正常执行（无异常）时的通知顺序



环绕通知是 “包裹式”，其他通知按以下顺序执行：`@Around`（前置部分） → `@Before` → 目标方法 → `@Around`（后置部分） → `@AfterReturning` → `@After`

示例流程（目标方法无异常）：

java

运行

```
@Around("pointcut()")
public Object aroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
    System.out.println("环绕通知：目标方法执行前"); // 先执行
    Object result = joinPoint.proceed(); // 触发目标方法
    System.out.println("环绕通知：目标方法执行后"); // 目标方法正常返回后执行
    return result;
}

@Before("pointcut()")
public void beforeAdvice() {
    System.out.println("前置通知：目标方法执行前");
}

@AfterReturning("pointcut()")
public void afterReturningAdvice() {
    System.out.println("返回后通知：目标方法正常返回");
}

@After("pointcut()")
public void afterAdvice() {
    System.out.println("最终通知：无论成败都会执行");
}
```



输出顺序：

plaintext

```plaintext
环绕通知：目标方法执行前
前置通知：目标方法执行前
目标方法执行（无异常）
环绕通知：目标方法执行后
返回后通知：目标方法正常返回
最终通知：无论成败都会执行
```



#### 2. 发生异常时的通知顺序（关键！）



当目标方法抛出异常时：

- `joinPoint.proceed()`会抛出异常，环绕通知的 “后置部分”（`proceed()`之后的代码）不会执行；
- `@AfterReturning`因 “目标方法未正常返回”，直接不触发；
- `@AfterThrowing`会触发（捕获到异常）；
- `@After`仍会触发（最终通知，类似`finally`）。

异常时流程输出：

plaintext

```plaintext
环绕通知：目标方法执行前
前置通知：目标方法执行前
目标方法执行（抛出异常）
异常后通知：目标方法抛出异常
最终通知：无论成败都会执行
// 注意：环绕通知的“后置部分”和@AfterReturning都未执行
```



#### 3. 特殊情况：环绕通知捕获异常并处理



如果环绕通知中捕获了目标方法的异常（不往外抛），那么：

- 目标方法的异常被 “消化”，视为 “正常执行”；
- `@AfterReturning`会触发（因为环绕通知返回了结果，目标方法未向外抛异常）；
- `@AfterThrowing`不会触发（无未捕获的异常）。

示例（环绕通知捕获异常）：

java

运行

```
@Around("pointcut()")
public Object aroundAdvice(ProceedingJoinPoint joinPoint) {
    System.out.println("环绕通知：目标方法执行前");
    Object result = null;
    try {
        result = joinPoint.proceed(); // 目标方法抛异常
    } catch (Throwable e) {
        System.out.println("环绕通知：捕获到异常，处理之");
        result = "默认结果"; // 手动返回默认结果
    }
    System.out.println("环绕通知：目标方法执行后（异常已处理）");
    return result;
}
```



输出顺序：

plaintext

```plaintext
环绕通知：目标方法执行前
前置通知：目标方法执行前
目标方法执行（抛出异常）
环绕通知：捕获到异常，处理之
环绕通知：目标方法执行后（异常已处理）
返回后通知：目标方法正常返回（因为异常被处理，视为正常）
最终通知：无论成败都会执行
// 注意：@AfterThrowing未触发，@AfterReturning触发了
```



### 三、核心总结（面试直接答）



1. AOP 的 5 种通知类型：`@Before`（前置）、`@AfterReturning`（返回后）、`@AfterThrowing`（异常后）、`@After`（最终）、`@Around`（环绕）；

2. 异常后

   ```
   @AfterReturning
   ```

   是否调用？→ 分两种情况：

   - 目标方法抛出的异常**未被捕获**（比如没写`try-catch`，或异常抛到切面外）：`@AfterReturning`不调用，`@AfterThrowing`和`@After`会调用；
   - 异常被**环绕通知捕获并处理**（未向外抛出）：视为目标方法 “正常返回”，`@AfterReturning`会调用，`@AfterThrowing`不调用。

3. 记忆口诀：正常返回找`AfterReturning`，抛异常找`AfterThrowing`，无论成败都有`After`，环绕通知能控全流程。
