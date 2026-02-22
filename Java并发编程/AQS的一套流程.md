AQS（AbstractQueuedSynchronizer）是 Java 并发包（`java.util.concurrent`）的核心基础组件，几乎所有同步工具（`ReentrantLock`、`Semaphore`、`CountDownLatch` 等）都基于它实现。其核心设计是 **“状态管理 + FIFO 等待队列”**，而非公平锁是 AQS 最常用的实现（如 `ReentrantLock` 默认非公平），效率高于公平锁。

下面从「AQS 整体设计→核心组件→非公平锁实现→与公平锁对比」的逻辑，完整拆解 AQS 一整套机制：

### 一、先搞懂 AQS 核心定位与设计思想



#### 1. 核心定位



AQS 是一个 **抽象同步器框架**，它封装了 “同步状态管理”“线程阻塞 / 唤醒”“等待队列维护” 的通用逻辑，让开发者只需重写少量钩子方法，就能快速实现自定义同步工具（无需关注底层并发细节）。

#### 2. 核心设计思想



AQS 的核心是 **“基于状态的同步”**，所有逻辑围绕两个核心要素展开：

- **同步状态（State）**：用 `volatile int state` 修饰（保证可见性、禁止指令重排），代表同步资源的可用状态（如：`ReentrantLock` 中 `state=0` 表示未锁定，`state>0` 表示已锁定且记录重入次数；`Semaphore` 中 `state` 表示可用许可数）。
- **FIFO 等待队列**：也称 “CLH 队列”（虚拟双向链表），当线程竞争同步状态失败时，会被封装成「节点（Node）」加入队列尾部，进入阻塞状态；当同步状态释放时，会唤醒队列头部的线程重新竞争。

#### 3. AQS 核心钩子方法（开发者需重写的方法）



AQS 定义了 5 个抽象 / 模板方法，子类需根据自身同步逻辑重写（无需重写全部，按需实现），核心是通过 CAS 操作修改 `state`：

| 钩子方法                    | 作用                         | 典型实现场景                                  |
| --------------------------- | ---------------------------- | --------------------------------------------- |
| `tryAcquire(int arg)`       | 尝试获取同步状态（独占模式） | `ReentrantLock` 锁定时，尝试 CAS 置位 `state` |
| `tryRelease(int arg)`       | 尝试释放同步状态（独占模式） | `ReentrantLock` 解锁时，重置 `state` 为 0     |
| `tryAcquireShared(int arg)` | 尝试获取同步状态（共享模式） | `Semaphore` 获取许可时，`state` 减 1          |
| `tryReleaseShared(int arg)` | 尝试释放同步状态（共享模式） | `Semaphore` 释放许可时，`state` 加 1          |
| `isHeldExclusively()`       | 判断当前线程是否独占同步状态 | `ReentrantLock` 判断当前线程是否持有锁        |

**关键**：AQS 已经实现了「队列管理」「线程阻塞 / 唤醒」的通用逻辑（如 `acquire`、`release` 方法），子类只需专注于 `state` 的竞争与释放（钩子方法）。

### 二、AQS 核心组件详解（队列 + 节点 + 状态）



#### 1. 同步状态（State）



- 类型：`private volatile int state`（volatile 保证多线程可见性）；
- 操作方式：通过 CAS 原子操作修改（AQS 提供 `compareAndSetState(int expect, int update)` 方法，底层调用 Unsafe 类的 CAS 指令）；
- 核心原则：只有通过 CAS 成功修改 `state` 的线程，才能获得同步资源；失败的线程进入等待队列。

#### 2. 等待队列（CLH 队列）



- 结构：虚拟双向链表，每个节点是 `AbstractQueuedSynchronizer.Node` 类实例；
- 节点核心属性：
  - `int waitStatus`：节点状态（-1=SIGNAL：后续节点需被唤醒；0 = 初始状态；1=CANCELLED：节点已取消；-2=CONDITION：节点在条件队列中；-3=PROPAGATE：共享模式下状态传播）；
  - `Node prev`：前驱节点；
  - `Node next`：后继节点；
  - `Thread thread`：当前节点对应的线程；
