


Java NIO（New IO，也叫 Non-blocking IO，非阻塞 IO）是 JDK 1.4 引入的一套全新 IO 模型，核心目标是解决传统 IO（BIO，Blocking IO）在高并发场景下的性能瓶颈。其核心优势是 **“非阻塞 + 多路复用”**，允许单个线程高效处理成千上万个网络连接，大幅提升高并发场景下的吞吐量。

## 一、先搞懂：NIO 与 BIO 的核心区别



要理解 NIO，先对比传统 BIO 的痛点，才能明白 NIO 的设计初衷：

| 对比维度 | BIO（阻塞 IO）                                  | NIO（非阻塞 IO）                           |
| -------- | ----------------------------------------------- | ------------------------------------------ |
| 核心模型 | 阻塞 + 线程池（1 个连接对应 1 个线程）          | 非阻塞 + 多路复用（1 个线程处理 N 个连接） |
| 线程开销 | 高（线程创建 / 上下文切换成本高，连接越多越卡） | 低（单线程 / 少量线程即可支撑高并发）      |
| IO 操作  | 读写均阻塞（线程等待数据就绪 / 写入完成）       | 读写非阻塞（数据未就绪时线程可做其他事）   |
| 适用场景 | 低并发、短连接（如简单 TCP 服务）               | 高并发、长连接（如服务器、消息队列）       |
| 核心组件 | Socket、ServerSocket、InputStream/OutputStream  | Channel、Buffer、Selector、SelectorKey     |

**BIO 的致命痛点**：比如一个服务器用 BIO 处理 1000 个客户端连接，就需要 1000 个线程（或线程池核心线程数 1000）。线程数量过多会导致 CPU 上下文切换频繁、内存占用飙升，最终服务器性能崩溃。

**NIO 的解决思路**：用「非阻塞」让线程不等待 IO 操作，用「多路复用」让单个线程同时监听多个连接的 IO 状态（数据是否就绪），只有当连接有数据时才处理，从而用少量线程支撑高并发。

## 二、NIO 的三大核心组件（必须掌握）



NIO 的所有功能都围绕「Channel（通道）、Buffer（缓冲区）、Selector（选择器）」展开，三者协同工作实现非阻塞多路复用，缺一不可。

### 1. Buffer（缓冲区）：数据的 “容器”



#### 核心作用



Buffer 是一块连续的内存区域，用于存储 IO 操作的数据（读数据时，数据从 Channel 读到 Buffer；写数据时，数据从 Buffer 写到 Channel）。**为什么需要 Buffer？**：传统 BIO 是 “流模式”（InputStream/OutputStream 直接读写字节），而 NIO 是 “块模式”（以 Buffer 为单位读写），块模式更适合批量处理，性能更高。

#### 核心特性



- 容量（Capacity）：Buffer 最大能存储的数据量（创建后不可变）；
- 位置（Position）：下一个要读写的数据的索引（初始为 0，读写时自动递增）；
- 限制（Limit）：当前能读写的数据上限（读模式下 = 写入的数据量；写模式下 = Capacity）；
- 标记（Mark）：用于临时标记一个位置，后续可通过 `reset()` 回到该位置。

#### 常用操作（核心流程：初始化 → 写数据 → 切换读模式 → 读数据 → 清空 / 重置）


```
// 1. 初始化 Buffer（以 ByteBuffer 为例，最常用）
ByteBuffer buffer = ByteBuffer.allocate(1024); // 分配 1024 字节的堆内存 Buffer
// 或 ByteBuffer.allocateDirect(1024); // 直接内存（堆外内存，少一次拷贝，性能更高但创建销毁成本高）

// 2. 写数据到 Buffer（写模式：position 从 0 开始递增）
buffer.put("Hello NIO".getBytes()); // 写入字节数据
System.out.println("写模式：position=" + buffer.position() + ", limit=" + buffer.limit()); // position=9, limit=1024

// 3. 切换为读模式（flip()：limit = position，position = 0）
buffer.flip();
System.out.println("读模式：position=" + buffer.position() + ", limit=" + buffer.limit()); // position=0, limit=9

// 4. 从 Buffer 读数据
byte[] dest = new byte[buffer.remaining()]; // remaining() = limit - position = 9
buffer.get(dest); // 读取数据到 dest
System.out.println(new String(dest)); // 输出 "Hello NIO"
System.out.println("读后：position=" + buffer.position() + ", limit=" + buffer.limit()); // position=9, limit=9

// 5. 清空 Buffer（两种方式）
buffer.clear(); // 重置 position=0, limit=capacity（数据未删除，只是标记重置，后续写会覆盖）
// 或 buffer.compact(); // 压缩：将未读完的数据移到 Buffer 开头，position 指向数据末尾（适合读了部分数据后继续写）
```



