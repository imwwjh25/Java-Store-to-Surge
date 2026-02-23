Servlet 的线程模型是**多线程**的，但默认情况下**线程不安全**，这是由其设计机制决定的。我们从 “工作原理” 和 “线程安全” 两个角度详细拆解：

### 一、Servlet 是多线程的：单实例，多线程并发处理请求

Servlet 的核心运行机制是 **“单实例，多线程”**：

1. **单实例**：当 Web 容器（如 Tomcat）启动时，会为每个 Servlet 类创建**唯一的一个实例**（默认情况下，`load-on-startup` 配置决定是否启动时加载，否则首次请求时创建）。这个实例会被所有请求共享，生命周期与 Web 容器一致（除非被销毁重建）。

2. **多线程处理请求**：当多个客户端同时请求同一个 Servlet 时，Web 容器会从线程池（如 Tomcat 的 `Executor`）中分配**多个线程**，每个线程独立执行 Servlet 的 `service()` 方法（或 `doGet()`、`doPost()` 等），处理对应的请求。

   例如：用户 A 和用户 B 同时访问 `LoginServlet`，Tomcat 会启动线程 T1 和 T2，同时执行 `LoginServlet` 单实例的 `doPost()` 方法，分别处理 A 和 B 的登录请求。

### 二、Servlet 默认线程不安全：共享实例变量导致的并发问题

线程安全的核心是 “多线程并发访问共享资源时，数据是否一致”。Servlet 之所以默认不安全，是因为**单实例的共享变量可能被多个线程同时修改**：

#### 1. 线程不安全的典型场景：使用实例变量

若在 Servlet 中定义**实例变量**（类级别的变量，而非方法内的局部变量），多线程并发时会导致数据混乱：






```java
public class LoginServlet extends HttpServlet {
    // 实例变量（被所有线程共享）
    private String username; 

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        // 线程1和线程2可能同时修改 username
        username = req.getParameter("username"); 
        // 模拟业务处理（此时若线程切换，其他线程会读到错误的 username）
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        System.out.println("当前用户：" + username); 
    }
}
```

- 问题：用户 A 线程（T1）设置 `username="A"` 后，还未打印时，用户 B 线程（T2）将 `username` 改为 `"B"`，最终 T1 可能打印 `"B"`，导致数据错误。

#### 2. 局部变量是安全的：线程私有

方法内的局部变量存储在**线程私有栈空间**，每个线程的局部变量互不干扰，因此是安全的：











```java
@Override
protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
    // 局部变量（每个线程独立拥有）
    String username = req.getParameter("username"); 
    // 即使线程切换，其他线程也不会影响当前线程的 username
    try { Thread.sleep(1000); } catch (InterruptedException e) {}
    System.out.println("当前用户：" + username); // 安全
}
```

### 三、如何保证 Servlet 线程安全？

解决线程安全问题的核心是**避免多线程共享可变状态**，常用方案有 3 种：

#### 1. 不使用实例变量，仅用局部变量（推荐）

这是最简单有效的方式：将所有变量定义在 `service()`、`doGet()` 等方法内部（局部变量），或通过 `request`、`response` 等参数传递数据（这些对象是线程私有的，每个请求对应独立的 `request`/`response`）。

#### 2. 对共享资源加锁（谨慎使用）

若必须使用实例变量（如缓存全局配置），需通过 `synchronized` 或 `Lock` 对操作共享变量的代码加锁，保证同一时间只有一个线程访问：









```java
private String config; // 共享的配置变量

// 加锁保护共享变量的修改
private synchronized void updateConfig(String newConfig) {
    this.config = newConfig;
}
```

- 缺点：加锁会降低并发性能，若锁粒度大（如直接锁 `service()` 方法），会导致所有请求串行执行，失去多线程优势。

#### 3. 配置 Servlet 为 “单线程模式”（不推荐）

通过在 `web.xml` 中配置 `<servlet>` 的 `<single-thread-model>` 标签（或注解），强制 Servlet 为每个请求创建新实例（或使用实例池，每个线程一个实例）：






```xml
<servlet>
    <servlet-name>LoginServlet</servlet-name>
    <servlet-class>com.example.LoginServlet</servlet-class>
    <single-thread-model/> <!-- 单线程模式 -->
</servlet>
```

- 问题：此模式已被 Servlet 2.4 标记为过时（deprecated），原因是创建多个实例会浪费内存，且无法解决跨实例的共享资源问题（如数据库连接），性能远不如 “局部变量 + 合理加锁” 方案。

### 总结

- **线程模型**：Servlet 是**多线程**的，单实例被多个线程共享，通过线程池并发处理请求，效率高。
- **线程安全**：默认**不安全**，因实例变量会被多线程并发修改；局部变量是安全的。
- **最佳实践**：避免使用实例变量，仅用局部变量和 `request`/`response` 传递数据，无需额外同步，既保证安全又不影响性能。