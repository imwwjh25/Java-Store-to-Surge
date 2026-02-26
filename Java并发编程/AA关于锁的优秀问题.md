## Java 中有哪些锁？Java 技术栈中有哪些锁的实现方式？除了锁，还有别的形式会处于阻塞状态吗？

### （1）Java 中的锁分类（按不同维度）

#### ① 按锁的实现方式分类

- synchronized：
    - 关键字级别的锁，由 JVM 实现，底层依赖对象头的`Monitor`锁。
    - 可重入、非公平锁（JDK 6 后优化为偏向锁、轻量级锁、重量级锁的自适应升级）。

- Lock 接口 ：

    - 手动锁，需显式调用`lock()`和`unlock()`，灵活性更高。
    - 常用实现：`ReentrantLock`（可重入、支持公平 / 非公平锁）、`ReentrantReadWriteLock`（读写分离锁）、`StampedLock`（乐观读锁）。

#### ② 按锁的竞争特性分类

- 公平锁 ：

    - 线程获取锁的顺序遵循请求顺序（FIFO），避免饥饿。
    - 实现：`ReentrantLock`的`fair=true`构造函数。

- 非公平锁 ：

    - 线程获取锁的顺序不固定，允许 “插队”，性能更高。
    - 实现：`synchronized`、`ReentrantLock`默认（`fair=false`）。

#### ③ 按锁的粒度分类

- 偏向锁 ：

    - 针对单线程重复获取锁的场景，避免 CAS 操作，提高性能。
    - JDK 6 引入，默认开启。

- 轻量级锁 ：

    - 多个线程交替获取锁，通过 CAS 自旋尝试获取锁，避免阻塞。
    - 竞争激烈时升级为重量级锁。

- 重量级锁 ：

    - 依赖操作系统的互斥量（Mutex）实现，线程竞争失败会阻塞，切换成本高。

#### ④ 其他特殊锁

- 可重入锁 ：

    - 同一线程可多次获取同一把锁，避免死锁。
    - 实现：`synchronized`、`ReentrantLock`。

- 读写锁 ：

    - 允许多个线程同时读，仅允许一个线程写，提高读多写少场景的并发度。
    - 实现：`ReentrantReadWriteLock`。

- 乐观锁 ：

    - 基于 CAS 实现，不阻塞线程，仅在提交时检查冲突。
    - 实现：`AtomicInteger`、`StampedLock`的乐观读模式。

- 悲观锁 ：

    - 认为并发冲突必然发生，获取锁前先阻塞其他线程。
    - 实现：`synchronized`、`ReentrantLock`的默认模式。

### （2）Java 技术栈中锁的实现方式

#### ① JVM 层面实现

- synchronized ：

    - 底层通过对象头的`Mark Word`存储锁状态，依赖`Monitor`（管程）实现互斥。
    - 升级流程：无锁 → 偏向锁 → 轻量级锁 → 重量级锁。

#### ② Java API 层面实现

- Lock 接口 ：

    - `ReentrantLock`：基于 AQS（AbstractQueuedSynchronizer）实现，通过 CAS + 队列管理线程竞争。
    - `ReentrantReadWriteLock`：内部维护读锁（共享锁）和写锁（排他锁），通过 AQS 的共享模式和排他模式实现。
    - `StampedLock`：结合了读写锁和乐观锁的特性，支持三种模式：写锁、悲观读锁、乐观读锁。

#### ③ 分布式锁实现（跨 JVM）

- **基于 Redis**：利用`SETNX`命令或 Lua 脚本实现，依赖 Redis 的原子性。
- **基于 ZooKeeper**：利用临时有序节点和 Watcher 机制实现，保证强一致性。
- **基于数据库**：利用唯一索引或悲观锁实现，适用于低并发场景。

### （3）除了锁，其他导致线程阻塞的形式

线程阻塞的核心是 **等待某个条件满足**，除了锁竞争，还有以下场景：

1. I/O 阻塞 ：

    - 线程执行 I/O 操作（如文件读写、网络通信）时，会阻塞直到 I/O 完成。
    - 示例：`InputStream.read()`、`Socket.accept()`。