- 队列核心引用：
  - `head`：队列头节点（已获取同步资源的线程）；
  - `tail`：队列尾节点（最新加入队列的线程）；
- 特点：无锁化设计，节点的入队、出队都通过 CAS 操作实现，避免了队列本身的线程安全问题。

#### 3. 线程阻塞 / 唤醒机制



AQS 依赖 `LockSupport` 工具类实现线程阻塞与唤醒：

- 阻塞：`LockSupport.park()`（让当前线程进入 WAITING 状态，可响应中断）；
- 唤醒：`LockSupport.unpark(Thread t)`（唤醒指定线程，使其从 park 状态恢复）；
- 优势：比 `Object.wait()` 更灵活，无需依赖同步锁，可直接操作线程。

### 三、AQS 非公平锁实现（以 ReentrantLock 为例）



AQS 的锁实现分为「独占模式」和「共享模式」：非公平锁是 **独占模式** 的典型实现（同一时间只有一个线程能获取锁），`ReentrantLock` 的非公平锁底层就是 AQS 的非公平实现。

下面结合 `ReentrantLock` 源码，拆解 AQS 非公平锁的核心流程：**获取锁（acquire）→ 释放锁（release）**。

#### 1. 非公平锁的核心特点



- 「非公平」体现在：线程获取锁时，会先 “插队” 尝试 CAS 抢锁（不按队列顺序），只有抢锁失败后，才会加入等待队列；
- 优势：减少线程切换开销（无需等待队列唤醒），并发效率更高；
- 劣势：可能导致队列头部线程长期抢不到锁（饥饿），但实际场景中概率极低，效率优先于绝对公平。

#### 2. 获取锁流程（`acquire(1)` 方法，`arg=1` 表示获取 1 个锁资源）



AQS 的 `acquire` 是模板方法，定义了获取锁的通用逻辑，核心步骤：

java

运行

```
// AQS 模板方法：独占模式获取锁，失败则入队阻塞
public final void acquire(int arg) {
    // 1. 尝试获取锁（调用子类重写的 tryAcquire）；2. 失败则入队；3. 阻塞线程
    if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
        selfInterrupt(); // 若线程在阻塞中被中断，恢复中断状态
    }
}
```



拆解每个步骤的细节：

##### （1）步骤 1：`tryAcquire(1)`—— 非公平抢锁（子类 ReentrantLock 重写）



非公平锁的核心是 “先插队抢锁”，`tryAcquire` 逻辑：

java

运行

```
// ReentrantLock 非公平锁的 tryAcquire 实现
protected final boolean tryAcquire(int acquires) {
    return nonfairTryAcquire(acquires);
}

// 非公平抢锁核心逻辑
final boolean nonfairTryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState(); // 获取当前同步状态
    if (c == 0) { // 状态为 0：锁未被持有
        // 插队 CAS 抢锁（不检查队列，直接尝试修改 state）
        if (compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current); // 标记当前线程为锁持有者
            return true;
        }
    }
    // 状态不为 0：检查是否是当前线程重入（可重入锁特性）
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires; // 重入次数+1
        if (nextc < 0) throw new Error("Maximum lock count exceeded");
        setState(nextc); // 重入无需 CAS（当前线程已持有锁，无竞争）
        return true;
    }
    // 抢锁失败（锁被其他线程持有）
    return false;
}
```



- 关键：锁未被持有时，线程直接 CAS 抢锁，不考虑队列顺序，体现 “非公平”；
- 可重入特性：当前线程已持有锁时，直接递增 `state`，无需再次 CAS。

##### （2）步骤 2：`addWaiter(Node.EXCLUSIVE)`—— 抢锁失败，封装成节点入队



如果 `tryAcquire` 失败，线程会被封装成「独占模式节点（Node.EXCLUSIVE）」，加入等待队列尾部：

java

运行

