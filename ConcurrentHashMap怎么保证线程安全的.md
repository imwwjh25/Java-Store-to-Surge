## 一、ConcurrentHashMap 怎么保证线程安全？（JDK 8+ 核心机制）



JDK 8 彻底抛弃了 JDK 7 的「Segment 分段锁」，改为 **“数组 + 链表 / 红黑树”** 的数据结构（与 HashMap 一致），通过「乐观锁（无锁）+ 悲观锁（synchronized）+ CAS 原子操作 + volatile 可见性」四重机制保障线程安全，核心设计是 **“读操作无锁，写操作轻量锁”**。

### 1. 核心数据结构与 volatile 保障



java

运行

```
// 哈希表数组（volatile 修饰，保障数组引用可见性）
transient volatile Node<K,V>[] table;
// 扩容时的临时数组（volatile 修饰，避免扩容过程中数组引用错乱）
private transient volatile Node<K,V>[] nextTable;
// 基础节点（val 和 next 用 volatile 修饰，保障读写可见性）
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    volatile V val;
    volatile Node<K,V> next;
    // ... 构造器、getter 等
}
```



- volatile 关键作用

  ：

  1. `table` 和 `nextTable` 用 volatile 修饰，确保线程对数组扩容、初始化的感知（避免指令重排导致的 “数组引用不可见”）；
  2. `Node` 的 `val` 和 `next` 用 volatile 修饰，确保一个线程修改节点值 / 链表后，其他线程能立即看到最新结果（避免脏读）。

### 2. 读操作：无锁设计（依赖 volatile + 原子引用）



CHM 的读操作（`get`/`containsKey`）全程无锁，直接读取数组和节点，依赖 volatile 的可见性和 Node 的不可变性（key 和 hash 是 final）：

java

运行

```
public V get(Object key) {
    Node<K,V>[] tab; Node<K,V> e, p; int n, eh; K ek;
    int h = spread(key.hashCode()); // 哈希值计算（扰动函数，减少碰撞）
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (e = tabAt(tab, (n - 1) & h)) != null) { // 原子获取数组元素（tabAt 是 Unsafe 操作）
        if ((eh = e.hash) == h) {
            if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                return e.val; // 命中头节点，直接返回
        }
        // 红黑树节点或迁移节点（FORWARDING_NODE），特殊处理
        else if (eh < 0)
            return (p = e.find(h, key)) != null ? p.val : null;
        // 链表节点，遍历查找
        while ((e = e.next) != null) {
            if (e.hash == h &&
                ((ek = e.key) == key || (ek != null && key.equals(ek))))
                return e.val;
        }
    }
    return null;
}
```



- 关键细节

  ：

  - `tabAt(tab, index)` 是 Unsafe 的 `getObjectVolatile` 操作，原子性读取数组指定位置的 Node，避免读取过程中数组元素被修改（如扩容时的节点迁移）；
  - 读操作无需加锁，因为所有写操作都会通过 volatile 或 CAS 保证修改的可见性，且 Node 的 key/hash 不可变，链表 / 红黑树的结构修改（如插入 / 删除）不会影响读操作的正确性（最多读到旧值，但符合 “最终一致性”）。

### 3. 写操作：synchronized + CAS 组合锁（轻量级悲观锁）



写操作（`put`/`remove`/`replace`）针对「数组槽位（桶）」加锁，而非全局锁，锁粒度是 “桶级”，支持多线程同时操作不同桶，并发效率远高于 JDK 7 的分段锁：

#### （1）put 操作核心流程（源码简化）



java

运行