#### 常用 Buffer 类型



对应不同的数据类型（类似数组）：`ByteBuffer`（字节，最常用）、`CharBuffer`、`IntBuffer`、`LongBuffer`、`FloatBuffer`、`DoubleBuffer`。

### 2. Channel（通道）：数据的 “传输通道”



#### 核心作用



Channel 是 NIO 中数据传输的 “通道”，类似 BIO 中的 `Socket`，但比 `Socket` 更强大：

- 双向性：既可以读也可以写（BIO 的流是单向的，InputStream 读、OutputStream 写）；
- 非阻塞：支持非阻塞模式（配合 Selector 使用）；
- 基于 Buffer：所有 IO 操作都必须通过 Buffer 进行（不能直接读写数据，必须先读到 Buffer 或从 Buffer 写入）。

#### 常用 Channel 类型



| Channel 类型        | 作用                                | 适用场景                     |
| ------------------- | ----------------------------------- | ---------------------------- |
| ServerSocketChannel | 服务器端通道，监听客户端连接        | TCP 服务器（如 NIO 服务器）  |
| SocketChannel       | 客户端 / 服务器端通道，用于数据传输 | TCP 客户端 / 服务器数据读写  |
| DatagramChannel     | UDP 通道，无连接的数据传输          | UDP 通信（如游戏、直播弹幕） |
| FileChannel         | 文件通道，用于文件读写              | 本地文件 IO（高吞吐量读写）  |

#### 核心操作（以 SocketChannel 为例）





```
// 客户端：创建 SocketChannel 并连接服务器（非阻塞模式）
SocketChannel clientChannel = SocketChannel.open();
clientChannel.configureBlocking(false); // 设置为非阻塞模式
clientChannel.connect(new InetSocketAddress("localhost", 8080)); // 非阻塞连接（立即返回，不会等待连接成功）

// 服务器端：创建 ServerSocketChannel 监听端口
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.bind(new InetSocketAddress(8080));
serverChannel.configureBlocking(false); // 非阻塞模式，accept() 不会阻塞
```



### 3. Selector（选择器）：NIO 的 “大脑”（多路复用核心）



#### 核心作用



Selector 是 NIO 实现 “单线程处理多连接” 的核心，本质是一个「IO 事件监听器」。它可以同时监听多个 Channel 的 IO 事件（如 “数据可读”“连接就绪”“数据可写”），当 Channel 触发某个事件时，Selector 会通知线程处理该 Channel。

#### 核心原理



1. 线程将 Channel 注册到 Selector，并指定要监听的事件（如 `OP_READ` 读事件、`OP_ACCEPT` 连接事件）；
2. 线程调用 Selector 的 `select()` 方法，阻塞等待事件触发（或超时返回）；
3. 当有 Channel 触发事件时，`select()` 方法返回，线程通过 Selector 获取所有 “就绪的 Channel”；
4. 线程遍历就绪的 Channel，处理对应的 IO 操作（如读取数据、接受连接）。

#### 关键概念：SelectorKey



Channel 注册到 Selector 时，会返回一个 `SelectorKey` 对象，它包含：

- 注册的 Channel 引用；
- 监听的事件类型（`interestOps`）；
- 就绪的事件类型（`readyOps`）；
- 附件（Attachment）：可绑定自定义对象（如 Buffer、连接上下文）。

#### 常用事件类型



| 事件常量                | 对应 Channel 类型              | 事件含义                             |
| ----------------------- | ------------------------------ | ------------------------------------ |
| SelectionKey.OP_ACCEPT  | ServerSocketChannel            | 有客户端连接请求就绪                 |
| SelectionKey.OP_READ    | SocketChannel、DatagramChannel | 通道中有数据可读（客户端发来了数据） |
| SelectionKey.OP_WRITE   | SocketChannel、DatagramChannel | 通道可写（缓冲区有空闲，可写入数据） |
| SelectionKey.OP_CONNECT | SocketChannel                  | 客户端连接服务器成功                 |