2. 等待其他线程通知 ：

    - 线程调用`Object.wait()`或`Condition.await()`后，会释放锁并阻塞，直到其他线程调用`notify()`/`notifyAll()`或`signal()`/`signalAll()`。

3. 线程睡眠 ：

    - 调用`Thread.sleep(long)`会让线程阻塞指定时间，期间不释放锁。

4. join () 阻塞 ：

    - 线程 A 调用线程 B 的`join()`，会阻塞直到线程 B 执行完毕。

5. LockSupport.park()：

    - 底层阻塞方法，`Lock`接口和 AQS 内部广泛使用，可通过`unpark()`唤醒。

6. 死锁 ：

    - 多个线程互相等待对方释放锁，导致永久阻塞。

------

## 8. 获取锁等待过程中，线程消耗 CPU 资源吗？

**分两种情况**：

### （1）自旋锁（轻量级锁）：消耗 CPU 资源

- **原理**：线程竞争锁时，不立即阻塞，而是通过 **CAS 循环尝试获取锁**（自旋）。
- **CPU 消耗**：自旋过程中，线程持续执行循环检查，会消耗 CPU 资源（相当于 “空转”）。
- **适用场景**：锁持有时间短、竞争不激烈的场景，避免线程阻塞 / 唤醒的切换成本。
- **示例**：`ReentrantLock`的非公平锁默认会自旋几次（JDK 6 后由 JVM 自适应调整）。

### （2）阻塞锁（重量级锁）：不消耗 CPU 资源

- **原理**：线程竞争锁失败时，会 **放弃 CPU 执行权**，进入阻塞状态（`BLOCKED`或`WAITING`），等待操作系统调度。
- **CPU 消耗**：阻塞期间，线程不执行任何代码，CPU 资源被其他线程占用，几乎不消耗 CPU。
- **适用场景**：锁持有时间长、竞争激烈的场景，避免自旋导致的 CPU 空转。
- **示例**：`synchronized`升级为重量级锁后，竞争失败的线程会阻塞在`Monitor`的入口。

### 总结

- **自旋锁**：消耗 CPU，适合短时间等待。
- **阻塞锁**：不消耗 CPU，适合长时间等待。
- JDK 6 后，`synchronized`和`ReentrantLock`都采用 **自适应锁策略**：根据锁的竞争情况自动切换自旋和阻塞，平衡性能和资源消耗。

------

## 9. 自旋中的线程处于什么状态？

**处于 `RUNNABLE` 状态**。

### 关键解析：

- **自旋的本质**：线程在 **用户态** 循环执行 CAS 操作，并未放弃 CPU 执行权。

- 线程状态定义 ：

    - `RUNNABLE`：线程正在执行，或等待 CPU 调度（就绪状态）。
    - `BLOCKED`：线程等待锁释放（如`synchronized`竞争失败）。
    - `WAITING`/`TIMED_WAITING`：线程等待其他条件（如`wait()`、`sleep()`）。

- **自旋的特殊性**：自旋时，线程仍在执行代码（循环检查锁状态），因此属于 `RUNNABLE` 状态，而非阻塞状态。

### 示例验证：







```java
ReentrantLock lock = new ReentrantLock();
new Thread(() -> {
    lock.lock(); // 持有锁不释放
    try { Thread.sleep(10000); } catch (InterruptedException e) {}
}).start();

new Thread(() -> {
    System.out.println(Thread.currentThread().getState()); // RUNNABLE（自旋中）
    lock.lock(); // 竞争锁，自旋一段时间后阻塞
}).start();
```

------

## 总结

1. **Java 锁分类**：按实现方式（`synchronized`/`Lock`）、竞争特性（公平 / 非公平）、粒度（偏向 / 轻量 / 重量级）等维度划分，核心是平衡并发安全性和性能。
2. **锁的实现**：JVM 层面依赖`Monitor`，API 层面依赖 AQS，分布式场景依赖中间件（Redis/ZK）。
3. **线程阻塞场景**：除了锁竞争，还有 I/O、`wait()`、`sleep()`、`join()`等。
4. **自旋锁**：消耗 CPU，状态为`RUNNABLE`；阻塞锁：不消耗 CPU，状态为`BLOCKED`/`WAITING`。