```
public V put(K key, V value) {
    return putVal(key, value, false);
}

final V putVal(K key, V value, boolean onlyIfAbsent) {
    if (key == null || value == null) throw new NullPointerException();
    int hash = spread(key.hashCode());
    int binCount = 0;
    for (Node<K,V>[] tab = table;;) { // 自旋重试（处理并发冲突）
        Node<K,V> f; int n, i, fh;
        // 1. 数组未初始化，CAS 初始化（无锁）
        if (tab == null || (n = tab.length) == 0)
            tab = initTable();
        // 2. 目标桶为空，CAS 插入头节点（无锁）
        else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
            if (casTabAt(tab, i, null, new Node<K,V>(hash, key, value, null)))
                break; // CAS 成功，插入完成
        }
        // 3. 目标桶是迁移节点（FORWARDING_NODE），说明正在扩容，当前线程协助扩容
        else if ((fh = f.hash) == MOVED)
            tab = helpTransfer(tab, f);
        // 4. 目标桶有节点，synchronized 加锁（桶级锁）
        else {
            V oldVal = null;
            synchronized (f) { // 对桶的头节点加锁，其他线程无法操作该桶
                if (tabAt(tab, i) == f) { // 二次校验：确保头节点未被修改（避免并发问题）
                    if (fh >= 0) { // 链表节点
                        binCount = 1;
                        for (Node<K,V> e = f;; ++binCount) {
                            K ek;
                            // key 已存在，更新值
                            if (e.hash == hash &&
                                ((ek = e.key) == key || (ek != null && key.equals(ek)))) {
                                oldVal = e.val;
                                if (!onlyIfAbsent)
                                    e.val = value; // volatile 写，保障可见性
                                break;
                            }
                            // key 不存在，插入链表尾部
                            Node<K,V> pred = e;
                            if ((e = e.next) == null) {
                                pred.next = new Node<K,V>(hash, key, value, null);
                                break;
                            }
                        }
                    }
                    else if (f instanceof TreeBin) { // 红黑树节点
                        Node<K,V> p;
                        binCount = 2;
                        if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key, value)) != null) {
                            oldVal = p.val;
                            if (!onlyIfAbsent)
                                p.val = value;
                        }
                    }
                }
            }
            // 5. 链表长度超过阈值（8），转为红黑树
            if (binCount != 0) {
                if (binCount >= TREEIFY_THRESHOLD)
                    treeifyBin(tab, i);
                if (oldVal != null)
                    return oldVal;
                break;
            }
        }
    }
    // 6. 统计元素个数（CAS 操作），并判断是否需要扩容
    addCount(1L, binCount);
    return null;
}
```



#### （2）写操作的线程安全保障：



- **桶空时**：用 `casTabAt`（Unsafe 的 `compareAndSwapObject`）原子插入头节点，无锁操作，高效；
- **桶非空时**：对桶的头节点 `f` 加 `synchronized` 锁，确保同一时间只有一个线程操作该桶（链表 / 红黑树的插入、删除、更新）；
- **扩容时**：遇到 `MOVED` 标记的迁移节点，当前线程会调用 `helpTransfer` 协助扩容，避免扩容线程成为瓶颈；
- **计数时**：`addCount` 用 CAS 原子更新元素个数（`baseCount` 或 `CounterCell`），避免并发计数错误。

### 4. 其他线程安全保障细节



- **红黑树操作安全**：`TreeBin` 类内部用 `lockState` 变量（CAS 控制）保障红黑树的旋转、插入、删除操作线程安全；
- **节点不可变性**：`Node` 的 `key` 和 `hash` 是 final，避免修改 key 导致的哈希错乱；
- **禁止 null 键值**：CHM 不允许 key 或 value 为 null，避免 `get` 返回 null 时无法区分 “键不存在” 和 “值为 null” 的问题（线程安全场景下的歧义）。

## 二、已经用了 synchronized，为什么还要用 CAS？



JDK 8 中 CHM 同时使用 `synchronized` 和 CAS，核心是 **“各司其职、优势互补”**——synchronized 处理 “重量级冲突”，CAS 处理 “轻量级无冲突”，最终实现 “高并发下的高效性 + 安全性”，具体原因如下：

### 1. 两者的核心定位不同（互补性）



| 特性     | synchronized（JDK 8 优化后）                                 | CAS（Compare-And-Swap）                                      |
| -------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 核心作用 | 处理 “桶非空” 的冲突场景（链表 / 红黑树操作），保证操作原子性 | 处理 “无冲突” 场景（数组初始化、桶空插入、计数更新），无锁高效 |
| 性能特点 | 偏向锁→轻量级锁→重量级锁的自适应升级，低冲突时开销小         | 无锁操作，无需上下文切换，冲突时自旋重试，开销极低           |
| 适用场景 | 操作复杂（如链表遍历插入、红黑树修改），需要排他性           | 操作简单（如原子赋值、计数），冲突概率低                     |

