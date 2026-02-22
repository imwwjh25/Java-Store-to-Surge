### 一、ConcurrentHashMap 的散列流程

ConcurrentHashMap 类似，ConcurrentHashMap 的散列流程核心是通过两次哈希减少哈希冲突，具体步骤如下：

1. **计算初始哈希值**：对 key 的 `hashCode()` 进行一次哈希，得到 `h = key.hashCode()`。

2. **二次哈希（扰动函数）**：通过 `h ^ (h >>> 16)` 将高 16 位与低 16 位异或，目的是让哈希值的高位也参与后续的桶位计算，减少哈希冲突（尤其在数组长度较小时）。

3. **计算桶位**：用二次哈希后的结果与数组长度减一（`n - 1`）进行与运算（`&`），得到元素存放的桶索引 `i = (n - 1) & hash`。

   注：JDK 1.8 中 ConcurrentHashMap 与 HashMap 的散列逻辑完全一致，目的是保持哈希计算的高效性。

### 二、ConcurrentHashMap 如何实现线程安全？

ConcurrentHashMap 在 JDK 1.7 和 1.8 中实现线程安全的方式不同，核心演进如下：

#### 1. JDK 1.7：分段锁（Segment）

- 底层结构：由 `Segment` 数组组成，每个 `Segment` 本质是一个可独立加锁的小 HashMap（包含 `HashEntry` 数组）。
- 线程安全：对数据的操作（如 put、get）会先定位到对应的 `Segment`，再对该 `Segment` 加锁（`ReentrantLock`），不同 `Segment` 可并行操作，实现 “分段锁” 隔离，减少锁竞争。

#### 2. JDK 1.8：CAS + synchronized + volatile

- 底层结构：摒弃 `Segment`，直接使用数组 + 链表 / 红黑树（与 HashMap 结构一致）。
- 线程安全机制：
    - **volatile**：数组 `table` 和节点的 `val`、`next` 字段用 `volatile` 修饰，保证内存可见性（一个线程修改后，其他线程能立即看到）。
    - **synchronized**：对链表头节点或红黑树的根节点加锁，粒度比 Segment 更细（仅锁定单个桶的首节点），减少锁冲突。
    - **CAS**：用于无锁化操作（如初始化数组、扩容检查、链表头节点插入等），避免加锁开销。

### 三、CAS 是什么？怎么实现的？

#### 1. CAS 含义

CAS（Compare And Swap，比较并交换）是一种乐观锁机制，用于无锁化的并发控制。它通过硬件指令保证原子性，核心逻辑是：**判断内存中的值是否等于预期值，若相等则更新为新值，否则不操作**。

#### 2. CAS 操作的三个参数

- `V`：内存地址（存放实际值的位置）。

- `A`：预期值（认为当前内存中的值应该是 A）。

- `B`：新值（若 V 的值等于 A，则将 V 更新为 B）。

  执行结果：若成功（V == A），返回 true；若失败（V != A），返回 false，通常需配合循环重试（自旋）。

#### 3. CAS 的实现

Java 中通过 `sun.misc.Unsafe` 类调用 native 方法，底层依赖 CPU 指令（如 x86 的 `cmpxchg` 指令）实现原子性。例如：







```java
// Unsafe 中的 CAS 方法（简化）
public final native boolean compareAndSwapInt(Object o, long offset, int expected, int x);
```

- `o`：目标对象。
- `offset`：字段在对象内存中的偏移量（通过 `objectFieldOffset` 获取）。
- `expected`：预期值。
- `x`：新值。

### 四、Unsafe 类怎么实现的？

`Unsafe` 是 Java 提供的一个底层工具类，允许直接操作内存、线程、锁等底层资源，其实现依赖 JVM 和 native 方法：

1. **获取实例**：`Unsafe` 构造函数私有，通过 `getUnsafe()` 方法获取，但仅允许启动类加载器（Bootstrap ClassLoader）加载的类使用，用户类需通过反射获取。

2. 核心功能 ：

    - 内存操作：直接分配 / 释放内存（`allocateMemory`、`freeMemory`）。
    - 字段操作：通过内存偏移量读写字段（`getInt`、`putInt`），绕过访问权限检查。
    - CAS 操作：提供 `compareAndSwapXxx` 系列方法，封装 CPU 原子指令。
    - 线程操作：`park`/`unpark` 实现线程阻塞 / 唤醒（LockSupport 的底层）。

3. **安全性**：`Unsafe` 直接操作底层资源，若使用不当会导致内存泄漏、JVM 崩溃等问题，因此不建议开发者直接使用。

### 五、ConcurrentHashMap 何时用到 CAS？

JDK 1.8 中，CAS 主要用于无锁化的原子操作，避免加锁开销，典型场景包括：

1. **初始化数组（table）**：当多个线程同时检测到 `table` 未初始化时，通过 CAS 竞争设置 `sizeCtl`（控制标志），只有一个线程能成功初始化。
2. **扩容检查**：扩容时通过 CAS 更新 `sizeCtl` 为负数（标记扩容状态），防止其他线程重复扩容。
3. **链表头节点插入**：当桶为空时，通过 `casTabAt` 原子性插入第一个节点（避免加锁）。
4. **计数更新**：通过 `CounterCell`（分段计数器）的 CAS 操作更新元素总数（`size()` 方法），减少并发冲突。

### 六、并发插入：两个线程同时插入元素的处理

假设两个线程同时向同一个桶插入元素，处理流程如下：

1. 若桶为空：线程 A 先通过 CAS 插入头节点成功，线程 B 发现头节点已存在（CAS 失败），则进入同步逻辑。

2. 若桶非空（链表或红黑树）：

    - 线程 A 先获取桶的头节点锁（synchronized 修饰头节点），开始插入操作。
    - 线程 B 尝试获取同一头节点锁时会被阻塞，直到线程 A 释放锁后，线程 B 才能进入并处理（可能需要判断是否已存在相同 key，或插入到链表 / 红黑树中）。

   核心：通过 synchronized 锁定桶的头节点，保证同一时刻只有一个线程操作该桶，结合 volatile 可见性，确保插入结果被其他线程正确感知。

### 七、初始化冲突：两个线程同时检测到需要初始化的处理

`table` 初始化由 `initTable()` 方法实现，通过 `sizeCtl` 控制并发（`sizeCtl` 为负数表示正在初始化或扩容，正数表示预期容量）：

1. 线程 A 检测到 `table == null` 且 `sizeCtl > 0`，尝试通过 CAS 将 `sizeCtl` 设为 `-1`（标记 “正在初始化”），成功后开始初始化 `table`。

2. 线程 B 同时检测到 `table == null`，但 CAS 尝试修改 `sizeCtl` 时发现已为 `-1`（说明有线程正在初始化），则通过 `Thread.yield()` 让出 CPU 时间片，循环等待。

3. 线程 A 初始化完成后，将 `sizeCtl` 设为 `n - (n >>> 2)`（扩容阈值，即容量的 0.75 倍），退出初始化。

4. 线程 B 循环中检测到 `table` 已初始化，直接退出等待，进入后续操作。

   核心：通过 CAS 竞争 `sizeCtl` 标志，保证只有一个线程执行初始化，其他线程自旋等待，避免重复初始化。
