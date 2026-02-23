核心结论：**Spring 拦截器（Interceptor）先执行，切面（AOP）后执行**—— 请求链路的执行顺序是：`请求进入容器 → 拦截器前置方法 → 切面通知（如@Before） → 目标方法执行 → 切面通知（如@AfterReturning） → 拦截器后置方法 → 响应返回`。

关键原因：两者的执行层级和触发机制完全不同，拦截器是「Servlet 容器层面的请求拦截」，切面是「Spring 容器层面的方法增强」，从请求流程来看，拦截器更早介入。

### 一、核心区别：执行层级决定先后顺序

要理解 “谁先执行”，首先要明确两者的执行位置和触发逻辑：

| 特性     | 拦截器（Interceptor）                           | 切面（AOP）                                                  |
| -------- | ----------------------------------------------- | ------------------------------------------------------------ |
| 所属层面 | Servlet 容器（Spring MVC 组件）                 | Spring IoC 容器（AOP 框架组件）                              |
| 触发时机 | 围绕「HTTP 请求生命周期」（请求到达、响应返回） | 围绕「目标方法生命周期」（方法调用前、调用后）               |
| 拦截范围 | 仅拦截 Controller 的请求（如 `/api/user` 接口） | 可拦截所有 Spring 管理的 Bean 的方法（Controller、Service、Dao 等） |
| 底层实现 | 基于 Java 接口回调（`HandlerInterceptor`）      | 基于动态代理（JDK 代理 / CGLIB）+ 字节码增强                 |

简单类比：

- 拦截器像 “小区大门保安”：请求（访客）进入小区（应用）时，先经过保安（拦截器）检查，再进入楼栋（Controller）；
- 切面像 “楼栋门口的快递柜”：访客（方法调用）进入楼栋后，要取快递（执行增强逻辑）才会到家门口（目标方法）。

因此，从请求流程来看，拦截器的前置方法必然先于切面的前置通知执行。

### 二、详细执行流程（以 Controller 接口请求为例）

完整的请求链路顺序，清晰体现两者的执行先后：

1. 客户端发送 HTTP 请求，经过 Tomcat 等 Servlet 容器，进入 Spring MVC 框架；
2. **拦截器前置方法（`preHandle`）执行**：检查请求参数、登录状态等（如未登录则直接返回响应，中断流程）；
3. Spring MVC 找到对应的 Controller 方法，准备调用；
4. **切面前置通知（`@Before`）执行**：如日志记录、权限校验等增强逻辑；
5. **目标方法执行**：Controller 方法本身的业务逻辑（如查询数据、返回结果）；
6. **切面后置通知（`@After`）/ 返回通知（`@AfterReturning`）执行**：如清理资源、统计方法耗时；
7. **拦截器后置方法（`postHandle`）执行**：可修改响应数据、设置响应头；
8. **拦截器完成方法（`afterCompletion`）执行**：无论请求成功失败，都会执行（如释放资源、记录请求结束日志）；
9. 响应结果通过 Servlet 容器返回给客户端。

**关键节点**：第 2 步（拦截器前置）早于第 4 步（切面前置），第 6 步（切面后置）早于第 7 步（拦截器后置）—— 拦截器先介入、后收尾，切面在中间对目标方法进行增强。

### 三、代码验证：直观看到执行顺序

通过简单代码打印日志，可直接验证顺序：

#### 1. 自定义拦截器（打印执行时机）



```java
@Component
public class MyInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("1. 拦截器前置方法（preHandle）执行");
        return true; // 返回 true 才会继续后续流程
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        System.out.println("4. 拦截器后置方法（postHandle）执行");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        System.out.println("5. 拦截器完成方法（afterCompletion）执行");
    }
}
```

#### 2. 自定义切面（打印执行时机）





```java
@Aspect
@Component
public class MyAspect {
    // 切入点：拦截所有 Controller 方法
    @Pointcut("execution(* com.example.demo.controller.*.*(..))")
    public void controllerPointcut() {}

    @Before("controllerPointcut()")
    public void before() {
        System.out.println("2. 切面前置通知（@Before）执行");
    }

    @AfterReturning("controllerPointcut()")
    public void afterReturning() {
        System.out.println("3. 切面返回通知（@AfterReturning）执行");
    }
}
```

#### 3. Controller 目标方法





```java
@RestController
@RequestMapping("/test")
public class TestController {
    @GetMapping("/demo")
    public String demo() {
        System.out.println("3. 目标方法（demo）执行");
        return "success";
    }
}
```

#### 4. 执行结果（控制台输出顺序）






```plaintext
1. 拦截器前置方法（preHandle）执行
2. 切面前置通知（@Before）执行
3. 目标方法（demo）执行
4. 切面返回通知（@AfterReturning）执行
5. 拦截器后置方法（postHandle）执行
6. 拦截器完成方法（afterCompletion）执行
```

结果完全符合 “拦截器先执行，切面后执行” 的结论。

### 四、特殊场景：是否有例外？

只有一种情况可能改变顺序 ——**切面拦截的是拦截器本身的方法**（而非 Controller 方法），但这种场景几乎不会用到（拦截器通常是框架组件，不会作为 AOP 增强的目标）。

正常业务场景下（拦截器拦截 Controller 请求，切面增强 Controller/Service 方法），必然是「拦截器先执行，切面后执行」。

### 面试总结（直接背）

Spring 拦截器先执行，切面后执行，核心逻辑：

1. 拦截器是 Servlet 容器层面的请求拦截，在 HTTP 请求到达 Controller 前触发；
2. 切面是 Spring 容器层面的方法增强，在 Controller/Service 方法调用前触发；
3. 执行顺序：拦截器 preHandle → 切面 @Before → 目标方法 → 切面 @AfterReturning → 拦截器 postHandle → 拦截器 afterCompletion。

简单说：请求先过 “大门保安”（拦截器），再到 “楼栋快递柜”（切面），最后到 “家门口”（目标方法）