### 2. 具体场景：CAS 解决 synchronized 无法覆盖的问题



#### （1）数组初始化（`initTable`）：CAS 避免并发初始化



java

运行

```
private final Node<K,V>[] initTable() {
    Node<K,V>[] tab; int sc;
    while ((tab = table) == null || tab.length == 0) {
        // sc < 0 表示已有线程在初始化，当前线程让出 CPU
        if ((sc = sizeCtl) < 0)
            Thread.yield();
        // CAS 将 sizeCtl 设为 -1（标记为初始化中），成功则执行初始化
        else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
            try {
                if ((tab = table) == null || tab.length == 0) {
                    int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                    @SuppressWarnings("unchecked")
                    Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                    table = tab = nt;
                    sc = n - (n >>> 2); // 计算扩容阈值（0.75 * n）
                }
            } finally {
                sizeCtl = sc; // 初始化完成，恢复 sizeCtl 为扩容阈值
            }
            break;
        }
    }
    return tab;
}
```



- 若用 `synchronized` 初始化，需加全局锁，开销大；
- 用 CAS 原子修改 `sizeCtl` 为 -1，标记 “初始化中”，其他线程看到 `sc < 0` 会 yield，避免并发初始化冲突，无锁且高效。

#### （2）桶空时插入节点：CAS 避免无意义加锁



当目标桶为空时，插入头节点的操作是 “单一赋值”，无复杂逻辑，此时用 CAS 原子操作：

- 若用 `synchronized`，需先获取桶的锁（即使桶空），上下文切换开销远大于 CAS；
- CAS 直接原子插入，冲突时自旋重试，冲突概率低（桶空时无竞争），性能更优。

#### （3）元素计数（`addCount`）：CAS 实现高并发计数



CHM 的元素个数统计用 `baseCount` + `CounterCell` 数组：

java

运行

```
private final void addCount(long x, int check) {
    CounterCell[] as; long b, s;
    // 1. CAS 更新 baseCount，成功则直接返回
    if ((as = counterCells) != null ||
        !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
        CounterCell a; long v; int m;
        boolean uncontended = true;
        // 2. baseCount 更新失败（冲突），使用 CounterCell 数组分散冲突
        if (as == null || (m = as.length - 1) < 0 ||
            (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
            !(uncontended = U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
            fullAddCount(x, uncontended); // 进一步处理冲突（扩容 CounterCell 等）
        }
        if (check <= 1)
            return;
        s = sumCount(); // 统计总个数（baseCount + 所有 CounterCell 的值）
    }
    // 3. 检查是否需要扩容
    if (check >= 0) {
        Node<K,V>[] tab, nt; int n, sc;
        while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
               (n = tab.length) < MAXIMUM_CAPACITY) {
            int rs = resizeStamp(n);
            if (sc < 0) {
                if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                    sc == rs + MAX_RESIZERS || (nt = nextTable) == null)
                    break;
                // CAS 增加扩容线程数（sc += 1）
                if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                    transfer(tab, nt);
            }
            // CAS 将 sizeCtl 设为 -rs -1（标记为扩容中）
            else if (U.compareAndSwapInt(this, SIZECTL, sc, (rs << RESIZE_STAMP_SHIFT) | 1))
                transfer(tab, null);
            s = sumCount();
        }
    }
}
```



- 若用 `synchronized` 计数，高并发下所有线程会阻塞在锁上，计数成为瓶颈；
- CAS 实现 “无锁计数”，冲突时用 `CounterCell` 分散压力，支持高并发计数。

#### （4）扩容时协助迁移：CAS 标记迁移进度



扩容过程中，线程通过 CAS 标记已迁移的桶，避免重复迁移（后续扩容部分详细说明）。

### 3. 核心结论：CAS 是 synchronized 的 “补充”，而非 “替代”