```
// AQS 方法：将线程封装为节点，加入队列尾部
private Node addWaiter(Node mode) {
    Node node = new Node(Thread.currentThread(), mode); // 创建节点
    Node pred = tail;
    if (pred != null) { // 队列不为空，尝试 CAS 入队
        node.prev = pred;
        if (compareAndSetTail(pred, node)) { // CAS 设为新尾节点
            pred.next = node;
            return node;
        }
    }
    enq(node); // 队列为空或 CAS 入队失败，自旋入队（确保入队成功）
    return node;
}

// 自旋入队：循环 CAS 直到入队成功
private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        if (t == null) { // 队列未初始化，CAS 初始化头节点（空节点）
            if (compareAndSetHead(new Node())) {
                tail = head;
            }
        } else {
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}
```



- 队列初始化：首次入队时，会创建一个 “空节点” 作为头节点（头节点是已获取锁的线程，空节点是初始状态）；
- 无锁入队：通过 CAS 操作保证节点入队的线程安全，无需额外锁。

##### （3）步骤 3：`acquireQueued(node, arg)`—— 节点入队后，阻塞线程



节点入队后，线程会进入 “自旋 + 阻塞” 状态，直到获取锁或被中断：

java

运行

```
// AQS 方法：节点在队列中自旋抢锁，失败则阻塞
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) { // 自旋
            final Node p = node.predecessor(); // 获取前驱节点
            // 前驱是头节点：说明当前节点是队列第二个节点，可尝试抢锁
            if (p == head && tryAcquire(arg)) {
                setHead(node); // 抢锁成功，当前节点设为新头节点（原头节点出队）
                p.next = null; // 断开原头节点，帮助 GC
                failed = false;
                return interrupted; // 返回是否被中断
            }
            // 抢锁失败：判断是否需要阻塞，避免无效自旋
            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                interrupted = true; // 线程被中断，标记中断状态
            }
        }
    } finally {
        if (failed) {
            cancelAcquire(node); // 抢锁失败且异常，取消当前节点
        }
    }
}
```



- 自旋优化：只有当前驱节点是头节点时，才尝试抢锁（队列 FIFO 特性，确保有序性）；
- 阻塞条件：`shouldParkAfterFailedAcquire` 会检查前驱节点状态，若前驱是 SIGNAL（-1），则当前线程可以安全阻塞；
- 阻塞实现：`parkAndCheckInterrupt()` 调用 `LockSupport.park(this)`，让线程进入阻塞状态，直到被唤醒。

#### 3. 释放锁流程（`release(1)` 方法）



当线程执行完逻辑后，调用 `release` 释放锁，核心是 “重置 state + 唤醒队列线程”：

java

运行

```
// AQS 模板方法：独占模式释放锁
public final boolean release(int arg) {
    if (tryRelease(arg)) { // 调用子类重写的 tryRelease，重置 state
        Node h = head;
        if (h != null && h.waitStatus != 0) { // 头节点不为空且状态有效
            unparkSuccessor(h); // 唤醒头节点的后继节点
        }
        return true;
    }
    return false;
}
```



拆解细节：

##### （1）步骤 1：`tryRelease(1)`—— 重置同步状态（子类 ReentrantLock 重写）



java

运行

```
// ReentrantLock 的 tryRelease 实现
protected final boolean tryRelease(int releases) {
    int c = getState() - releases; // 重入次数-1
    if (Thread.currentThread() != getExclusiveOwnerThread()) {
        throw new IllegalMonitorStateException(); // 非锁持有者不能释放
    }
    boolean free = false;
    if (c == 0) { // 重入次数减为 0，锁完全释放
        free = true;
        setExclusiveOwnerThread(null); // 清空锁持有者
    }
    setState(c); // 重置 state（c>0 时，只是减少重入次数）
    return free;
}
```



- 可重入释放：只有 `state` 减为 0 时，才视为锁完全释放；
- 线程校验：确保只有锁持有者才能释放锁，避免非法释放。

