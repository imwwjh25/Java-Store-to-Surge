### 一、FutureTask 核心定位



`FutureTask` 是一个**可取消的异步计算任务**，它实现了 `RunnableFuture` 接口（同时继承 `Runnable` 和 `Future`），可以把 `Callable`/`Runnable` 包装成异步任务，提交给 `Executor` 执行，并且支持获取任务结果、取消任务、判断任务状态等核心能力。

### 二、核心设计与关键字段



#### 1. 任务状态（state）



这是 `FutureTask` 最核心的字段，用 `volatile` 保证可见性，所有状态转换都是通过 CAS 操作完成，状态流转是单向的（只能从 NEW 到终态）：



```
private volatile int state;
private static final int NEW          = 0; // 初始状态，任务未执行
private static final int COMPLETING   = 1; // 中间态，任务正在完成（设置结果/异常）
private static final int NORMAL       = 2; // 终态，任务正常完成
private static final int EXCEPTIONAL  = 3; // 终态，任务抛出异常
private static final int CANCELLED    = 4; // 终态，任务被取消（未执行时）
private static final int INTERRUPTING = 5; // 中间态，正在中断执行中的任务
private static final int INTERRUPTED  = 6; // 终态，任务被中断完成
```



**状态流转路径**：

- NEW → COMPLETING → NORMAL（正常完成）
- NEW → COMPLETING → EXCEPTIONAL（执行抛异常）
- NEW → CANCELLED（未执行时被取消）
- NEW → INTERRUPTING → INTERRUPTED（执行中被中断取消）

#### 2. 其他核心字段



| 字段名     | 作用                                                  |
| ---------- | ----------------------------------------------------- |
| `callable` | 包装的异步任务（执行后置为 null，减少内存占用）       |
| `outcome`  | 任务结果（正常完成时存返回值，异常时存 Throwable）    |
| `runner`   | 执行任务的线程（CAS 赋值，防止多线程重复执行）        |
| `waiters`  | 等待任务结果的线程栈（Treiber 栈，基于 CAS 的无锁栈） |

### 三、核心方法解析



#### 1. 构造方法



把 `Runnable`/`Callable` 统一包装成 `Callable`（`Runnable` 会通过 `Executors.callable` 适配成 `Callable`），并初始化状态为 NEW：


```
// 包装 Callable
public FutureTask(Callable<V> callable) {
    if (callable == null)
        throw new NullPointerException();
    this.callable = callable;
    this.state = NEW; // 保证 callable 可见性
}

// 包装 Runnable + 结果
public FutureTask(Runnable runnable, V result) {
    this.callable = Executors.callable(runnable, result);
    this.state = NEW;
}
```



#### 2. 执行任务（run ()）



这是任务执行的核心逻辑，核心是**保证任务只被执行一次**，并处理正常 / 异常完成的情况：



```
public void run() {
    // 双重校验：状态不是 NEW 或 runner CAS 失败 → 直接返回（防止重复执行）
    if (state != NEW || !RUNNER.compareAndSet(this, null, Thread.currentThread()))
        return;
    try {
        Callable<V> c = callable;
        if (c != null && state == NEW) { // 再次校验状态，防止并发取消
            V result;
            boolean ran;
            try {
                result = c.call(); // 执行任务
                ran = true;
            } catch (Throwable ex) {
                result = null;
                ran = false;
                setException(ex); // 任务抛异常，设置异常结果
            }
            if (ran)
                set(result); // 任务正常完成，设置结果
        }
    } finally {
        runner = null; // 清空执行线程
        // 处理可能的中断（如果任务被取消且中断）
        int s = state;
        if (s >= INTERRUPTING)
            handlePossibleCancellationInterrupt(s);
    }
}
```



- `set(result)`：把结果赋值给 `outcome`，并把状态从 COMPLETING 转为 NORMAL，最后唤醒所有等待线程。
- `setException(ex)`：逻辑类似，只是把异常赋值给 `outcome`，状态转为 EXCEPTIONAL。

#### 3. 获取任务结果（get ()）



支持**无超时**和**带超时**两种方式，核心是如果任务未完成，当前线程会阻塞等待：



```
// 无超时 get
public V get() throws InterruptedException, ExecutionException {
    int s = state;
    if (s <= COMPLETING) // 任务未完成 → 阻塞等待
        s = awaitDone(false, 0L);
    return report(s); // 根据最终状态返回结果/抛异常
}

// 带超时 get
public V get(long timeout, TimeUnit unit) 
    throws InterruptedException, ExecutionException, TimeoutException {
    // 省略参数校验...
    int s = state;
    if (s <= COMPLETING && (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
        throw new TimeoutException(); // 超时未完成 → 抛异常
    return report(s);
}
```