- `synchronized` 适合 “复杂操作 + 高冲突” 场景（如链表 / 红黑树修改），提供排他性保证；
- CAS 适合 “简单操作 + 低冲突” 场景（如初始化、插入、计数），提供无锁高效保证；
- 两者结合，既解决了 synchronized 高并发下的性能问题，又解决了 CAS 无法处理复杂操作的问题，实现 “安全 + 高效” 的平衡。

## 三、扩容是怎么保证线程安全的？（源码级保障机制）



CHM 的扩容（`transfer` 方法）是 **“多线程协助扩容”** 机制，核心目标是 “线程安全 + 高效迁移”，通过「扩容标记、节点迁移、并发控制、迁移状态标记」四大步骤保障安全，以下是源码细节：

### 1. 扩容触发条件



- 元素个数 `sumCount()` >= 扩容阈值（`sizeCtl`，默认是数组长度的 0.75 倍）；
- 链表长度超过阈值（8），且数组长度 < 64 时，触发扩容（而非直接转红黑树）。

### 2. 扩容核心流程与线程安全保障



#### （1）步骤 1：标记扩容状态（CAS 修改 sizeCtl）



扩容前，线程通过 CAS 将 `sizeCtl` 从 “扩容阈值” 改为 “扩容标记”：

- `sizeCtl` 的结构（高 16 位是扩容戳 `resizeStamp`，低 16 位是扩容线程数）；
- 初始扩容时，CAS 将 `sizeCtl` 设为 `(rs << RESIZE_STAMP_SHIFT) | 1`（`rs` 是扩容戳，低 16 位为 1 表示 1 个扩容线程）；
- 其他线程协助扩容时，CAS 将 `sizeCtl` 的低 16 位加 1（`sc += 1`），标记扩容线程数增加。

#### （2）步骤 2：初始化扩容数组（nextTable）



java

运行