##### （2）步骤 2：`unparkSuccessor(h)`—— 唤醒后继节点



锁完全释放后，唤醒头节点的后继节点，让其重新抢锁：

java

运行

```
// AQS 方法：唤醒头节点的后继节点
private void unparkSuccessor(Node node) {
    int ws = node.waitStatus;
    if (ws < 0) {
        compareAndSetWaitStatus(node, ws, 0); // 重置头节点状态为 0
    }
    Node s = node.next;
    // 后继节点为空或已取消，从队尾向前找第一个有效节点（避免跳过取消节点）
    if (s == null || s.waitStatus > 0) {
        s = null;
        for (Node t = tail; t != null && t != node; t = t.prev) {
            if (t.waitStatus <= 0) {
                s = t;
            }
        }
    }
    if (s != null) {
        LockSupport.unpark(s.thread); // 唤醒有效后继节点的线程
    }
}
```



- 唤醒逻辑：唤醒头节点的后继节点，被唤醒的线程会从 `parkAndCheckInterrupt()` 恢复，继续自旋抢锁；
- 边界处理：跳过已取消的节点，确保唤醒的是队列中第一个有效线程。

### 四、AQS 公平锁 vs 非公平锁（核心差异）



公平锁的实现也是基于 AQS，与非公平锁的核心差异仅在「获取锁的时机」，对比如下：

| 对比维度     | 非公平锁（ReentrantLock 默认）                 | 公平锁（ReentrantLock 构造参数指定）               |
| ------------ | ---------------------------------------------- | -------------------------------------------------- |
| 抢锁逻辑     | 先插队 CAS 抢锁，失败后入队                    | 先检查队列是否有等待线程，无则 CAS 抢锁，有则入队  |
| 核心方法差异 | `tryAcquire` 不检查队列（`nonfairTryAcquire`） | `tryAcquire` 检查队列（`hasQueuedPredecessors()`） |
| 效率         | 高（减少线程切换，插队成功率高）               | 低（严格按队列顺序，线程切换频繁）                 |
| 饥饿问题     | 可能出现（队列头线程长期抢不到锁）             | 无（绝对公平，按 FIFO 顺序）                       |
| 适用场景     | 并发高、追求效率的场景（如互联网服务）         | 对公平性要求高的场景（如金融交易）                 |

**关键方法差异**：公平锁的 `tryAcquire` 会调用 `hasQueuedPredecessors()` 检查队列是否有等待线程：

java

运行

```
// 公平锁的 tryAcquire 实现
protected final boolean tryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        // 先检查队列是否有前驱线程（hasQueuedPredecessors()），无则 CAS 抢锁
        if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    // 重入逻辑（与非公平锁一致）
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0) throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}

// 检查队列是否有等待线程（公平锁核心）
public final boolean hasQueuedPredecessors() {
    Node t = tail;
    Node h = head;
    Node s;
    // 队列不为空，且头节点的后继节点不是当前线程 → 有等待线程
    return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
}
```



### 五、AQS 核心总结（面试直接答）



1. AQS 是 Java 并发包的基础，核心是「同步状态（state）+ FIFO 等待队列」，封装了通用的同步逻辑；
2. 子类通过重写 `tryAcquire`/`tryRelease` 等钩子方法，实现自定义同步工具（独占 / 共享模式）；
3. 非公平锁是 AQS 独占模式的典型实现：先插队 CAS 抢锁，失败后入队阻塞，释放锁时唤醒后继节点，效率高；
4. 公平锁与非公平锁的核心差异：是否检查等待队列（公平锁严格按 FIFO，非公平锁允许插队）；
5. 典型应用：`ReentrantLock`（独占锁）、`Semaphore`（共享锁）、`CountDownLatch`（共享锁）、`CyclicBarrier`（基于 Condition）。

记住：AQS 的本质是「用 CAS 管理状态，用队列管理竞争线程，用 LockSupport 实现阻塞 / 唤醒」，非公平锁的核心是 “插队抢锁”，这也是它效率高的原因。
