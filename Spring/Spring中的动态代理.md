代理模式是一种结构型设计模式，用于在不修改原始类代码的情况下，为其提供额外功能（如日志、事务、权限控制等）。Java 中常见的代理实现方式有两种：**JDK 动态代理**（基于接口）和**CGLIB 动态代理**（基于继承）。

### 一、JDK 动态代理（基于接口）

JDK 动态代理是 Java 原生支持的代理方式，**要求目标类必须实现接口**，代理类会动态生成并实现相同的接口。

#### 实现步骤：

1. 定义业务接口和实现类（目标类）。
2. 实现 `InvocationHandler` 接口，编写代理逻辑（如增强代码）。
3. 通过 `Proxy.newProxyInstance()` 生成代理实例。

#### 代码实现：











```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// 1. 定义业务接口
interface UserService {
    void login(String username);
    void logout();
}

// 2. 实现接口的目标类
class UserServiceImpl implements UserService {
    @Override
    public void login(String username) {
        System.out.println("用户 " + username + " 登录成功");
    }

    @Override
    public void logout() {
        System.out.println("用户登出成功");
    }
}

// 3. 实现InvocationHandler，编写代理逻辑
class LogInvocationHandler implements InvocationHandler {
    // 目标对象（被代理的原始对象）
    private Object target;

    public LogInvocationHandler(Object target) {
        this.target = target;
    }

    /**
     * 代理方法的核心逻辑
     * @param proxy 代理对象本身（一般不用）
     * @param method 目标方法
     * @param args 目标方法的参数
     * @return 目标方法的返回值
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 前置增强（如日志记录）
        System.out.println("===== 方法 " + method.getName() + " 开始执行 =====");
        
        // 调用目标对象的原始方法
        Object result = method.invoke(target, args);
        
        // 后置增强
        System.out.println("===== 方法 " + method.getName() + " 执行结束 =====");
        return result;
    }
}

// 测试JDK动态代理
public class JdkProxyDemo {
    public static void main(String[] args) {
        // 目标对象
        UserService target = new UserServiceImpl();
        
        // 创建代理处理器
        InvocationHandler handler = new LogInvocationHandler(target);
        
        // 生成代理对象（参数：类加载器、目标接口、处理器）
        UserService proxy = (UserService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                handler
        );
        
        // 调用代理对象的方法（实际会执行invoke()中的逻辑）
        proxy.login("张三");
        proxy.logout();
    }
}
```

#### 输出结果：










```plaintext
===== 方法 login 开始执行 =====
用户 张三 登录成功
===== 方法 login 执行结束 =====
===== 方法 logout 开始执行 =====
用户登出成功
===== 方法 logout 执行结束 =====
```

### 二、CGLIB 动态代理（基于继承）

CGLIB（Code Generation Library）是一个第三方库，**通过继承目标类生成代理类**，无需目标类实现接口。适用于没有接口的类。

#### 实现步骤：

1. 引入 CGLIB 依赖（需手动添加，JDK 不自带）。
2. 定义目标类（无需实现接口）。
3. 实现 `MethodInterceptor` 接口，编写代理逻辑。
4. 通过 `Enhancer` 生成代理实例。

#### 代码实现：

##### 1. 引入 CGLIB 依赖（Maven）：








```xml
<dependency>
    <groupId>cglib</groupId>
    <artifactId>cglib</artifactId>
    <version>3.3.0</version>
</dependency>
```

##### 2. 代理代码：











```java
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import java.lang.reflect.Method;

// 1. 目标类（无需实现接口）
class OrderService {
    public void createOrder(String product) {
        System.out.println("创建订单：" + product);
    }

    public void cancelOrder() {
        System.out.println("取消订单");
    }
}

// 2. 实现MethodInterceptor，编写代理逻辑
class TransactionInterceptor implements MethodInterceptor {
    // 目标对象
    private Object target;

    public TransactionInterceptor(Object target) {
        this.target = target;
    }

    /**
     * 代理方法的核心逻辑
     * @param obj 代理对象
     * @param method 目标方法
     * @param args 方法参数
     * @param proxy 方法代理（用于调用父类方法）
     * @return 目标方法的返回值
     */
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        // 前置增强（如开启事务）
        System.out.println("===== 开启事务 =====");
        
        // 调用目标对象的原始方法（通过代理调用父类方法）
        Object result = proxy.invokeSuper(obj, args);
        
        // 后置增强（如提交事务）
        System.out.println("===== 提交事务 =====");
        return result;
    }
}

// 测试CGLIB动态代理
public class CglibProxyDemo {
    public static void main(String[] args) {
        // 目标对象
        OrderService target = new OrderService();
        
        // 创建增强器（CGLIB核心类）
        Enhancer enhancer = new Enhancer();
        // 设置父类（目标类）
        enhancer.setSuperclass(OrderService.class);
        // 设置回调（代理逻辑）
        enhancer.setCallback(new TransactionInterceptor(target));
        
        // 生成代理对象（是目标类的子类）
        OrderService proxy = (OrderService) enhancer.create();
        
        // 调用代理对象的方法
        proxy.createOrder("手机");
        proxy.cancelOrder();
    }
}
```

#### 输出结果：










```plaintext
===== 开启事务 =====
创建订单：手机
===== 提交事务 =====
===== 开启事务 =====
取消订单
===== 提交事务 =====
```

### 三、JDK 动态代理 vs CGLIB 动态代理

| 对比维度   | JDK 动态代理               | CGLIB 动态代理                |
| ---------- | -------------------------- | ----------------------------- |
| 底层原理   | 实现目标接口生成代理类     | 继承目标类生成代理子类        |
| 目标类要求 | 必须实现接口               | 可无接口（但不能是 final 类） |
| 性能       | 调用效率高（直接反射）     | 生成代理类时较慢，但调用快    |
| 依赖       | JDK 原生支持，无需额外依赖 | 需引入 CGLIB 库               |
| 适用场景   | 目标类有接口时             | 目标类无接口时                |

两种代理方式均为动态代理（运行时生成代理类），核心思想是通过 “代理对象” 包装 “目标对象”，在不修改目标类的前提下增强其功能。