```
private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
    int n = tab.length, stride;
    // 计算每个线程的迁移步长（根据 CPU 核心数，避免线程迁移重叠）
    if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
        stride = MIN_TRANSFER_STRIDE; // 最小步长 16
    // 初始化 nextTable（扩容后的数组，长度为 2n）
    if (nextTab == null) {
        try {
            @SuppressWarnings("unchecked")
            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
            nextTab = nt;
        } catch (Throwable ex) { // 内存不足，扩容失败
            sizeCtl = Integer.MAX_VALUE;
            return;
        }
        nextTable = nextTab;
        transferIndex = n; // 迁移索引（从数组尾部开始迁移）
    }
    int nextn = nextTab.length;
    // 迁移节点（FORWARDING_NODE）：标记桶已迁移完成，hash 为 MOVED（-1）
    ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);
    boolean advance = true;
    boolean finishing = false; // 是否迁移完成
    for (int i = 0, bound = 0;;) {
        Node<K,V> f; int fh;
        // 步骤 3：并发分配迁移桶（CAS 控制 transferIndex）
        while (advance) {
            int nextIndex, nextBound;
            if (--i >= bound || finishing)
                advance = false;
            // CAS 获取迁移区间（从 transferIndex 减去 stride，分配给当前线程）
            else if ((nextIndex = transferIndex) <= 0) {
                i = -1;
                advance = false;
            }
            else if (U.compareAndSwapInt(this, TRANSFERINDEX, nextIndex,
                                         nextBound = (nextIndex > stride ? nextIndex - stride : 0))) {
                bound = nextBound;
                i = nextIndex - 1;
                advance = false;
            }
        }
        if (i < 0 || i >= n || i + n >= nextn) {
            int sc;
            // 步骤 6：迁移完成，更新 table 和 sizeCtl
            if (finishing) {
                nextTable = null;
                table = nextTab;
                sizeCtl = (nextn >>> 1) + (nextn >>> 2); // 新的扩容阈值（0.75 * nextn）
                return;
            }
            // CAS 减少扩容线程数（sc -= 1）
            if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
                if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)
                    return;
                finishing = advance = true;
                i = n; // 重新遍历，检查是否有遗漏的桶
            }
        }
        // 步骤 4：迁移当前桶的节点
        else if ((f = tabAt(tab, i)) == null)
            // 桶为空，CAS 标记为迁移完成（放入 fwd 节点）
            advance = casTabAt(tab, i, null, fwd);
        else if ((fh = f.hash) == MOVED)
            // 桶已迁移完成，跳过
            advance = true;
        else {
            // 对当前桶加锁，避免迁移过程中被修改
            synchronized (f) {
                if (tabAt(tab, i) == f) {
                    Node<K,V> ln, hn;
                    if (fh >= 0) { // 链表节点
                        // 拆分链表为两个部分（根据新的哈希值，放入 nextTable 的两个桶）
                        int runBit = fh & n;
                        Node<K,V> lastRun = f;
                        for (Node<K,V> p = f.next; p != null; p = p.next) {
                            int b = p.hash & n;
                            if (b != runBit) {
                                runBit = b;
                                lastRun = p;
                            }
                        }
                        if (runBit == 0) {
                            ln = lastRun;
                            hn = null;
                        } else {
                            hn = lastRun;
                            ln = null;
                        }
                        // 遍历链表，拆分到 ln（低半区）和 hn（高半区）
                        for (Node<K,V> p = f; p != lastRun; p = p.next) {
                            int b = p.hash & n;
                            Node<K,V> newNode = new Node<K,V>(p.hash, p.key, p.val, null);
                            if (b == 0)
                                ln = new Node<K,V>(p.hash, p.key, p.val, ln);
                            else
                                hn = new Node<K,V>(p.hash, p.key, p.val, hn);
                        }
                        // CAS 插入低半区链表到 nextTable
                        setTabAt(nextTab, i, ln);
                        // CAS 插入高半区链表到 nextTable（i + n 位置）
                        setTabAt(nextTab, i + n, hn);
                        // CAS 标记当前桶已迁移完成（放入 fwd 节点）
                        setTabAt(tab, i, fwd);
                        advance = true;
                    }
                    else if (f instanceof TreeBin) { // 红黑树节点
                        // 拆分红黑树为两个链表，再转为红黑树
                        TreeBin<K,V> t = (TreeBin<K,V>)f;
                        TreeNode<K,V> lc = null, hc = null;
                        for (Node<K,V> e = t.first; e != null; e = e.next) {
                            int b = e.hash & n;
                            TreeNode<K,V> p = new TreeNode<K,V>(e.hash, e.key, e.val, null, null);
                            if (b == 0) {
                                p.prev = lc;
                                lc = p;
                            } else {
                                p.prev = hc;
                                hc = p;
                            }
                        }
                        // 转为红黑树或链表
                        ln = (lc != null) ? new TreeBin<K,V>(lc) : null;
                        hn = (hc != null) ? new TreeBin<K,V>(hc) : null;
                        setTabAt(nextTab, i, ln);
                        setTabAt(nextTab, i + n, hn);
                        setTabAt(tab, i, fwd);
                        advance = true;
                    }
                }
            }
        }
    }
}
```



#### （3）扩容的线程安全保障细节：



1. **扩容状态标记**：通过 `sizeCtl` 的高 16 位扩容戳和低 16 位线程数，标记扩容中状态，避免重复扩容；
2. **迁移区间分配**：线程通过 CAS 修改 `transferIndex`，原子分配迁移区间（从尾部开始，按步长分配），避免线程迁移重叠；
3. **桶级锁保护**：迁移每个桶时，对桶的头节点加 `synchronized` 锁，确保迁移过程中桶的节点不被修改（如插入、删除）；
4. **迁移状态标记**：迁移完成的桶，通过 CAS 放入 `ForwardingNode`（hash = MOVED），标记 “已迁移”，其他线程看到该节点会跳过或协助扩容；
5. **读操作兼容**：扩容过程中，读操作遇到 `ForwardingNode` 会自动跳转到 `nextTable` 读取数据，不影响读操作的正确性；
6. **写操作兼容**：写操作遇到 `ForwardingNode` 会调用 `helpTransfer` 协助扩容，待扩容完成后再执行写操作；
7. **扩容线程数控制**：通过 `sizeCtl` 的低 16 位限制最大扩容线程数（`MAX_RESIZERS = 65535`，但实际受 CPU 核心数限制，避免过多线程导致上下文切换）。

