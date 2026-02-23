在 Java 中保证线程 A、B 顺序执行（A 先执行，B 后执行），可以通过多种线程同步机制实现。以下是常见的几种方法及示例：

### 1. 使用 `join()` 方法（最简单直接）

`Thread.join()` 的作用是：让当前线程等待调用 `join()` 的线程执行完毕后，再继续执行。若在线程 B 中调用 `A.join()`，则 B 会等待 A 执行完成后再继续运行，从而保证顺序。



```java
public class ThreadOrderWithJoin {
    public static void main(String[] args) {
        // 线程A：先执行的任务
        Thread threadA = new Thread(() -> {
            System.out.println("线程A执行任务");
        }, "线程A");

        // 线程B：后执行的任务，需等待A完成
        Thread threadB = new Thread(() -> {
            try {
                // 等待线程A执行完毕
                threadA.join(); 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("线程B执行任务");
        }, "线程B");

        // 启动线程（A先启动，B后启动，确保A先运行）
        threadA.start();
        threadB.start();
    }
}
```

**原理**：`threadA.join()` 会让线程 B 进入阻塞状态，直到 A 执行完（`run()` 方法结束），B 才会从阻塞中唤醒并继续执行。

### 2. 等待 / 通知机制（`wait()` + `notify()`）

通过共享对象的锁机制，让线程 B 先等待，线程 A 执行完后通知 B 继续执行。






```java
public class ThreadOrderWithWaitNotify {
    public static void main(String[] args) {
        // 共享锁对象
        Object lock = new Object();

        // 线程B：先获取锁并等待
        Thread threadB = new Thread(() -> {
            synchronized (lock) {
                try {
                    System.out.println("线程B等待A执行...");
                    lock.wait(); // 释放锁并进入等待状态
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("线程B执行任务");
            }
        }, "线程B");

        // 线程A：执行完后通知B
        Thread threadA = new Thread(() -> {
            synchronized (lock) {
                System.out.println("线程A执行任务");
                lock.notify(); // 唤醒等待的线程B
            }
        }, "线程A");

        // 先启动B（确保B先获取锁并等待，避免A先通知导致B永远等待）
        threadB.start();
        // 短暂休眠，确保B已进入等待状态（实际开发可优化）
        try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
        threadA.start();
    }
}
```

**原理**：

- 线程 B 先获取锁并调用 `wait()`，释放锁并进入等待队列。
- 线程 A 获取锁后执行任务，完成后调用 `notify()` 唤醒 B，B 重新获取锁并继续执行。

### 3. 使用 `CountDownLatch`（灵活的计数器）

`CountDownLatch` 是一个计数器，初始化时指定计数次数，线程调用 `await()` 会等待计数归零，其他线程调用 `countDown()` 减少计数。此处用计数 1 控制：B 等待计数归零，A 执行完后将计数减为 0。



```java
import java.util.concurrent.CountDownLatch;

public class ThreadOrderWithCountDownLatch {
    public static void main(String[] args) {
        // 初始化计数器为1
        CountDownLatch latch = new CountDownLatch(1);

        // 线程B：等待计数器归零
        Thread threadB = new Thread(() -> {
            try {
                System.out.println("线程B等待A执行...");
                latch.await(); // 等待计数变为0
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("线程B执行任务");
        }, "线程B");

        // 线程A：执行完后将计数减为0
        Thread threadA = new Thread(() -> {
            System.out.println("线程A执行任务");
            latch.countDown(); // 计数-1（变为0）
        }, "线程A");

        // 先启动B确保其进入等待
        threadB.start();
        try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
        threadA.start();
    }
}
```

**原理**：`latch.await()` 让 B 阻塞，A 执行完后调用 `latch.countDown()` 将计数从 1 变为 0，B 被唤醒并继续执行。

### 4. 单线程线程池（`SingleThreadExecutor`）

单线程线程池（`Executors.newSingleThreadExecutor()`）会按任务提交顺序**串行执行**，因此先提交 A、再提交 B 即可保证顺序。









```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadOrderWithExecutor {
    public static void main(String[] args) {
        // 创建单线程线程池（任务按提交顺序执行）
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // 先提交线程A的任务
        executor.submit(() -> System.out.println("线程A执行任务"));
        // 再提交线程B的任务（会在A完成后执行）
        executor.submit(() -> System.out.println("线程B执行任务"));

        // 关闭线程池
        executor.shutdown();
    }
}
```

**原理**：单线程池内部只有一个工作线程，任务队列按提交顺序排列，前一个任务执行完才会执行下一个。

### 总结

- 简单场景优先用 `join()`，代码简洁。
- 复杂场景（如多线程依赖）可用 `CountDownLatch` 或 `wait/notify`。
- 任务提交场景可用单线程池，无需手动控制线程同步。

注意：所有方法需确保 “等待方” 先进入等待状态，避免 “通知” 在 “等待” 前发生（导致等待方永远阻塞）。