这些知识点是 Java 并发编程的核心，面试中常结合实际场景（如锁优化、死锁排查）考察，建议结合源码（如`ReentrantLock`、AQS）深入理解。

但是我好像记得在java中的线程状态中 只有获得锁失败才会进入到bloced的状态

你的理解非常正确！

在 Java 中，线程的 **`BLOCKED` 状态** 确实**仅针对「获取锁失败」的场景**—— 更具体地说，是线程在竞争 **`synchronized` 关键字** 锁时失败，才会进入 `BLOCKED` 状态。

这是一个非常关键的细节，我们来详细拆解，避免与其他阻塞场景混淆：

### 一、先明确：Java 线程的 6 种核心状态（官方定义）

根据 JVM 规范，线程的生命周期包含 6 种状态，**`BLOCKED` 是其中唯一与「锁竞争失败」强绑定的状态**：

| 状态            | 核心含义                                                     |
| --------------- | ------------------------------------------------------------ |
| `NEW`           | 线程刚创建，尚未调用 `start()` 方法。                        |
| `RUNNABLE`      | 线程正在执行，或等待 CPU 调度（就绪状态）。                  |
| `BLOCKED`       | 线程竞争 **`synchronized` 锁** 失败，等待锁释放。（仅针对 `synchronized`） |
| `WAITING`       | 线程主动放弃 CPU 执行权，等待其他线程的「通知」（如 `wait()`、`join()`）。 |
| `TIMED_WAITING` | 线程在指定时间内等待通知（如 `sleep(time)`、`wait(time)`、`join(time)`）。 |
| `TERMINATED`    | 线程执行完毕或异常终止。                                     |

### 二、关键区分：`BLOCKED` 与其他阻塞状态的核心差异

很多人会把 `BLOCKED` 和 `WAITING`/`TIMED_WAITING` 混淆，但它们的触发场景完全不同 ——**`BLOCKED` 仅因「抢 `synchronized` 锁失败」，而其他阻塞状态与锁竞争无关**：

#### 1. `BLOCKED` 状态的唯一触发条件

线程尝试进入一个 **`synchronized` 代码块 / 方法**，但锁已被其他线程持有，此时线程会被 JVM 放入「锁等待队列」，状态变为 `BLOCKED`。

- 示例：









  ```java
  synchronized void test() { /* ... */ }
  
  Thread A = new Thread(this::test);
  Thread B = new Thread(this::test);
  A.start(); 
  B.start(); // B 会因抢不到锁进入 BLOCKED 状态
  ```



- 特点：**被动等待**（无需主动调用方法），锁释放后 JVM 会自动唤醒线程重新竞争。

#### 2. 其他阻塞状态（`WAITING`/`TIMED_WAITING`）的触发条件

这些状态的线程**并未在竞争锁**，而是主动放弃执行权，等待某个「特定条件」满足：

| 状态            | 触发方式（示例）                                             | 核心特点                       |
| --------------- | ------------------------------------------------------------ | ------------------------------ |
| `WAITING`       | `object.wait()`、`thread.join()`、`LockSupport.park()`       | 无限期等待，需其他线程通知唤醒 |
| `TIMED_WAITING` | `Thread.sleep(time)`、`object.wait(time)`、`LockSupport.parkNanos()` | 限时等待，超时自动唤醒         |

- 示例：







  ```java
  Object lock = new Object();
  synchronized (lock) {
      lock.wait(); // 线程主动释放锁，进入 WAITING 状态
  }
  ```



- 特点：**主动触发**（需调用特定方法），与 `synchronized` 锁竞争无关。

### 三、补充：`Lock` 接口竞争失败的状态？

你可能会好奇：如果用 `ReentrantLock`（`Lock` 接口实现）竞争锁失败，线程状态是什么？答案是：**`WAITING` 或 `TIMED_WAITING`**，而非 `BLOCKED`！

因为 `Lock` 接口的锁竞争依赖 **AQS（AbstractQueuedSynchronizer）**，线程竞争失败时，会通过 `LockSupport.park()` 主动阻塞，状态变为 `WAITING`（或 `TIMED_WAITING`，若用 `tryLock(time)`）。