## 三、NIO 核心工作流程（以 TCP 服务器为例）



用 “单线程 + Selector + 非阻塞 Channel” 实现高并发 TCP 服务器，完整流程如下：

### 步骤 1：初始化 Selector、ServerSocketChannel


```
// 1. 创建 Selector
Selector selector = Selector.open();

// 2. 创建 ServerSocketChannel 并绑定端口
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.bind(new InetSocketAddress(8080));
serverChannel.configureBlocking(false); // 设置为非阻塞模式

// 3. 将 ServerSocketChannel 注册到 Selector，监听 OP_ACCEPT 事件
serverChannel.register(selector, SelectionKey.OP_ACCEPT);
System.out.println("NIO 服务器启动，监听端口 8080...");
```



### 步骤 2：循环监听 Selector 事件（核心循环）


```
while (true) {
    // 4. 阻塞等待事件触发（返回就绪的 Channel 数量，超时 1000ms 可避免永久阻塞）
    int readyChannels = selector.select(1000);
    if (readyChannels == 0) {
        continue; // 无就绪事件，继续循环
    }

    // 5. 获取所有就绪的 SelectorKey（每个 Key 对应一个就绪的 Channel）
    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    Iterator<SelectionKey> iterator = selectedKeys.iterator();

    // 6. 遍历处理每个就绪的 Key
    while (iterator.hasNext()) {
        SelectionKey key = iterator.next();

        // 7. 处理 OP_ACCEPT 事件（客户端连接请求）
        if (key.isAcceptable()) {
            handleAccept(key);
        }

        // 8. 处理 OP_READ 事件（通道中有数据可读）
        if (key.isReadable()) {
            handleRead(key);
        }

        // 9. 处理完成后，移除当前 Key（避免重复处理）
        iterator.remove();
    }
}
```



### 步骤 3：处理连接请求（OP_ACCEPT 事件）

```
private static void handleAccept(SelectionKey key) throws IOException {
    // 1. 获取 ServerSocketChannel（从 Key 中取出注册的 Channel）
    ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

    // 2. 接受客户端连接（非阻塞模式，不会阻塞，因为 OP_ACCEPT 事件已就绪）
    SocketChannel clientChannel = serverChannel.accept();
    clientChannel.configureBlocking(false); // 客户端 Channel 也设为非阻塞

    // 3. 为客户端 Channel 分配 Buffer，并注册到 Selector，监听 OP_READ 事件
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    clientChannel.register(key.selector(), SelectionKey.OP_READ, buffer); // 绑定 Buffer 为附件

    System.out.println("新客户端连接：" + clientChannel.getRemoteAddress());
}
```



### 步骤 4：处理数据读取（OP_READ 事件）

```
private static void handleRead(SelectionKey key) throws IOException {
    // 1. 获取客户端 Channel 和绑定的 Buffer
    SocketChannel clientChannel = (SocketChannel) key.channel();
    ByteBuffer buffer = (ByteBuffer) key.attachment();

    // 2. 从 Channel 读取数据到 Buffer（非阻塞读，数据未就绪时返回 -1）
    int readBytes = clientChannel.read(buffer);
    if (readBytes == -1) {
        // 客户端关闭连接，取消 Key 并关闭 Channel
        key.cancel();
        clientChannel.close();
        System.out.println("客户端断开连接：" + clientChannel.getRemoteAddress());
        return;
    }

    // 3. 切换 Buffer 为读模式，读取数据
    buffer.flip();
    byte[] data = new byte[buffer.remaining()];
    buffer.get(data);
    System.out.println("收到客户端消息：" + new String(data) + "（来自：" + clientChannel.getRemoteAddress() + "）");

    // 4. 重置 Buffer，准备下次读取
    buffer.clear();
}
```



### 核心流程总结



1. 服务器初始化 Selector 和 ServerSocketChannel，注册连接事件；
2. 单线程循环调用 Selector 的 `select()`，等待事件；
3. 有客户端连接时，接受连接并注册读事件；
4. 客户端发数据时，触发读事件，服务器读取并处理数据；
5. 全程单线程处理所有连接，无线程上下文切换开销，支撑高并发。

