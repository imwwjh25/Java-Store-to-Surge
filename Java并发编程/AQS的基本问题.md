### 一、`ReentrantLock` 的 `lock()` 方法做了什么？（概括）

`ReentrantLock` 的 `lock()` 方法核心是**通过 AQS（AbstractQueuedSynchronizer）实现线程的同步与互斥**，确保同一时刻只有一个线程（或重入的线程）能获取锁，其他线程则会被阻塞并加入等待队列。

具体到 `lock()` 方法的执行流程（以非公平锁为例）：

1. **尝试获取锁**：

   - 调用 `compareAndSetState(0, 1)`（CAS 操作），尝试将 AQS 的 `state` 从 0（无锁状态）改为 1（锁定状态）。
   - 若 CAS 成功，说明当前线程获取锁成功，**将当前线程设置为 AQS 的 `exclusiveOwnerThread`**（标记当前锁的持有者），方法返回。

2. **获取锁失败（已有线程持有锁）**：

   - 若 CAS 失败，进入 `acquire(1)` 方法，该方法会调用 `tryAcquire(1)` 再次尝试获取锁（考虑重入场景）。

   - 在

      

     ```
     tryAcquire(1)
     ```

      

     中：

     - 首先判断当前锁的持有者（`exclusiveOwnerThread`）是否为当前线程。
     - 若是当前线程（重入场景），则将 `state` 加 1（`state` 记录重入次数），方法返回。
     - 若不是当前线程（其他线程持有锁），则获取锁失败，进入下一步。

3. **阻塞并加入等待队列**：

   - 调用 `addWaiter(Node.EXCLUSIVE)`，将当前线程包装为独占模式的节点（`Node`），并加入 AQS 的等待队列（双向链表）。
   - 调用 `acquireQueued(node, 1)`，让当前节点在队列中自旋等待，或阻塞（通过 `LockSupport.park()`），直到获取到锁。
   - 若线程在等待过程中被中断，会根据设置决定是否响应中断（`lock()` 不响应中断，`lockInterruptibly()` 响应中断）。

### 二、关键问题解答（结合面试引导）

#### 1. 只做了 CAS 吗？漏了什么？

**当然不是只做了 CAS**。CAS 只是 “尝试获取锁” 的第一步，后续还有两个关键操作：

- **标记锁的持有者**：CAS 成功后，必须将当前线程设置为 AQS 的 `exclusiveOwnerThread`（这是你面试中被提醒 “漏了” 的核心步骤）。
  - 作用：用于判断后续线程是否为 “重入线程”（同一线程再次获取锁时，通过 `exclusiveOwnerThread == Thread.currentThread()` 验证）。
  - 若不标记持有者，重入机制无法实现（线程再次 CAS 会失败，误以为锁被其他线程占用）。
- **处理获取失败的线程**：CAS 失败后，需要将线程加入等待队列并阻塞，否则线程会一直自旋消耗 CPU，这是 AQS 队列管理的核心价值。

#### 2. 同一线程再次执行 `lock()` 会怎么样？（重入场景）

同一线程再次调用 `lock()` 时，会触发**重入机制**，流程如下：

1. 第一次获取锁时，`state` 从 0 → 1，`exclusiveOwnerThread` 设为当前线程。

2. 同一线程再次调用

    

   ```
   lock()
   ```

   ：

   - 进入 `tryAcquire(1)`，判断 `exclusiveOwnerThread == Thread.currentThread()` → 成立（是当前线程持有锁）。
   - 将 `state` 加 1（`state` 变为 2，记录重入次数）。
   - 方法返回，线程继续执行（无需阻塞）。

**核心**：重入的实现依赖两个关键：

- `state` 记录重入次数（每次重入 `state++`，释放时 `state--`，直到 `state == 0` 才真正释放锁）。
- `exclusiveOwnerThread` 标记当前锁持有者（用于验证是否为同一线程重入）。

#### 3. 换一个线程过来执行 `lock()` 会怎么样？（竞争场景）

其他线程调用 `lock()` 时，流程如下：

1. 尝试 CAS（`state` 从 0 → 1）→ 失败（`state` 已被原线程设为 1 或更高）。

2. 进入 `tryAcquire(1)`，判断 `exclusiveOwnerThread` → 不是当前线程 → 获取失败。

3. 调用 `addWaiter()` 将当前线程加入 AQS 等待队列（队列节点状态设为 `SIGNAL`，表示需要被唤醒）。

4. 调用

    

   ```
   acquireQueued()
   ```

   ，线程在队列中自旋：

   - 若当前节点是队列头节点的后继节点，会再次尝试 `tryAcquire(1)`（可能原线程已释放锁）。
   - 若尝试失败，通过 `LockSupport.park()` 阻塞当前线程（状态变为 `WAITING`）。

5. 直到原线程释放锁（`unlock()` 时 `state--` 到 0，唤醒队列头节点的后继节点），当前线程才可能获取到锁。

#### 4. 为什么原线程可以重入，新线程不能重入？

核心原因是 **AQS 通过 `exclusiveOwnerThread` 标记锁的持有者**：

- **原线程重入**：再次调用 `lock()` 时，`tryAcquire(1)` 会检查 `exclusiveOwnerThread == Thread.currentThread()` → 成立，允许重入（`state++`）。
- **新线程竞争**：调用 `lock()` 时，`tryAcquire(1)` 检查 `exclusiveOwnerThread` → 不是当前线程，且 `state > 0`（锁已被占用），因此不允许获取锁，只能加入等待队列。

