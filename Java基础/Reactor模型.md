# Reactor 线程模型：核心原理、三种模式与实际应用

Reactor 线程模型（也叫 “反应器模式”）是 **高并发网络编程** 的核心设计模式，核心思想是「**事件驱动 + 多路复用**」—— 通过一个或多个线程监听所有 IO 事件，当事件就绪（如数据可读、连接就绪）时，触发对应的处理逻辑，从而实现 “单线程 / 少量线程处理大量并发连接”，避免传统 BIO 模型的线程开销问题。

Reactor 模型是 Java NIO、Netty、Redis、Nginx 等高性能中间件的底层核心，理解它就能看透高并发网络编程的本质。

## 一、核心问题：为什么需要 Reactor 模型？

传统 BIO 模型的痛点是「1 个连接对应 1 个线程」：

- 高并发下线程数量暴增，导致 CPU 上下文切换频繁、内存占用飙升；
- 线程大部分时间处于阻塞状态（等待 IO 就绪），CPU 利用率极低。

Reactor 模型的解决思路：

1. **事件驱动**：不再让线程等待 IO，而是让 IO 事件（如 “数据可读”）触发线程处理；
2. **多路复用**：用一个线程（或少量线程）通过「Selector（NIO）/epoll（Linux）」监听所有连接的 IO 事件，实现 “一个线程管所有连接”；
3. **分工明确**：将 “事件监听” 和 “业务处理” 分离，避免业务逻辑阻塞事件监听线程。

## 二、Reactor 模型的核心组件

无论哪种 Reactor 模式，都包含以下 4 个核心组件（基于 NIO 举例）：

| 组件               | 作用                                                         | 对应 NIO 实现                     |
| ------------------ | ------------------------------------------------------------ | --------------------------------- |
| Reactor（反应器）  | 核心调度器：监听 IO 事件，将事件分发到对应的 Handler 处理    | Selector（多路复用器）            |
| Acceptor（接受器） | 专门处理「连接事件」（OP_ACCEPT）：接受客户端连接，创建对应的 Handler | ServerSocketChannel + 线程        |
| Handler（处理器）  | 处理具体 IO 事件（如 OP_READ/OP_WRITE）：读取数据、业务逻辑、写入响应 | SocketChannel + Buffer + 业务逻辑 |
| Event（事件）      | IO 事件类型：连接就绪（OP_ACCEPT）、数据可读（OP_READ）、数据可写（OP_WRITE） | SelectionKey（NIO 事件标识）      |

核心流程：

1. Reactor 启动，通过 Selector 监听所有 Channel 的 IO 事件；
2. 客户端发起连接，触发「OP_ACCEPT 事件」，Reactor 通知 Acceptor 处理；
3. Acceptor 接受连接，创建 SocketChannel 和对应的 Handler，将 Handler 注册到 Reactor；
4. 客户端发送数据，触发「OP_READ 事件」，Reactor 通知对应的 Handler 处理；
5. Handler 读取数据、执行业务逻辑、写入响应，完成一次交互。

## 三、Reactor 模型的三种核心模式（从简单到复杂）

根据「Reactor 线程数量」和「Handler 处理线程数量」，Reactor 分为三种模式，适用于不同并发场景：

### 1. 单 Reactor 单线程模式（最简单，入门级）

#### 核心结构：

- 一个 Reactor 线程（同时担任 “事件监听” 和 “业务处理”）；
- 所有 IO 事件（连接、读、写）和业务逻辑都在这一个线程中处理。

#### 工作流程：


```plaintext
客户端 → 连接事件（OP_ACCEPT）→ Reactor 线程 → Acceptor 接受连接 → 创建 Handler → 注册读事件（OP_READ）→
客户端发数据 → 读事件触发 → Reactor 线程 → Handler 读数据 → 执行业务逻辑 → 写响应（OP_WRITE）→ 客户端
```

#### 代码简化模型（基于 NIO）：

