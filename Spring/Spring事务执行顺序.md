要搞懂 `@Transactional`（事务）和 Spring AOP 切面日志的执行顺序，核心是理解 **Spring AOP 的代理机制** 和 **事务切面的特殊优先级**—— 事务本质是 Spring 内置的一个「特殊切面」，而自定义日志切面是用户自定义切面，两者的执行顺序由「切面优先级」决定，且事务回滚时的顺序是「正向执行的逆序」。

### 一、核心前提：Spring AOP 与事务的底层逻辑

1. **AOP 本质是动态代理**：Spring 对被 `@Transactional` 或自定义切面注解的方法，会创建「代理对象」。方法调用时，先执行代理对象的「切面链」（多个切面按优先级排序），最后执行目标方法（业务逻辑）。

2. 事务是 Spring 内置切面 ：```@Transactional```由 Spring 核心模块（```spring-tx```）提供，其切面类为```TransactionInterceptor```

，作用是：

- 正向执行：开启事务 → 执行其他切面 → 执行目标方法 → 提交事务；
- 异常回滚：目标方法抛出异常 → 触发事务回滚 → 执行其他切面的异常通知 → 向上抛出异常。

3. 切面优先级规则 ：

    - 用 `@Order` 注解（或 `Ordered` 接口）指定优先级，`value` 越小，优先级越高（越先执行）；
    - 若未指定 `@Order`，Spring 对「自定义切面」和「事务切面」的默认优先级：**事务切面优先级更低（`Order` 默认为 `Ordered.LOWEST_PRECEDENCE`，即最小优先级）**，意味着自定义日志切面会先于事务切面执行。

### 二、正常执行（无异常，事务提交）的顺序

假设场景：

- 目标方法 `method()` 加了 `@Transactional`；
- 自定义日志切面 `LogAspect`（无 `@Order` 或 `@Order(1)`，优先级高于事务切面），包含 4 个通知：`@Before`（前置）、`@After`（最终）、`@AfterReturning`（返回后）、`@AfterThrowing`（异常后）。

#### 完整执行链路（从代理对象到目标方法）：



```plaintext
1. 代理对象接收方法调用
2. 执行【日志切面 @Before】→ 记录方法开始（如"方法 method() 执行开始"）
3. 执行【事务切面 @Before】→ 开启事务（获取数据库连接、设置事务隔离级别等）
4. 执行【目标方法】→ 业务逻辑（如库存扣减、订单创建）
5. 执行【事务切面 @AfterReturning】→ 提交事务（执行 SQL COMMIT）
6. 执行【日志切面 @AfterReturning】→ 记录方法成功返回（如"方法 method() 执行成功，返回值：xxx"）
7. 执行【日志切面 @After】→ 记录方法结束（如"方法 method() 执行完毕"）
8. 代理对象返回结果
```

#### 关键结论：

- 前置通知（`@Before`）：自定义日志切面 → 事务切面（先日志，后开启事务）；
- 返回通知（`@AfterReturning`）：事务切面（提交） → 自定义日志切面（记录成功）；
- 最终通知（`@After`）：在返回通知之后执行（无论成功失败都会执行）；
- 核心逻辑：**事务的「开启」在日志前置之后，「提交」在日志返回之前**，确保日志能完整记录「事务开启→业务执行→事务提交」的全流程。

### 三、异常执行（触发事务回滚）的顺序

假设场景：目标方法执行中抛出 `RuntimeException`（默认 `@Transactional` 仅回滚运行时异常），其他条件同上。

#### 完整执行链路：


```plaintext
1. 代理对象接收方法调用
2. 执行【日志切面 @Before】→ 记录方法开始
3. 执行【事务切面 @Before】→ 开启事务
4. 执行【目标方法】→ 业务逻辑执行中抛出异常（如库存不足）
5. 执行【事务切面 @AfterThrowing】→ 触发事务回滚（执行 SQL ROLLBACK）
6. 执行【日志切面 @AfterThrowing】→ 记录方法异常（如"方法 method() 执行失败，异常：xxx"）
7. 执行【日志切面 @After】→ 记录方法结束
8. 代理对象向上抛出异常（给调用方）
```

#### 关键结论：

- 异常通知（`@AfterThrowing`）：事务切面（回滚） → 自定义日志切面（记录异常）；
- 最终通知（`@After`）：仍会执行（在异常通知之后），确保日志能记录「方法执行结束」状态；
- 核心逻辑：**事务回滚优先于日志异常通知**，日志能准确记录「事务回滚」的结果（比如日志中可体现 “事务已回滚，原因：xxx”）。

### 四、切面优先级对顺序的影响（自定义切面优先级低于事务）

如果自定义日志切面显式设置低优先级（如 `@Order(Ordered.LOWEST_PRECEDENCE - 1)`，仍低于事务的默认优先级），执行顺序会反转：

#### 正常执行：




```plaintext
1. 代理对象调用
2. 事务切面 @Before（开启事务）
3. 日志切面 @Before（记录开始）
4. 目标方法
5. 日志切面 @AfterReturning（记录成功）
6. 事务切面 @AfterReturning（提交事务）
7. 日志切面 @After（记录结束）
```

#### 异常执行：









```plaintext
1. 代理对象调用
2. 事务切面 @Before（开启事务）
3. 日志切面 @Before（记录开始）
4. 目标方法抛出异常
5. 日志切面 @AfterThrowing（记录异常）
6. 事务切面 @AfterThrowing（回滚事务）
7. 日志切面 @After（记录结束）
```