这是 `synchronized` 与 `Lock` 接口的核心差异之一：

| 锁类型                  | 竞争失败后的线程状态      | 等待队列                 |
| ----------------------- | ------------------------- | ------------------------ |
| `synchronized`          | `BLOCKED`                 | JVM 内部的「锁等待队列」 |
| `ReentrantLock`（Lock） | `WAITING`/`TIMED_WAITING` | AQS 的「阻塞队列」       |

### 四、总结（核心结论）

你的记忆是准确的：

> **Java 中 `BLOCKED` 状态的唯一场景是：线程竞争 `synchronized` 锁失败。**

其他所有阻塞场景（如 `sleep()`、`wait()`、`join()`、`Lock` 竞争失败），线程状态都是 `WAITING` 或 `TIMED_WAITING`，与 `BLOCKED` 无关。

这个细节在面试中经常被考察，用于区分对线程模型和锁机制的理解深度～



在Java中，线程在等待锁时**基本不消耗CPU资源**，但具体情况取决于锁的类型和实现机制。以下是详细分析：

## 1. synchronized 关键字



```
public class SynchronizedExample {
    private final Object lock = new Object();
    
    public void method() {
        synchronized(lock) { // 等待锁时线程进入BLOCKED状态
            // 临界区代码
        }
    }
}
```



- **线程状态**: `BLOCKED`
- **CPU消耗**: 几乎为零
- **机制**: 线程进入操作系统级别的等待队列，由操作系统调度，不占用CPU

## 2. ReentrantLock



```
public class ReentrantLockExample {
    private final ReentrantLock lock = new ReentrantLock();
    
    public void method() {
        lock.lock(); // 等待锁时可能涉及少量自旋
        try {
            // 临界区代码
        } finally {
            lock.unlock();
        }
    }
}
```



- **线程状态**: `WAITING` 或 `TIMED_WAITING`
- **CPU消耗**: 极低（内部使用AQS，可能有短暂自旋）
- **机制**: 使用park/unpark机制，线程挂起

## 3. 自旋锁（特殊情况）

java

```
// 自定义自旋锁示例 - 不推荐在实际使用
public class SpinLock {
    private AtomicBoolean locked = new AtomicBoolean(false);
    
    public void lock() {
        while (!locked.compareAndSet(false, true)) {
            // 忙等待 - 消耗CPU!
        }
    }
}
```



- **线程状态**: `RUNNABLE`
- **CPU消耗**: 高
- **机制**: 循环检查锁状态，持续占用CPU

## 4. 验证示例



```
public class LockCPUProfile {
    private static final Object lock = new Object();
    
    public static void main(String[] args) {
        // 线程1持有锁
        new Thread(() -> {
            synchronized(lock) {
                try {
                    System.out.println("Thread1 holding lock");
                    Thread.sleep(5000); // 持有5秒
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        
        // 线程2等待锁
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 确保线程1先获取锁
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            long start = System.currentTimeMillis();
            synchronized(lock) { // 这里会等待
                long end = System.currentTimeMillis();
                System.out.println("Thread2 waited: " + (end - start) + "ms");
            }
        }).start();
    }
}
```



## 5. 监控线程状态



```
public class ThreadStateMonitor {
    public static void monitorThreadState(Thread thread) {
        new Thread(() -> {
            while (true) {
                System.out.println("Thread state: " + thread.getState());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }
}
```



## 总结

| 锁类型        | 线程状态 | CPU消耗  | 适用场景           |
| :------------ | :------- | :------- | :----------------- |
| synchronized  | BLOCKED  | 几乎为零 | 通用场景           |
| ReentrantLock | WAITING  | 极低     | 需要高级功能       |
| 自旋锁        | RUNNABLE | 高       | 等待时间极短的场景 |

**最佳实践建议**：

- 优先使用synchronized或ReentrantLock
- 避免实现自己的自旋锁
- 锁等待时间较长时，标准的锁机制是最佳选择
- 使用JVM工具（jstack、jconsole）监控线程状态