```java
public class SingleReactorSingleThread {
    private final Selector selector;
    private final ServerSocketChannel serverChannel;

    public SingleReactorSingleThread(int port) throws IOException {
        // 1. 初始化 Reactor（Selector）
        selector = Selector.open();
        // 2. 初始化 Acceptor（ServerSocketChannel）
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        // 3. 注册连接事件到 Reactor
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    // 启动 Reactor 线程（核心循环）
    public void run() throws IOException {
        while (!Thread.currentThread().isInterrupted()) {
            // 监听 IO 事件（阻塞，直到有事件触发）
            selector.select();
            // 遍历就绪事件
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove(); // 避免重复处理

                if (key.isAcceptable()) {
                    // 处理连接事件（Acceptor 逻辑）
                    accept(key);
                } else if (key.isReadable()) {
                    // 处理读事件（Handler 逻辑：读数据 + 业务处理 + 写响应）
                    handleRead(key);
                }
            }
        }
    }

    // Acceptor：接受连接
    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = server.accept(); // 非阻塞接受
        clientChannel.configureBlocking(false);
        // 注册读事件到 Reactor
        clientChannel.register(selector, SelectionKey.OP_READ);
        System.out.println("新客户端连接：" + clientChannel.getRemoteAddress());
    }

    // Handler：处理读事件 + 业务逻辑 + 写响应
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        // 1. 读数据
        int readBytes = clientChannel.read(buffer);
        if (readBytes == -1) {
            clientChannel.close();
            key.cancel();
            return;
        }
        // 2. 执行业务逻辑（如解析请求、计算响应）
        buffer.flip();
        String request = new String(buffer.array(), 0, readBytes);
        String response = "处理结果：" + request.toUpperCase();
        // 3. 写响应
        clientChannel.write(ByteBuffer.wrap(response.getBytes()));
    }
}
```

#### 优点：

- 实现简单，无线程安全问题（单线程）；
- 无线程上下文切换开销。

#### 缺点（致命）：

- 单线程瓶颈：所有连接的 IO 操作 + 业务逻辑都在一个线程，高并发下线程阻塞（如业务逻辑耗时）会导致所有连接超时；
- 不适合 CPU 密集型业务（业务逻辑耗时会阻塞 IO 事件监听）。

#### 适用场景：

- 低并发、短连接、业务逻辑简单（无耗时操作）的场景（如简单回声服务器）；
- 仅用于理解 Reactor 核心思想，生产环境几乎不用。

### 2. 单 Reactor 多线程模式（常用，平衡性能与复杂度）

#### 核心改进：

- 保留 1 个 Reactor 线程（专门处理「事件监听」和「连接事件」）；
- 新增「业务处理线程池」：将 Handler 中的 “业务逻辑” 剥离到线程池执行，IO 操作（读 / 写）仍在 Reactor 线程，避免业务逻辑阻塞 Reactor。

#### 核心结构：

```plaintext
Reactor 线程（1 个）：监听事件 + 处理连接 + 处理 IO 读/写
业务线程池（N 个）：处理耗时业务逻辑（如数据库查询、复杂计算）
```

#### 工作流程：

```plaintext
客户端 → 连接事件 → Reactor 线程 → Acceptor 接受连接 → 创建 Handler → 注册读事件 →
客户端发数据 → 读事件触发 → Reactor 线程 → Handler 读数据 → 提交业务逻辑到线程池 →
线程池执行业务逻辑 → 生成响应 → 通知 Reactor 线程 → Reactor 线程写响应到客户端
```

#### 代码简化模型（核心修改：业务逻辑异步化）：

```java
public class SingleReactorMultiThread {
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    // 业务处理线程池
    private final ExecutorService workerPool = Executors.newFixedThreadPool(10);

    public SingleReactorMultiThread(int port) throws IOException {
        // 初始化逻辑同单线程模式（略）
    }

    // 启动 Reactor 线程（同单线程模式，略）
    // Acceptor 逻辑（同单线程模式，略）

    // Handler：读数据 + 提交业务逻辑到线程池 + 写响应
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int readBytes = clientChannel.read(buffer);
        if (readBytes == -1) {
            clientChannel.close();
            key.cancel();
            return;
        }
        // 读取数据（保留原数据，避免线程安全问题）
        byte[] data = new byte[readBytes];
        System.arraycopy(buffer.array(), 0, data, 0, readBytes);
        String request = new String(data);

        // 核心改进：提交业务逻辑到线程池（异步执行）
        workerPool.submit(() -> {
            try {
                // 1. 执行业务逻辑（耗时操作，如数据库查询）
                Thread.sleep(100); // 模拟耗时
                String response = "多线程处理结果：" + request.toUpperCase();

                // 2. 业务完成后，注册写事件到 Reactor（让 Reactor 线程写响应）
                selector.wakeup(); // 唤醒 Reactor 线程（避免阻塞在 select()）
                clientChannel.register(selector, SelectionKey.OP_WRITE, response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Reactor 线程中新增写事件处理
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        String response = (String) key.attachment(); // 获取业务线程池的响应
        clientChannel.write(ByteBuffer.wrap(response.getBytes()));
        // 写完成后，重新注册读事件（等待下一次请求）
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    // 关闭资源时关闭线程池
    public void shutdown() {
        workerPool.shutdown();
    }
}
```