简单说：**重入的前提是 “线程身份匹配”，AQS 通过 `exclusiveOwnerThread` 确保只有锁的持有者才能重入**。

#### 5. AQS 有当前线程的变量吗？怎么标记当前线程是否自己？

AQS 内部有一个关键变量 `exclusiveOwnerThread`（定义在 `AbstractOwnableSynchronizer` 中，AQS 继承自该类）：

java



运行









```java
private transient Thread exclusiveOwnerThread;
```

- 作用：**标记当前独占锁的持有者线程**。
- 赋值时机：线程通过 CAS 获取锁成功后，调用 `setExclusiveOwnerThread(Thread.currentThread())` 赋值。
- 验证逻辑：后续线程（或重入线程）通过 `getExclusiveOwnerThread() == Thread.currentThread()` 判断是否为锁的持有者。

#### 6. 再回到 `lock()` 方法：漏了什么？

正如面试中引导的，`lock()` 方法除了 CAS 和队列管理，**最关键的一步是 “将当前线程设置为 `exclusiveOwnerThread`”**。

这一步是连接 “CAS 成功” 和 “重入机制” 的桥梁：

- 没有这一步，AQS 无法判断后续线程是否为同一线程，重入机制会失效。
- 没有这一步，新线程竞争时也无法确认锁的持有者，同步逻辑会混乱。

### 三、延伸问题：AQS 相关

#### 1. AQS 为啥要设计成抽象类？

AQS 设计为抽象类的核心原因是 **“模板方法模式” 的应用**，旨在：

- **定义通用流程**：AQS 封装了同步器的通用逻辑（如队列管理、阻塞 / 唤醒、中断处理），暴露 `tryAcquire(int)`、`tryRelease(int)` 等抽象方法，让子类（如 `ReentrantLock`、`Semaphore`）实现具体的同步策略。
- **复用代码**：避免不同同步工具（锁、信号量、倒计时器）重复实现队列、阻塞等底层逻辑。
- **灵活性**：子类可根据需求实现独占模式（`ReentrantLock`）、共享模式（`Semaphore`、`CountDownLatch`），或两者结合（`ReadWriteLock`）。

简单说：AQS 是 “同步器骨架”，子类是 “具体实现”，抽象类的设计让骨架与实现解耦，兼顾通用性和灵活性。

#### 2. 只用 AQS 的 `state` 和队列，能实现哪些并发工具？

AQS 的核心组件是 `state`（状态）、`exclusiveOwnerThread`（独占持有者）、队列（等待线程管理）。其中，**`state` 和队列是基础，`exclusiveOwnerThread` 主要用于独占锁的重入**。

只用 `state` 和队列就能实现的并发工具：

- **`Semaphore`（信号量）**：
  - 核心逻辑：`state` 表示可用许可证数量，线程通过 `acquire()` 尝试获取许可证（`state--`），通过 `release()` 释放许可证（`state++`）。
  - 实现：
    - `tryAcquireShared(int)`：判断 `state >= permits`，若是则 `state -= permits`，返回剩余许可证数量（>=0 表示成功）。
    - 若获取失败，线程加入队列阻塞。
    - 无需 `exclusiveOwnerThread`（共享模式，多个线程可同时获取许可证）。
- **`CountDownLatch`（倒计时器）**：
  - 核心逻辑：`state` 表示倒计时次数，线程通过 `await()` 等待 `state` 变为 0，其他线程通过 `countDown()` 减少 `state`。
  - 实现：
    - `tryAcquireShared(int)`：判断 `state == 0`，若是则返回 0（成功），否则返回 -1（失败）。
    - 失败的线程加入队列阻塞，直到 `state` 变为 0 被唤醒。
    - 无需 `exclusiveOwnerThread`（共享模式，多个线程可同时等待）。
- **`CyclicBarrier`（循环屏障）**：
  - 核心逻辑：`state` 表示等待的线程数，线程调用 `await()` 时 `state--`，直到 `state == 0` 时所有线程被同时唤醒。
  - 实现：基于 AQS 的共享模式，`state` 记录等待线程数，队列管理等待的线程，无需 `exclusiveOwnerThread`。

**核心结论**：共享模式的并发工具（`Semaphore`、`CountDownLatch` 等）只需依赖 AQS 的 `state`（状态管理）和队列（等待线程管理），无需 `exclusiveOwnerThread`；而独占模式的并发工具（`ReentrantLock`）需要 `exclusiveOwnerThread` 实现重入机制。

### 总结

1. `ReentrantLock` 的 `lock()` 方法核心是**通过 AQS 实现同步**，关键步骤包括：CAS 尝试获取锁、标记锁持有者（`exclusiveOwnerThread`）、重入判断、队列管理与阻塞。
2. 重入的实现依赖 `state`（重入次数）和 `exclusiveOwnerThread`（线程身份匹配）。
3. AQS 设计为抽象类是为了通过模板方法模式复用通用逻辑，子类实现具体同步策略。
4. 共享模式的并发工具（如 `Semaphore`、`CountDownLatch`）可仅通过 AQS 的 `state` 和队列实现，无需 `exclusiveOwnerThread`。