#### 问题：

这种顺序会导致日志无法完整记录事务状态（比如日志前置通知执行时，事务还未开启；日志返回通知执行时，事务还未提交），**不推荐**。因此，自定义日志切面建议设置高于事务的优先级（`@Order` 数值更小，如 `@Order(1)`）。

### 五、关键细节补充

1. **通知类型的执行顺序规则**：无论是否有事务，Spring AOP 通知的基础执行顺序是：

    - 正常：`@Before` → 目标方法 → `@AfterReturning` → `@After`；

    - 异常：```@Before```→ 目标方法 →```@AfterThrowing```→```@After```； 事务切面本质是「嵌入」在这个基础顺序中的特殊切面，优先级决定其在切面链中的位置。

2. **事务切面的特殊逻辑**：

    - 事务切面只关注「目标方法是否抛出回滚异常」，回滚操作仅在 `@AfterThrowing` 中执行；
    - 事务的「开启」和「提交 / 回滚」是原子性的，不会被其他切面打断。

3. **多自定义切面的顺序**：若有多个自定义切面（如日志切面、权限切面），按 `@Order` 数值从小到大执行：

    - 前置通知：`Order(1)` 切面 → `Order(2)` 切面 → 事务切面；
    - 返回 / 异常通知：事务切面 → `Order(2)` 切面 → `Order(1)` 切面（逆序）。

4. **为什么事务切面默认优先级最低？**：Spring 设计的核心思想是「事务要包裹业务逻辑和其他切面」—— 确保其他切面（如日志、权限校验）的执行都在事务范围内（比如权限校验失败抛出异常，也会触发事务回滚），避免出现 “切面执行成功但事务回滚” 的不一致状态。

### 六、代码验证（直观感受顺序）

#### 1. 自定义日志切面（高优先级）：


```java
@Aspect
@Component
@Order(1)  // 优先级高于事务切面（默认最低）
public class LogAspect {

    @Before("execution(* com.example.service.OrderService.createOrder(..))")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("【日志切面】@Before：方法开始执行，参数：" + Arrays.toString(joinPoint.getArgs()));
    }

    @AfterReturning(pointcut = "execution(* com.example.service.OrderService.createOrder(..))", returning = "result")
    public void logAfterReturning(Object result) {
        System.out.println("【日志切面】@AfterReturning：方法执行成功，返回值：" + result);
    }

    @AfterThrowing(pointcut = "execution(* com.example.service.OrderService.createOrder(..))", throwing = "e")
    public void logAfterThrowing(Throwable e) {
        System.out.println("【日志切面】@AfterThrowing：方法执行失败，异常：" + e.getMessage());
    }

    @After("execution(* com.example.service.OrderService.createOrder(..))")
    public void logAfter() {
        System.out.println("【日志切面】@After：方法执行结束");
    }
}
```

#### 2. 目标方法（带事务）：




```java
@Service
public class OrderService {

    @Transactional
    public String createOrder(Integer ticketId, Integer buyNum) {
        System.out.println("【目标方法】执行订单创建业务（库存扣减、订单入库）");
        // 模拟正常执行（注释掉则触发异常）
        // if (buyNum > 10) {
        //     throw new RuntimeException("库存不足");
        // }
        return "订单创建成功：" + System.currentTimeMillis();
    }
}
```

#### 3. 正常执行输出（顺序对应前文）：








```plaintext
【日志切面】@Before：方法开始执行，参数：[1, 2]
【事务切面】@Before：开启事务（Spring 内部日志，需开启 debug 日志可见）
【目标方法】执行订单创建业务（库存扣减、订单入库）
【事务切面】@AfterReturning：提交事务（Spring 内部日志）
【日志切面】@AfterReturning：方法执行成功，返回值：订单创建成功：1699999999999
【日志切面】@After：方法执行结束
```

#### 4. 异常执行输出（模拟库存不足）：









```plaintext
【日志切面】@Before：方法开始执行，参数：[1, 20]
【事务切面】@Before：开启事务
【目标方法】执行订单创建业务（库存扣减、订单入库）
【事务切面】@AfterThrowing：回滚事务（Spring 内部日志）
【日志切面】@AfterThrowing：方法执行失败，异常：库存不足
【日志切面】@After：方法执行结束
```

### 七、总结

#### 核心执行顺序（默认优先级：自定义日志切面 > 事务切面）：

| 场景     | 执行顺序                                                     |
| -------- | ------------------------------------------------------------ |
| 正常执行 | 日志 @Before → 事务开启 → 目标方法 → 事务提交 → 日志 @AfterReturning → 日志 @After |
| 事务回滚 | 日志 @Before → 事务开启 → 目标方法（抛异常） → 事务回滚 → 日志 @AfterThrowing → 日志 @After |

#### 关键原则：

1. 切面优先级：`@Order` 数值越小，越先执行，前置通知正序，返回 / 异常通知逆序；
2. 事务切面默认优先级最低，确保事务包裹所有业务逻辑和自定义切面；
3. 事务回滚时，顺序是正向执行的逆序，回滚操作优先于自定义切面的异常通知。

#### 实践建议：

- 自定义日志切面建议设置 `@Order(1~10)`（高于事务），确保日志能记录事务的开启 / 提交 / 回滚状态；
- 若需让某些切面（如权限校验）在事务开启前执行，也需设置更高优先级；
- 开启 Spring 事务的 debug 日志（`logging.level.org.springframework.transaction.interceptor=DEBUG`），可直观看到事务切面的执行时机。