AOP（面向切面编程）的核心是 **“在不修改目标类代码的前提下，通过代理对象增强目标方法的功能”**（如日志、事务、权限控制）。Java 中 AOP 代理的实现方式主要分为 **2 大类**：**静态代理** 和 **动态代理**，其中动态代理又分为 **JDK 动态代理** 和 **CGLIB 动态代理**（Spring AOP 默认优先使用 JDK 动态代理， fallback 到 CGLIB）。

以下是每种代理方式的原理、实现、优缺点及适用场景，结合代码示例详细说明：

## 一、核心概念铺垫



在讲代理实现前，先明确 3 个核心角色：

- **目标对象（Target）**：被增强的原始对象（如业务类 `UserService`）；
- **代理对象（Proxy）**：对目标对象的包装，增强目标方法（如添加日志）；
- **切面（Aspect）**：增强的逻辑（如日志记录、事务控制），通过代理对象织入目标方法。

代理的核心逻辑：**客户端调用代理对象的方法 → 代理对象先执行切面逻辑 → 再调用目标对象的原始方法 → （可选）代理对象再执行后续切面逻辑**。

## 二、1. 静态代理（编译期生成代理类）



### 原理



静态代理是 **编译期就生成代理类的字节码**，代理类和目标类实现同一个接口（或继承同一个父类），在代理类中硬编码切面逻辑，直接调用目标对象的方法。

### 实现步骤



1. 定义公共接口（目标类和代理类共同实现）；
2. 实现目标类（原始业务逻辑）；
3. 实现代理类（实现同一接口，持有目标对象引用，在方法中织入切面逻辑）。

### 代码示例（日志增强）



#### 步骤 1：定义公共接口



java

运行

```
// 业务接口（目标类和代理类共同实现）
public interface UserService {
    void addUser(String username);
    void deleteUser(String username);
}
```



#### 步骤 2：实现目标类（原始业务）



java

运行

```
// 目标对象：原始业务逻辑
public class UserServiceImpl implements UserService {
    @Override
    public void addUser(String username) {
        System.out.println("核心业务：添加用户 " + username);
    }

    @Override
    public void deleteUser(String username) {
        System.out.println("核心业务：删除用户 " + username);
    }
}
```



#### 步骤 3：实现代理类（织入切面逻辑）



java

运行

```
// 静态代理类：实现同一接口，持有目标对象，织入日志切面
public class UserServiceStaticProxy implements UserService {
    // 持有目标对象引用
    private final UserService target;

    // 构造器注入目标对象
    public UserServiceStaticProxy(UserService target) {
        this.target = target;
    }

    @Override
    public void addUser(String username) {
        // 切面逻辑：方法执行前（日志记录）
        System.out.println("日志：开始执行 addUser 方法，参数：" + username);
        try {
            // 调用目标对象的原始方法
            target.addUser(username);
        } catch (Exception e) {
            // 切面逻辑：方法异常时（异常日志）
            System.out.println("日志：addUser 方法执行异常，原因：" + e.getMessage());
        } finally {
            // 切面逻辑：方法执行后（结束日志）
            System.out.println("日志：addUser 方法执行结束");
        }
    }

    @Override
    public void deleteUser(String username) {
        System.out.println("日志：开始执行 deleteUser 方法，参数：" + username);
        target.deleteUser(username);
        System.out.println("日志：deleteUser 方法执行结束");
    }
}
```



#### 步骤 4：客户端调用（通过代理对象）



java

运行

```
public class StaticProxyDemo {
    public static void main(String[] args) {
        // 1. 创建目标对象
        UserService target = new UserServiceImpl();
        // 2. 创建代理对象（包装目标对象）
        UserService proxy = new UserServiceStaticProxy(target);
        // 3. 调用代理对象的方法（自动触发切面逻辑）
        proxy.addUser("张三");
        System.out.println("-------------------");
        proxy.deleteUser("李四");
    }
}
```



### 运行结果



plaintext

```plaintext
日志：开始执行 addUser 方法，参数：张三
核心业务：添加用户 张三
日志：addUser 方法执行结束
-------------------
日志：开始执行 deleteUser 方法，参数：李四
核心业务：删除用户 李四
日志：deleteUser 方法执行结束
```