#### 优点：

- 解决单线程瓶颈：业务逻辑在多线程执行，不阻塞 Reactor 线程的事件监听；
- IO 操作仍在 Reactor 线程，无多线程竞争，线程安全；
- 实现复杂度适中，是生产环境常用模式。

#### 缺点：

- Reactor 线程仍是单点瓶颈：如果 Reactor 线程挂掉，整个服务不可用；
- 高并发下，Reactor 线程的 IO 读 / 写操作可能成为瓶颈（如百万级连接的 IO 事件触发）。

#### 适用场景：

- 中高并发、业务逻辑耗时（如数据库查询、RPC 调用）的场景；
- 大部分网络服务的默认选择（如普通 HTTP 服务器、消息队列客户端）。

### 3. 主从 Reactor 多线程模式（高并发终极方案）

#### 核心改进：

- 将 Reactor 线程拆分为「主 Reactor（Main Reactor）」和「从 Reactor（Sub Reactor）」；
- 主 Reactor：仅处理「连接事件（OP_ACCEPT）」，接受连接后分发给从 Reactor；
- 从 Reactor（多个）：每个从 Reactor 管理一部分连接的 IO 事件（读 / 写），再配合业务线程池处理业务逻辑。

#### 核心结构：



```plaintext
主 Reactor（1 个）：监听连接事件 → 接受连接 → 分发连接到从 Reactor
从 Reactor（N 个）：监听并处理分管连接的 IO 事件（读/写）
业务线程池（M 个）：处理耗时业务逻辑
```

#### 工作流程：


```plaintext
客户端 → 连接事件 → 主 Reactor → Acceptor 接受连接 → 分发连接到某个从 Reactor →
从 Reactor 注册读事件 → 客户端发数据 → 读事件触发 → 从 Reactor → Handler 读数据 →
提交业务逻辑到线程池 → 线程池执行业务 → 生成响应 → 从 Reactor 写响应 → 客户端
```

#### 代码简化模型（核心：主从 Reactor 分工）：



```java
// 主 Reactor：仅处理连接事件
class MainReactor implements Runnable {
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final List<SubReactor> subReactors; // 从 Reactor 列表
    private int nextSubReactorIndex = 0; // 轮询分发连接

    public MainReactor(int port, int subReactorCount) throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // 初始化从 Reactor（多个）
        subReactors = new ArrayList<>();
        ExecutorService subPool = Executors.newFixedThreadPool(subReactorCount);
        for (int i = 0; i < subReactorCount; i++) {
            SubReactor subReactor = new SubReactor();
            subReactors.add(subReactor);
            subPool.submit(subReactor);
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    if (key.isAcceptable()) {
                        accept(key); // 处理连接，分发到从 Reactor
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 接受连接，轮询分发到从 Reactor
    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = server.accept();
        clientChannel.configureBlocking(false);
        System.out.println("主 Reactor 接受连接：" + clientChannel.getRemoteAddress());

        // 轮询选择一个从 Reactor 分发连接
        SubReactor subReactor = subReactors.get(nextSubReactorIndex);
        nextSubReactorIndex = (nextSubReactorIndex + 1) % subReactors.size();
        subReactor.register(clientChannel); // 从 Reactor 注册该连接的 IO 事件
    }
}

// 从 Reactor：处理分管连接的 IO 事件
class SubReactor implements Runnable {
    private final Selector selector;
    private final ExecutorService workerPool = Executors.newFixedThreadPool(5); // 业务线程池

    public SubReactor() throws IOException {
        this.selector = Selector.open();
    }

    // 注册客户端连接到当前从 Reactor
    public void register(SocketChannel clientChannel) throws IOException {
        selector.wakeup(); // 唤醒从 Reactor（避免阻塞在 select()）
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    if (key.isReadable()) {
                        handleRead(key); // 处理读事件，提交业务到线程池
                    } else if (key.isWritable()) {
                        handleWrite(key); // 处理写事件
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 读数据 + 提交业务逻辑（同单 Reactor 多线程模式，略）
    private void handleRead(SelectionKey key) throws IOException { /* ... */ }

    // 写响应（同单 Reactor 多线程模式，略）
    private void handleWrite(SelectionKey key) throws IOException { /* ... */ }
}

// 启动服务
public class MasterSlaveReactor {
    public static void main(String[] args) throws IOException {
        MainReactor mainReactor = new MainReactor(8080, 2); // 2 个从 Reactor
        new Thread(mainReactor).start();
    }
}
```

