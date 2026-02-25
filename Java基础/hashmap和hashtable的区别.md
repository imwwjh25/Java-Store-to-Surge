# HashMap 与 Hashtable 的核心区别（Java 集合框架）

HashMap 和 Hashtable 都是 Java 中实现 `Map` 接口的哈希表，用于存储键值对（Key-Value），核心基于「数组 + 链表 / 红黑树」的哈希结构，但二者在 **线程安全、空值支持、性能、底层实现** 等方面差异显著，Hashtable 是 JDK 1.0 遗留类，目前已基本被 HashMap 替代（或用 `ConcurrentHashMap` 替代线程安全场景）。

## 一、核心区别对比表

| 对比维度           | HashMap（JDK 1.2 引入）                                      | Hashtable（JDK 1.0 引入）                                    |
| ------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| **线程安全**       | 非线程安全（未加锁），多线程并发修改可能导致数据错乱（如死循环） | 线程安全（方法加 `synchronized` 锁），多线程环境下安全但性能低 |
| **空值支持**       | 允许 1 个 `null` Key，多个 `null` Value                      | 不允许 `null` Key 和 `null` Value（否则抛 `NullPointerException`） |
| **底层实现**       | JDK 8 后：数组 + 链表（哈希冲突时）+ 红黑树（链表长度＞8 时） | 数组 + 链表（无红黑树优化，哈希冲突时链表一直扩展）          |
| **初始容量与扩容** | 默认初始容量 16，扩容因子 0.75，扩容为原容量的 2 倍（`newCap = oldCap << 1`） | 默认初始容量 11，扩容因子 0.75，扩容为原容量的 2 倍 + 1（`newCap = oldCap * 2 + 1`） |
| **哈希函数**       | 扰动函数更复杂（`hash(key) ^ (hash(key) >>> 16)`），哈希分布更均匀 | 直接使用 `key.hashCode()`，哈希冲突概率更高                  |
| **遍历方式**       | 支持迭代器（`Iterator`），且是快速失败（fail-fast）迭代器    | 支持迭代器（`Iterator`）和枚举（`Enumeration`），迭代器是快速失败，枚举不是 |
| **继承关系**       | 继承 `AbstractMap` 类，实现 `Map` 接口                       | 继承 `Dictionary` 类（已过时），实现 `Map` 接口              |
| **性能**           | 无锁，查询 / 插入 / 删除效率高（JDK 8 红黑树优化后）         | 方法加锁（`synchronized`），并发场景下性能低（锁粒度大，整机锁） |
| **适用场景**       | 单线程环境、高并发场景（配合 `ConcurrentHashMap`）           | 遗留系统维护、需线程安全但性能要求低的场景（不推荐新代码使用） |

## 二、关键区别详解

### 1. 线程安全：非线程安全 vs 线程安全（锁粒度不同）

这是二者最核心的区别：

- **Hashtable 线程安全**：所有公开方法（如 `put()`、`get()`、`remove()`）都加了 `synchronized` 关键字，本质是「整机锁」—— 同一时刻只有一个线程能操作 Hashtable，避免并发修改问题，但锁竞争激烈，性能极低（尤其是高并发场景）。









  ```java
  // Hashtable 的 put 方法源码（加锁）
  public synchronized V put(K key, V value) {
      if (value == null) throw new NullPointerException(); // 禁止 null Value
      // ... 其他逻辑
  }
  ```



- **HashMap 非线程安全**：未加任何锁，多线程并发修改（如同时 `put()`）可能导致链表成环（JDK 8 前）、数据丢失等问题。若需线程安全，推荐使用 `ConcurrentHashMap`（JDK 1.5+ 引入，锁粒度更细，性能远优于 Hashtable），而非 Hashtable。







  ```java
  // HashMap 的 put 方法源码（无锁）
  public V put(K key, V value) {
      return putVal(hash(key), key, value, false, true);
  }
  ```



### 2. 空值支持：允许 null vs 禁止 null

- **HashMap**：

    - 允许 1 个 `null` Key（底层存储在数组索引 0 的位置）；
    - 允许多个 `null` Value（无限制，只要 Key 不同）。











  ```java
  HashMap<String, String> map = new HashMap<>();
  map.put(null, "value1"); // 合法
  map.put("key2", null);   // 合法
  map.put("key3", null);   // 合法
  System.out.println(map.get(null)); // 输出 "value1"
  ```



- **Hashtable**：

    - 禁止 `null` Key 和 `null` Value，一旦传入 `null`，直接抛出 `NullPointerException`。







  ```java
  Hashtable<String, String> table = new Hashtable<>();
  table.put(null, "value1"); // 抛 NullPointerException
  table.put("key2", null);   // 抛 NullPointerException
  ```



原因：Hashtable 设计时未考虑 `null` 键值的场景，源码中直接校验 `value == null` 并抛异常，Key 的 `hashCode()` 方法也不允许 `null`（`null.hashCode()` 会抛空指针）。

### 3. 底层实现与性能：红黑树优化 vs 纯链表