##### 核心阻塞逻辑：awaitDone ()



这是实现线程阻塞的核心方法，基于 `LockSupport.park()` 实现，结合 Treiber 栈管理等待线程：


```
private int awaitDone(boolean timed, long nanos) throws InterruptedException {
    long startTime = 0L;
    WaitNode q = null;
    boolean queued = false;
    for (;;) { // 自旋 + CAS 保证线程安全
        int s = state;
        if (s > COMPLETING) { // 任务已完成 → 返回状态
            if (q != null) q.thread = null;
            return s;
        } else if (s == COMPLETING) { // 中间态 → 让出 CPU 等待状态完成
            Thread.yield();
        } else if (Thread.interrupted()) { // 当前线程被中断 → 移除等待节点并抛异常
            removeWaiter(q);
            throw new InterruptedException();
        } else if (q == null) { // 首次进入 → 创建等待节点
            q = new WaitNode();
        } else if (!queued) { // 把节点入栈（Treiber 栈，CAS 操作）
            queued = WAITERS.weakCompareAndSet(this, q.next = waiters, q);
        } else if (timed) { // 带超时 → 计算剩余时间并 park
            // 省略超时计算...
            LockSupport.parkNanos(this, parkNanos);
        } else { // 无超时 → 直接 park 阻塞
            LockSupport.park(this);
        }
    }
}
```



#### 4. 取消任务（cancel ()）



支持两种取消方式：`mayInterruptIfRunning` 表示是否中断正在执行的任务：


```
public boolean cancel(boolean mayInterruptIfRunning) {
    // 只有状态是 NEW 时才能取消 → CAS 修改状态
    if (!(state == NEW && STATE.compareAndSet(this, NEW, 
          mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
        return false;
    try {
        if (mayInterruptIfRunning) { // 需要中断执行中的任务
            Thread t = runner;
            if (t != null) t.interrupt(); // 中断线程
            STATE.setRelease(this, INTERRUPTED); // 状态转为 INTERRUPTED
        }
    } finally {
        finishCompletion(); // 唤醒所有等待线程，清理资源
    }
    return true;
}
```



#### 5. 唤醒等待线程（finishCompletion ()）



任务完成 / 取消后，遍历等待栈，唤醒所有阻塞的线程，并清理资源：



```
private void finishCompletion() {
    for (WaitNode q; (q = waiters) != null;) {
        if (WAITERS.weakCompareAndSet(this, q, null)) { // CAS 清空等待栈
            for (;;) {
                Thread t = q.thread;
                if (t != null) {
                    q.thread = null;
                    LockSupport.unpark(t); // 唤醒线程
                }
                WaitNode next = q.next;
                if (next == null) break;
                q.next = null; // 断开引用，帮助 GC
                q = next;
            }
            break;
        }
    }
    done(); // 空方法，子类可重写扩展
    callable = null; // 清空任务，减少内存占用
}
```



### 四、关键设计亮点



1. **无锁设计**：放弃了早期基于 AQS 的实现，改用 `volatile + CAS` 管理状态和等待栈，避免 AQS 带来的中断状态保留问题；
2. **状态单向流转**：所有状态只能从 NEW 向终态转换，保证了逻辑的简洁性和线程安全；
3. **高效的等待 / 唤醒**：基于 `LockSupport.park/unpark` 实现线程阻塞，结合 Treiber 栈管理等待线程，避免了 synchronized 的重量级锁开销；
4. **防止重复执行**：通过 `runner` 字段的 CAS 赋值，保证同一个任务只能被一个线程执行。

### 总结



`FutureTask` 是 Java 异步编程的核心基础类，核心关键点如下：

1. **状态驱动**：通过 `volatile` 修饰的 `state` 字段控制任务全生命周期，所有状态转换通过 CAS 保证原子性；
2. **异步执行**：包装 `Callable/Runnable` 为可执行任务，支持提交到线程池执行，通过 `get()` 阻塞获取结果；
3. **线程安全**：基于 CAS + 自旋 + LockSupport 实现无锁的线程等待 / 唤醒，保证高并发下的安全性和效率。

理解 `FutureTask` 的设计，能帮你掌握 Java 异步任务的核心实现思路，也是理解线程池（如 `ThreadPoolExecutor`）异步执行逻辑的关键。