#### 优点：

- 无单点瓶颈：主 Reactor 仅处理连接，从 Reactor 分摊 IO 事件处理，业务逻辑多线程执行；
- 高并发支撑：可通过增加从 Reactor 数量和业务线程池大小，横向扩展支撑百万级连接；
- 容错性强：单个从 Reactor 挂掉，仅影响其分管的连接，其他从 Reactor 正常工作。

#### 缺点：

- 实现复杂度最高：需要处理主从 Reactor 协作、连接分发、线程安全等问题；
- 资源占用略高（多个 Reactor 线程 + 业务线程池）。

#### 适用场景：

- 超高并发、高可用要求的场景（如分布式服务、消息队列服务器、大型网关）；
- 生产环境高性能中间件的标准选择（如 Netty 默认采用主从 Reactor 模式）。

## 四、Reactor 模型的核心优势与适用场景

### 核心优势：

1. **高并发支撑**：基于多路复用，少量线程处理大量连接，线程开销极低；
2. **事件驱动**：线程仅在事件就绪时工作，CPU 利用率高（无无效阻塞）；
3. **扩展性强**：可通过增加从 Reactor 数量、业务线程池大小横向扩展；
4. **解耦清晰**：事件监听、连接处理、IO 操作、业务逻辑分离，代码可维护性高。

### 适用场景：

- 高并发网络编程（如 HTTP 服务器、WebSocket 服务器、RPC 框架）；
- IO 密集型场景（如文件传输、日志收集、消息队列）；
- 分布式系统通信（如微服务间调用、分布式缓存）。

### 不适用场景：

- 纯 CPU 密集型场景（如科学计算、复杂算法）：线程池会被占满，Reactor 模型无优势；
- 低并发、简单场景（如本地工具类、简单接口）：Reactor 复杂度反而增加开发成本。

## 五、Reactor 模型的实际应用（知名中间件）

1. **Netty**：默认采用「主从 Reactor 多线程模式」，主 Reactor 处理连接，从 Reactor 处理 IO 事件，业务逻辑通过 `ChannelPipeline` 异步执行；
2. **Redis**：单 Reactor 单线程模式（核心网络 IO + 命令执行在一个线程），因为 Redis 命令执行极快（内存操作），无单线程瓶颈；
3. **Nginx**：多进程 Reactor 模式（类似主从 Reactor），主进程监听端口，工作进程处理连接和 IO 事件，每个工作进程是一个独立的 Reactor；
4. **MySQL**：单 Reactor 多线程模式（主线程监听连接，工作线程处理 IO 和 SQL 执行）。

## 六、三种模式对比总结表

| 模式                | 核心线程分工                                          | 优点                                   | 缺点                       | 适用场景                                |
| ------------------- | ----------------------------------------------------- | -------------------------------------- | -------------------------- | --------------------------------------- |
| 单 Reactor 单线程   | 1 线程处理所有事件 + 业务逻辑                         | 实现简单、无线程安全问题               | 单线程瓶颈、不支持耗时业务 | 低并发、简单场景（如回声服务器）        |
| 单 Reactor 多线程   | 1 线程监听事件 + 多线程处理业务逻辑                   | 平衡性能与复杂度、无单点瓶颈（业务层） | Reactor 线程单点瓶颈       | 中高并发、业务耗时场景（如 HTTP 服务）  |
| 主从 Reactor 多线程 | 主 Reactor 处理连接 + 从 Reactor 处理 IO + 多线程业务 | 高并发支撑、无单点瓶颈、容错性强       | 实现复杂、资源占用略高     | 超高并发、高可用场景（如 Netty 服务器） |

## 核心总结

Reactor 模型的本质是「**事件驱动 + 多路复用 + 分工协作**」：

- 事件驱动：IO 事件触发处理，避免线程阻塞；
- 多路复用：一个线程监听多个连接，降低线程开销；
- 分工协作：拆分事件监听、连接处理、业务逻辑，提升并发能力。

选型建议：

- 简单场景：单 Reactor 单线程；
- 常规高并发：单 Reactor 多线程；
- 超高并发 / 高可用：主从 Reactor 多线程（优先用 Netty 等成熟框架，避免重复造轮子）。
