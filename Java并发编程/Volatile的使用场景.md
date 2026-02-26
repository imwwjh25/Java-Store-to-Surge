`volatile` 是 Java 中用于保证**变量可见性**、**禁止指令重排序**的关键字（但不保证原子性），核心解决多线程下变量 “缓存不一致” 和 “指令重排导致的逻辑异常” 问题。以下是它的核心使用场景、原理及典型案例：

### 一、`volatile` 核心特性（先明确原理，再看使用）

1. **可见性**：一个线程修改了 `volatile` 变量，其他线程能立即看到最新值（禁止 CPU 缓存，直接读写主内存）；
2. **禁止指令重排序**：编译器 / CPU 不会对 `volatile` 变量相关的指令进行重排（通过内存屏障实现）；
3. **不保证原子性**：如 `i++` 这类复合操作（读 - 改 - 写），`volatile` 无法保证线程安全（需加锁 / 用原子类）。

### 二、`volatile` 典型使用场景

#### 场景 1：状态标记位（最常用）

用于多线程间的 “开关” 控制，如线程启停、任务中断、状态切换等，利用**可见性**保证状态变更能被所有线程感知。

**示例：线程启停控制**







```java
public class VolatileFlagDemo {
    // 状态标记位，用volatile保证可见性
    private volatile boolean isRunning = true;

    public void start() {
        new Thread(() -> {
            while (isRunning) { // 线程循环执行，依赖isRunning的最新值
                System.out.println("线程运行中...");
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }
            System.out.println("线程已停止");
        }).start();
    }

    public void stop() {
        isRunning = false; // 修改标记位，其他线程能立即看到
    }

    public static void main(String[] args) throws InterruptedException {
        VolatileFlagDemo demo = new VolatileFlagDemo();
        demo.start();
        Thread.sleep(500);
        demo.stop(); // 停止线程
    }
}
```

**为什么必须加 `volatile`？**如果不加，线程可能缓存 `isRunning` 的值（CPU 缓存），即使主线程修改为 `false`，子线程仍读取缓存的 `true`，导致线程无法停止。

#### 场景 2：单例模式（双重检查锁 DCL）

解决 DCL 单例中 “指令重排序” 导致的空指针问题，利用**禁止指令重排**特性保证对象初始化的完整性。

**反例（不加 volatile 有问题）**：








```java
// 错误的DCL单例（可能出现NPE）
public class Singleton {
    private static Singleton instance; // 未加volatile

    private Singleton() {}

    public static Singleton getInstance() {
        if (instance == null) { // 第一次检查
            synchronized (Singleton.class) {
                if (instance == null) { // 第二次检查
                    // new操作会被重排：1.分配内存 2.赋值引用 3.初始化对象
                    // 重排后可能先执行2（instance非null），但对象未初始化，其他线程获取到半初始化对象，调用方法抛NPE
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```

**正确写法（加 volatile）**：








```java
public class Singleton {
    // 加volatile，禁止new操作的指令重排
    private static volatile Singleton instance;

    private Singleton() {}

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton(); // 重排被禁止，保证对象完全初始化
                }
            }
        }
        return instance;
    }
}
```

#### 场景 3：保证变量的可见性（多线程读写共享变量）

当多个线程仅**读 / 写单个 volatile 变量**（无复合操作）时，用 `volatile` 替代锁，提升性能。

**示例：配置动态更新**









```java
public class ConfigManager {
    // 系统配置，支持动态更新，多线程读取需可见性
    private volatile String config = "default";

    // 更新配置（单线程写）
    public void updateConfig(String newConfig) {
        this.config = newConfig; // 写操作，volatile保证其他线程能立即看到
    }

    // 读取配置（多线程读）
    public String getConfig() {
        return this.config; // 读操作，直接获取最新值
    }
}
```

**适用条件**：仅单线程写、多线程读，且无复合操作（如 `config += "xxx"` 这类操作不行，需加锁）。

#### 场景 4：与 CAS 配合实现无锁并发（JUC 底层）

Java 并发包（`java.util.concurrent`）中大量使用 `volatile` + CAS 实现无锁并发，如 `AtomicInteger`、`ConcurrentHashMap` 等。

**示例：AtomicInteger 底层（简化版）**



```java
public class MyAtomicInteger {
    private volatile int value; // 用volatile保证可见性

    public int get() {
        return value;
    }

    // CAS操作（原子性由CPU指令保证）
    public boolean compareAndSet(int expect, int update) {
        // 底层调用Unsafe的CAS方法，保证读-改-写原子性
        return Unsafe.getUnsafe().compareAndSwapInt(this, valueOffset, expect, update);
    }

    public void increment() {
        int prev;
        do {
            prev = get(); // volatile保证读取最新值
        } while (!compareAndSet(prev, prev + 1)); // CAS自旋
    }
}
```

`volatile` 保证 `value` 的可见性，CAS 保证修改的原子性，两者结合实现高效的无锁并发。

#### 场景 5：解决长循环中的变量可见性

当线程执行长循环时，JVM 可能优化为 “栈内缓存” 变量，导致外部修改无法感知，`volatile` 可打破该优化。

**示例：长循环中的状态感知**











```java
public class LongLoopDemo {
    private volatile int count = 0; // 加volatile

    public void loop() {
        new Thread(() -> {
            while (count < 1000000) { // 长循环，依赖count的最新值
                // 无其他操作，JVM可能缓存count
            }
            System.out.println("循环结束");
        }).start();
    }

    public void add() {
        count = 1000000; // 修改count
    }
}
```

如果不加 `volatile`，JVM 可能将 `count` 缓存到线程栈中，即使主线程修改 `count`，循环线程也无法感知，导致无限循环。

### 三、`volatile` 使用注意事项（避坑）

1. **不保证原子性**：`volatile int i; i++` 不是线程安全的（需用 `AtomicInteger` 或 `synchronized`）；
2. **不能替代锁**：当存在多个线程同时写（如 `i++`、`config += "xxx"`），必须加锁，`volatile` 仅适用于 “单写多读” 场景；
3. **性能影响**：`volatile` 禁止 CPU 缓存，频繁读写会增加主内存交互，性能略低于普通变量（但远高于锁）；
4. **仅对变量有效**：`volatile` 修饰对象时，仅保证对象引用的可见性，对象内部字段的修改仍需额外处理（如内部字段加 `volatile` 或加锁）。

### 四、总结：`volatile` 适用场景速记

| 场景类型           | 核心作用              | 典型案例                         |
| ------------------ | --------------------- | -------------------------------- |
| 状态标记位         | 可见性                | 线程启停、任务中断               |
| DCL 单例           | 禁止指令重排序        | 单例对象初始化                   |
| 单写多读的共享变量 | 可见性                | 动态配置、状态变量               |
| JUC 无锁并发       | 可见性 + 配合 CAS     | AtomicInteger、ConcurrentHashMap |
| 长循环中的变量感知 | 打破 JVM 栈内缓存优化 | 长循环中的状态判断               |

简单来说：**只要需要多线程间感知变量的最新值，且无复合写操作，就可以用 volatile；有复合写操作，用锁或原子类；需要有序性，用 volatile 禁止重排**。