### 优缺点



| 优点                                             | 缺点                                                         |
| ------------------------------------------------ | ------------------------------------------------------------ |
| 实现简单，无额外依赖（无需框架）                 | 代码冗余：每个目标类都需手动编写代理类，若接口有多个方法，代理类需重复实现切面逻辑 |
| 性能好（编译期生成字节码，无运行时开销）         | 灵活性差：切面逻辑硬编码在代理类中，修改切面需修改代理类；新增目标类需新增代理类 |
| 可读性高（直观看到切面逻辑和目标方法的调用关系） | 仅支持接口实现类：代理类和目标类需实现同一接口（若目标类无接口，需继承目标类，耦合度更高） |

### 适用场景



- 简单场景（如单个类的少量方法增强）；
- 对性能要求极高，且切面逻辑固定的场景。

## 三、2. 动态代理（运行时生成代理类）



动态代理是 **运行时通过反射 / 字节码技术动态生成代理类的字节码**，无需手动编写代理类，切面逻辑可统一配置（如通过注解、XML），灵活性极高（Spring AOP、MyBatis 等框架的核心代理方式）。

动态代理分为 2 种核心实现：**JDK 动态代理**（JDK 原生支持，无需第三方依赖）和 **CGLIB 动态代理**（基于字节码生成库，需引入依赖）。

### （1）JDK 动态代理（基于接口）



#### 原理



JDK 动态代理是 JDK 原生提供的代理机制（`java.lang.reflect.Proxy` 类 + `InvocationHandler` 接口），核心特点：

- 代理类必须 **基于接口** 生成（目标类需实现至少一个接口）；
- 运行时通过 `Proxy.newProxyInstance()` 动态生成代理类字节码，代理类会实现目标类的所有接口；
- 切面逻辑统一写在 `InvocationHandler` 的 `invoke()` 方法中（所有代理方法都会触发该方法）。

### 实现步骤



1. 定义公共接口（目标类实现接口）；
2. 实现目标类（原始业务逻辑）；
3. 实现 `InvocationHandler` 接口（织入切面逻辑）；
4. 通过 `Proxy.newProxyInstance()` 生成代理对象。

### 代码示例（日志增强）



#### 步骤 1-2：复用静态代理的 `UserService` 接口和 `UserServiceImpl` 目标类



#### 步骤 3：实现 `InvocationHandler`（切面逻辑）



java

运行

```
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

// 切面逻辑处理器：实现 InvocationHandler，统一处理所有代理方法
public class LogInvocationHandler implements InvocationHandler {
    // 持有目标对象引用
    private final Object target;

    public LogInvocationHandler(Object target) {
        this.target = target;
    }

    /**
     * 所有代理方法的调用都会触发 invoke 方法
     * @param proxy 代理对象本身（一般不用）
     * @param method 目标方法的 Method 对象（通过反射获取）
     * @param args 目标方法的参数
     * @return 目标方法的返回值
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 切面逻辑：方法执行前（日志记录）
        System.out.println("日志：开始执行 " + method.getName() + " 方法，参数：" + (args != null ? args[0] : ""));
        Object result = null;
        try {
            // 调用目标对象的原始方法（通过反射）
            result = method.invoke(target, args);
        } catch (Exception e) {
            // 切面逻辑：方法异常时
            System.out.println("日志：" + method.getName() + " 方法执行异常，原因：" + e.getMessage());
        } finally {
            // 切面逻辑：方法执行后
            System.out.println("日志：" + method.getName() + " 方法执行结束");
        }
        return result;
    }
}
```



#### 步骤 4：生成代理对象并调用



java

运行

```
import java.lang.reflect.Proxy;

public class JdkDynamicProxyDemo {
    public static void main(String[] args) {
        // 1. 创建目标对象
        UserService target = new UserServiceImpl();
        // 2. 创建 InvocationHandler（传入目标对象）
        InvocationHandler handler = new LogInvocationHandler(target);
        // 3. 动态生成代理对象（核心 API：Proxy.newProxyInstance）
        UserService proxy = (UserService) Proxy.newProxyInstance(
            target.getClass().getClassLoader(), // 目标类的类加载器
            target.getClass().getInterfaces(),  // 目标类实现的所有接口（代理类会实现这些接口）
            handler                             // 切面逻辑处理器
        );

        // 4. 调用代理对象的方法（触发 invoke 方法）
        proxy.addUser("张三");
        System.out.println("-------------------");
        proxy.deleteUser("李四");
    }
}
```