## 四、NIO 的核心优势与适用场景



### 核心优势



1. **高并发支撑**：单线程处理成千上万个连接，线程开销极低；
2. **非阻塞 IO**：线程无需等待 IO 操作完成，可充分利用 CPU 资源；
3. **多路复用**：Selector 集中管理所有 Channel 的 IO 状态，避免线程轮询每个连接；
4. **高效数据传输**：Buffer 块模式 + 直接内存（少一次堆拷贝），IO 效率高于 BIO；
5. **双向通道**：Channel 支持读写双向操作，简化代码。

### 适用场景



- 高并发网络服务（如 HTTP 服务器、WebSocket 服务器、消息队列服务器）；
- 大数据量 IO 场景（如文件传输、日志收集）；
- 低延迟场景（如高频交易、游戏服务器）；
- 分布式系统中的通信组件（如 RPC 框架底层）。

### 不适用场景



- 低并发、短连接场景（BIO 实现更简单，NIO 的复杂度反而没必要）；
- 单个连接需要大量 CPU 计算的场景（单线程会成为瓶颈，需配合线程池）。

## 五、NIO 的进阶概念（可选深入）



### 1. 直接内存（Direct Buffer）



- 定义：直接内存是堆外内存（不在 JVM 堆中），由操作系统直接管理；
- 优势：IO 操作时无需从堆内存拷贝到直接内存（少一次拷贝，即 “零拷贝”），性能更高；
- 缺点：创建和销毁成本高，不受 JVM 垃圾回收管理（需手动释放，或等待系统回收）；
- 适用场景：大数据量、长连接的 IO 操作（如文件传输、高并发网络通信）。

### 2. 零拷贝（Zero-Copy）



NIO 的 `FileChannel` 支持 `transferTo()`/`transferFrom()` 方法，实现 “零拷贝”：

- 传统文件传输：硬盘 → 内核缓冲区 → 用户缓冲区（JVM 堆）→ 内核缓冲区 → 网络卡；
- 零拷贝传输：硬盘 → 内核缓冲区 → 网络卡（跳过用户缓冲区，减少两次拷贝）；
- 适用场景：大文件传输（如视频、文件下载服务），性能提升显著。

### 3. NIO 2.0（AIO）



JDK 7 引入 NIO 2.0（Asynchronous IO，异步 IO），是对 NIO 的补充：

- 核心区别：NIO 是 “非阻塞同步 IO”（线程需要主动轮询 Selector），AIO 是 “异步 IO”（线程发起 IO 操作后，无需等待，IO 完成后系统通知线程）；
- 适用场景：超大规模并发（如百万级连接），但实现复杂度更高，日常开发中 NIO 已能满足大部分高并发需求（如 Netty 基于 NIO 而非 AIO）。

## 六、常用 NIO 框架（避免重复造轮子）



原生 NIO 代码复杂度较高（需手动处理 Selector、Buffer、异常等），实际开发中常用成熟框架：

- **Netty**：最流行的 NIO 框架，封装了原生 NIO 的复杂性，提供高并发、高可靠的网络通信能力，支持 HTTP、WebSocket、RPC 等协议，广泛用于中间件（如 RocketMQ、Elasticsearch）；
- **Mina**：Apache 旗下的 NIO 框架，类似 Netty，设计简洁，适合快速开发网络应用；
- **Jetty**：基于 NIO 的 Web 服务器，支持 HTTP/2、WebSocket，常用于嵌入式场景。

## 总结



### NIO 核心本质



NIO 是一套基于「非阻塞 + 多路复用」的 IO 模型，通过 Channel（通道）、Buffer（缓冲区）、Selector（选择器）三大组件，实现单线程高效处理多连接，解决了 BIO 在高并发场景下的性能瓶颈。

### 核心关键词



- 非阻塞：线程不等待 IO 操作；
- 多路复用：Selector 监听多个 Channel 的 IO 事件；
- 块模式：以 Buffer 为单位读写数据；
- 高并发：少量线程支撑大量连接。

### 一句话选型



- 低并发、简单场景：用 BIO（实现简单）；
- 高并发、高性能场景：用 NIO 或其框架（Netty）。