- **HashMap（JDK 8 优化）**：
    - 底层结构：数组 + 链表（哈希冲突时，链表存储冲突元素）；
    - 红黑树优化：当链表长度超过阈值（默认 8），且数组长度≥64 时，链表自动转为红黑树（查询时间复杂度从 O (n) 优化为 O (log n)）；
    - 哈希函数优化：通过 `hash(key) = key.hashCode() ^ (key.hashCode() >>> 16)` 扰动，将 Key 的高位哈希值融入低位，减少哈希冲突。
- **Hashtable**：
    - 底层结构：数组 + 链表（无红黑树优化），哈希冲突时链表一直扩展，查询效率随冲突增多急剧下降（最坏 O (n)）；
    - 哈希函数简单：直接使用 `key.hashCode()` 计算哈希值，未做扰动处理，哈希冲突概率高于 HashMap；
    - 扩容机制：默认初始容量 11，扩容后容量为 `oldCap * 2 + 1`（奇数容量理论上减少哈希冲突，但扩容后容量增长不均匀），而 HashMap 初始容量 16（2 的幂），扩容后为 2 倍（便于位运算计算数组索引）。

### 4. 遍历方式与快速失败（fail-fast）

- **遍历方式**：
    - HashMap：支持 `Iterator` 迭代器（推荐），不支持 `Enumeration`；
    - Hashtable：支持 `Iterator` 和 `Enumeration` 两种方式（`Enumeration` 是 JDK 1.0 遗留接口，功能比 `Iterator` 简单，不支持移除元素）。
- **快速失败（fail-fast）**：
    - 二者的 `Iterator` 都是快速失败迭代器：当迭代器遍历过程中，其他线程修改了集合（如 `put()`、`remove()`），会抛出 `ConcurrentModificationException`，避免迭代器遍历到不一致的数据；
    - 区别：Hashtable 的 `Enumeration` 不是快速失败，遍历过程中集合被修改不会抛异常，可能导致数据遍历不准确。

### 5. 继承关系与设计理念

- **HashMap**：
    - 继承 `AbstractMap` 类（`AbstractMap` 是 `Map` 接口的抽象实现，统一实现了 `Map` 的核心方法），设计更符合面向接口编程思想，与 `LinkedHashMap`、`TreeMap` 等 `Map` 实现类的继承体系一致。
- **Hashtable**：
    - 继承 `Dictionary` 类（JDK 1.0 遗留类，已被 `Map` 接口替代，官方标记为 “过时”），设计上存在历史包袱，与现代 `Map` 实现类的兼容性较差。

## 三、使用场景与替代方案

### 1. 何时用 HashMap？

- 单线程环境（如普通业务逻辑、本地缓存）；
- 多线程环境（需配合 `ConcurrentHashMap`，而非 Hashtable）；
- 需要存储 `null` 键值的场景；
- 对性能要求高，需处理大量数据（红黑树优化提升查询效率）。

### 2. 何时用 Hashtable？

- 仅用于维护 JDK 1.0 遗留系统（新代码绝对不推荐）；
- 需线程安全，但性能要求极低（如单线程并发、数据量极小），且无法使用 `ConcurrentHashMap`（如 JDK 版本＜1.5）。

### 3. 线程安全场景的推荐替代方案

Hashtable 的线程安全是 “低效的安全”（整机锁），现代开发中优先使用 `ConcurrentHashMap`：

- 锁粒度更细：JDK 8 中 `ConcurrentHashMap` 用「CAS + synchronized 分段锁」替代传统分段锁，锁粒度细化到哈希桶（数组元素），并发性能远优于 Hashtable；
- 支持高并发：多线程可同时操作不同哈希桶，无锁竞争；
- 功能更完善：支持 `null` Value（不支持 `null` Key）、原子操作（如 `putIfAbsent()`）等。

## 四、总结

| 特性     | HashMap                                   | Hashtable                         |
| -------- | ----------------------------------------- | --------------------------------- |
| 线程安全 | 否（需手动加锁或用 ConcurrentHashMap）    | 是（方法加 synchronized，性能低） |
| 空值支持 | 允许 1 个 null Key + 多个 null Value      | 禁止 null Key/Value（抛 NPE）     |
| 底层结构 | 数组 + 链表 + 红黑树（JDK 8+）            | 数组 + 链表（无红黑树）           |
| 性能     | 高（扰动哈希 + 红黑树优化）               | 低（无优化 + 整机锁）             |
| 适用场景 | 单线程 / 高并发（配合 ConcurrentHashMap） | 遗留系统维护（新代码不推荐）      |

**核心结论**：

- 新代码优先使用 **HashMap**（单线程）或 **ConcurrentHashMap**（多线程）；
- 避免使用 **Hashtable**，其线程安全可被 `ConcurrentHashMap` 替代，空值限制和低性能也不符合现代开发需求；
- 若需存储 `null` Key，只能用 HashMap（ConcurrentHashMap 不支持 `null` Key）。