### 运行结果（与静态代理一致）



plaintext

```plaintext
日志：开始执行 addUser 方法，参数：张三
核心业务：添加用户 张三
日志：addUser 方法执行结束
-------------------
日志：开始执行 deleteUser 方法，参数：李四
核心业务：删除用户 李四
日志：deleteUser 方法执行结束
```



### 核心特点



- 依赖接口：目标类必须实现接口，否则无法生成代理对象（`Proxy.newProxyInstance` 第二个参数是接口数组）；
- 反射调用：目标方法通过 `Method.invoke()` 反射调用，有一定性能开销，但比 CGLIB 略轻；
- 灵活性高：一个 `InvocationHandler` 可复用给多个目标类（如同时代理 `UserService` 和 `OrderService`），切面逻辑统一维护。

### （2）CGLIB 动态代理（基于继承）



#### 原理



CGLIB（Code Generation Library）是一个第三方字节码生成库，核心特点：

- 代理类通过 **继承目标类** 生成（无需目标类实现接口）；
- 运行时动态生成目标类的子类，并重写目标方法，在重写方法中织入切面逻辑；
- 依赖 `asm` 字节码操作框架（CGLIB 已内置），需引入 CGLIB 依赖。

### 实现步骤



1. 引入 CGLIB 依赖；
2. 实现目标类（可无接口，直接是普通类）；
3. 实现 `MethodInterceptor` 接口（CGLIB 提供的切面逻辑处理器）；
4. 通过 `Enhancer` 类生成代理对象（CGLIB 的核心工具类）。

### 代码示例（日志增强）



#### 步骤 1：引入 CGLIB 依赖（Maven）



xml

```
<!-- CGLIB 核心依赖 -->
<dependency>
    <groupId>cglib</groupId>
    <artifactId>cglib</artifactId>
    <version>3.3.0</version>
</dependency>
```



#### 步骤 2：实现目标类（无接口，普通类）



java

运行

```
// 目标对象：无接口的普通类（CGLIB 可直接代理）
public class UserService {
    public void addUser(String username) {
        System.out.println("核心业务：添加用户 " + username);
    }

    public void deleteUser(String username) {
        System.out.println("核心业务：删除用户 " + username);
    }
}
```



#### 步骤 3：实现 `MethodInterceptor`（切面逻辑）



java

运行

```
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import java.lang.reflect.Method;

// CGLIB 切面处理器：实现 MethodInterceptor
public class LogMethodInterceptor implements MethodInterceptor {
    /**
     * 所有代理方法的调用都会触发 intercept 方法
     * @param obj 代理对象（目标类的子类实例）
     * @param method 目标方法的 Method 对象
     * @param args 目标方法的参数
     * @param proxy MethodProxy 对象（CGLIB 提供的高效方法调用工具，比反射快）
     * @return 目标方法的返回值
     */
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        // 切面逻辑：方法执行前
        System.out.println("日志：开始执行 " + method.getName() + " 方法，参数：" + (args != null ? args[0] : ""));
        Object result = null;
        try {
            // 调用目标对象的原始方法（两种方式）
            // 方式 1：通过 MethodProxy.invokeSuper（推荐，比反射快，直接调用父类（目标类）方法）
            result = proxy.invokeSuper(obj, args);
            // 方式 2：通过 Method.invoke（反射调用，性能略差）
            // result = method.invoke(target, args); // 需持有目标对象引用
        } catch (Exception e) {
            System.out.println("日志：" + method.getName() + " 方法执行异常，原因：" + e.getMessage());
        } finally {
            System.out.println("日志：" + method.getName() + " 方法执行结束");
        }
        return result;
    }
}
```



#### 步骤 4：生成代理对象并调用



java

运行