## 四、扩容时会有几个线程在处理？（源码级限制逻辑）



CHM 扩容时的线程数是 **“动态可变的，支持多线程协助，但有上限”**，核心由「主动扩容线程 + 被动协助线程」组成，具体规则如下：

### 1. 线程参与扩容的两种方式



- **主动扩容线程**：第一个触发扩容条件的线程（调用 `transfer` 方法）；
- **被动协助线程**：其他执行写操作（`put`/`remove` 等）的线程，遇到 `ForwardingNode` 时，调用 `helpTransfer` 协助扩容。

### 2. 扩容线程数的限制规则（源码依据）



#### （1）最大扩容线程数上限：`MAX_RESIZERS = 65535`



源码中 `sizeCtl` 的低 16 位用于存储扩容线程数，因此理论最大扩容线程数是 `2^16 - 1 = 65535`：

java

运行

```
private static final int MAX_RESIZERS = (1 << RESIZE_STAMP_SHIFT) - 1; // RESIZE_STAMP_SHIFT = 16
```



但实际中不会达到这个上限，因为受 CPU 核心数和迁移步长的限制。

#### （2）实际扩容线程数：受 CPU 核心数和迁移步长控制



`transfer` 方法中，每个线程的迁移步长 `stride` 计算逻辑：

java

运行

```
stride = (NCPU > 1) ? (n >>> 3) / NCPU : n;
if (stride < MIN_TRANSFER_STRIDE)
    stride = MIN_TRANSFER_STRIDE; // MIN_TRANSFER_STRIDE = 16
```



- `NCPU` 是 CPU 核心数（如 8 核 CPU）；
- 步长 `stride` 表示每个线程负责迁移的桶数（默认最小 16 个桶）；
- 例如：数组长度 `n = 1024`，8 核 CPU 时，`stride = (1024 / 8) / 8 = 16`，总迁移线程数 = 1024 / 16 = 64 个；
- 若数组长度较小（如 `n = 64`），`stride = 16`，总迁移线程数 = 64 / 16 = 4 个。

#### （3）核心结论：扩容线程数 = min (实际需要的线程数，MAX_RESIZERS, CPU 核心数 * 某个倍数)



- 实际需要的线程数 = 数组长度 / 迁移步长（`stride`）；
- 上限是 `MAX_RESIZERS = 65535`（理论值），但实际中受 CPU 核心数限制（避免过多线程导致上下文切换）；
- 正常情况下，扩容线程数不会超过 CPU 核心数的 2~4 倍（如 8 核 CPU 最多 32 个线程），除非数组长度极大且并发极高。

### 3. 扩容线程的退出机制



- 每个线程完成自己分配的迁移区间后，通过 CAS 将 `sizeCtl` 的低 16 位减 1（`sc -= 1`）；
- 当最后一个扩容线程完成迁移后，`sizeCtl` 的低 16 位变为 0，触发 `finishing = true`，更新 `table = nextTab`，扩容完成；
- 若线程在扩容过程中被中断或异常，`sizeCtl` 的线程数会通过 CAS 自动调整，不影响扩容整体进度。

## 五、核心总结（全维度梳理）



1. **线程安全核心**：JDK 8+ 用「volatile 可见性 + 桶级 synchronized 锁 + CAS 原子操作 + 无锁读」，替代 JDK 7 的分段锁，实现 “高并发 + 低开销”；
2. **synchronized 与 CAS 搭配原因**：synchronized 处理复杂冲突场景，CAS 处理简单无冲突场景，互补提升性能；
3. **扩容线程安全**：通过 “扩容标记、区间分配、桶级锁、迁移节点标记” 四大机制，保障多线程协助扩容的安全性，同时兼容读写操作；
4. **扩容线程数**：动态可变（主动 + 被动协助），理论上限 65535，实际受 CPU 核心数和迁移步长限制，避免过多线程开销。

CHM 的设计本质是「权衡安全性和性能」，通过精细化的锁粒度、无锁设计、多线程协作，成为 Java 并发场景下的首选容器。