为什么要存在超时获取结果 的方法

你想知道 `FutureTask` 为什么要提供带超时的 `get(long timeout, TimeUnit unit)` 方法，核心原因是**避免调用线程无限期阻塞**，这是异步编程中保障系统稳定性和可用性的关键设计。下面我会从实际场景、问题解决和设计原则三个维度，帮你彻底理解这个方法的存在价值。

### 一、核心痛点：无超时 `get()` 的致命问题



如果只有无超时的 `get()` 方法，当异步任务出现以下情况时，调用线程会**永久阻塞**：

1. **任务执行卡死**：异步任务因为死循环、死锁、外部资源（如数据库、网络）挂起等原因，永远无法完成（状态一直是 NEW/COMPLETING）；
2. **任务被恶意 / 意外阻塞**：比如任务依赖的第三方服务超时、网络分区，导致任务无法正常结束；
3. **系统资源耗尽**：大量线程因调用无超时 `get()` 被永久阻塞，最终导致线程池耗尽、系统无法处理新请求。

举个实际例子：

```
// 假设这是一个可能卡死的异步任务
Callable<String> stuckTask = () -> {
    while (true) { // 死循环，任务永远无法完成
        Thread.sleep(1000);
    }
};

FutureTask<String> future = new FutureTask<>(stuckTask);
new Thread(future).start();

// 调用无超时 get() → 当前线程永久阻塞，再也不会执行后续逻辑
String result = future.get(); 
System.out.println("永远执行不到这里");
```



### 二、带超时 `get()` 的核心价值



带超时的 `get` 方法本质是给异步任务设置了 “最大等待容忍度”，解决了上述问题，核心作用体现在：

#### 1. 防止调用线程无限阻塞，保障系统可用性



调用线程只会等待指定时长，超时后会抛出 `TimeoutException`，线程可以捕获异常并执行兜底逻辑（比如重试、返回默认值、降级处理），而不是永久挂起。



```
try {
    // 最多等待3秒，超时则抛出TimeoutException
    String result = future.get(3, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    // 超时兜底逻辑：取消任务 + 返回默认值
    future.cancel(true); // 中断卡死的任务线程
    System.out.println("任务超时，返回默认值");
    return "default";
}
```



#### 2. 支持业务层面的超时控制，符合实际需求



在实际业务中，几乎所有异步操作都有 “超时阈值”：

- 比如调用第三方支付接口，要求最多等 5 秒；
- 比如查询缓存，最多等 1 秒，超时就走数据库查询；
- 比如分布式任务，最多等 30 秒，超时则判定任务失败并告警。

带超时的 `get` 方法直接满足了这类业务需求，无需开发者自己手写超时控制逻辑（比如另起线程监控、CountDownLatch 计时等）。

#### 3. 便于资源回收和故障治理



超时后可以主动调用 `cancel(true)` 中断执行中的任务线程，释放任务占用的资源（如数据库连接、网络句柄）；同时可以记录超时日志、触发告警，便于定位和解决问题（比如识别出慢接口、不稳定的依赖服务）。

### 三、设计层面的补充说明



从 Java 并发框架的设计原则来看，带超时的 `get` 方法也符合 “**优雅失败（Graceful Failure）**” 和 “**资源可控**” 的核心思想：

1. **优雅失败**：不允许线程无限制阻塞，而是通过异常告知调用方 “任务超时”，让调用方有机会处理失败，而非直接导致线程泄漏；
2. **资源可控**：每个调用线程的阻塞时长都被限制，系统能预估最大线程占用量，避免因少量异常任务导致整个系统资源耗尽；
3. **与其他并发工具对齐**：Java 并发包中几乎所有阻塞类方法（如 `Lock.tryLock(timeout)`、`BlockingQueue.poll(timeout)`）都提供超时版本，这是一套统一的设计范式，降低了开发者的学习成本。

### 总结



带超时的 `get` 方法存在的核心价值可总结为 3 点：

1. **避免无限阻塞**：防止调用线程因异步任务异常而永久挂起，保障线程资源不泄漏；
2. **适配业务需求**：满足实际场景中对异步操作的超时控制要求（如接口超时、降级逻辑）；
3. **保障系统稳定性**：通过超时兜底逻辑（取消任务、重试、降级），避免单个任务异常扩散为系统级故障。

简单来说，无超时 `get()` 是 “赌任务一定能完成”，而带超时 `get()` 是 “给任务设定期限，超时就止损”—— 后者才是生产环境中更安全、更实用的选择。