```
import net.sf.cglib.proxy.Enhancer;

public class CglibDynamicProxyDemo {
    public static void main(String[] args) {
        // 1. 创建 CGLIB 增强器（核心工具类）
        Enhancer enhancer = new Enhancer();
        // 2. 设置父类（目标类，代理类会继承该类）
        enhancer.setSuperclass(UserService.class);
        // 3. 设置切面处理器（MethodInterceptor）
        enhancer.setCallback(new LogMethodInterceptor());
        // 4. 动态生成代理对象（子类实例）
        UserService proxy = (UserService) enhancer.create();

        // 5. 调用代理对象的方法（触发 intercept 方法）
        proxy.addUser("张三");
        System.out.println("-------------------");
        proxy.deleteUser("李四");
    }
}
```



### 运行结果（与静态代理、JDK 动态代理一致）



plaintext

```plaintext
日志：开始执行 addUser 方法，参数：张三
核心业务：添加用户 张三
日志：addUser 方法执行结束
-------------------
日志：开始执行 deleteUser 方法，参数：李四
核心业务：删除用户 李四
日志：deleteUser 方法执行结束
```



### 核心特点



- 不依赖接口：目标类可无接口（弥补 JDK 动态代理的不足）；
- 字节码增强：通过生成子类重写方法实现代理，`MethodProxy.invokeSuper()` 比 JDK 反射调用略快；
- 限制：目标类不能是 `final`（无法继承），目标方法不能是 `final`（无法重写）；
- 依赖第三方库：需引入 CGLIB 依赖（Spring 已内置 CGLIB，无需额外引入）。

## 四、3 种代理方式对比总结



| 代理方式       | 核心原理                                      | 是否依赖接口       | 性能                           | 优点                                               | 缺点                                      | 典型应用                                         |
| -------------- | --------------------------------------------- | ------------------ | ------------------------------ | -------------------------------------------------- | ----------------------------------------- | ------------------------------------------------ |
| 静态代理       | 编译期生成代理类，实现同一接口 / 继承同一父类 | 是（或继承父类）   | 最高（无运行时开销）           | 实现简单、可读性高、无反射开销                     | 代码冗余、灵活性差、维护成本高            | 简单场景（少量类增强）                           |
| JDK 动态代理   | 运行时通过 `Proxy` 类生成代理类，实现目标接口 | 是（必须）         | 中（反射调用开销）             | 原生支持（无第三方依赖）、灵活性高、可复用切面逻辑 | 仅支持接口代理、反射调用有轻微开销        | Spring AOP（默认优先）、MyBatis Mapper 代理      |
| CGLIB 动态代理 | 运行时通过字节码生成子类，重写目标方法        | 否（支持无接口类） | 中高（`MethodProxy` 比反射快） | 支持无接口类代理、性能略优于 JDK 动态代理          | 依赖第三方库、目标类 / 方法不能是 `final` | Spring AOP（目标类无接口时 fallback）、Hibernate |

## 五、Spring AOP 对代理方式的选择逻辑



Spring AOP 是 AOP 思想的主流实现，其代理选择规则如下（Spring 4.3+ 版本）：

1. 若目标类 **实现了接口**：优先使用 **JDK 动态代理**（原生支持，无额外依赖）；
2. 若目标类 **未实现接口**：使用 **CGLIB 动态代理**（Spring 内置 CGLIB，无需手动引入）；
3. 强制使用 CGLIB：可通过配置 `spring.aop.proxy-target-class=true`（Spring Boot）或 `<aop:aspectj-autoproxy proxy-target-class="true"/>`（XML 配置），强制所有代理都使用 CGLIB。

### 选择建议



- 若目标类有接口：优先 JDK 动态代理（轻量、无依赖）；
- 若目标类无接口：使用 CGLIB；
- 若需代理 `final` 方法：无法使用 CGLIB，需给目标类添加接口，改用 JDK 动态代理。

## 核心结论



1. AOP 代理的核心是 **“动态织入切面逻辑”**，静态代理仅适用于简单场景，动态代理是框架的主流选择；
2. JDK 动态代理和 CGLIB 动态代理的核心区别是 **“是否依赖接口”** 和 **“实现方式（接口实现 vs 继承）”**；
3. 日常开发中无需手动实现代理（由 Spring AOP 等框架封装），但需理解其原理，避免踩坑（如 `final` 方法无法被增强）。
