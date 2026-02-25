HashMap 是 Java 中常用的哈希表实现，其构造函数和遍历方式都有明确的设计场景。以下是详细说明：

### 一、HashMap 的构造函数（共 4 种）

HashMap 提供了 4 种构造函数，核心是通过指定**初始容量**和**负载因子**来优化哈希表的性能（减少扩容次数）。

| 构造函数                                         | 说明                                                         | 适用场景                                                     |
| ------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| `HashMap()`                                      | 默认构造：初始容量 16，负载因子 0.75（扩容阈值 = 容量 × 负载因子） | 大多数场景，无需提前预估数据量，依赖自动扩容。               |
| `HashMap(int initialCapacity)`                   | 指定初始容量，负载因子默认 0.75                              | 已知大致数据量，避免频繁扩容（如预估存储 1000 条数据，初始容量设为 1000/0.75≈1334）。 |
| `HashMap(int initialCapacity, float loadFactor)` | 同时指定初始容量和负载因子                                   | 对性能有极致要求的场景（如高并发写入）：负载因子低（如 0.5）减少哈希冲突，但占用更多内存；负载因子高（如 0.8）节省内存，但冲突概率增加。 |
| `HashMap(Map<? extends K, ? extends V> m)`       | 基于已有 Map 初始化，容量为 “原 Map 大小的 1.5 倍” 与 “16” 的最大值，负载因子 0.75 | 需要将其他 Map（如 Hashtable、TreeMap）转换为 HashMap 时使用。 |

### 二、HashMap 的遍历方式（5 种）及适用场景

HashMap 的遍历本质是对**键（Key）、值（Value）、键值对（Entry）** 的遍历，不同方式的性能和适用场景不同：

#### 1. 遍历键集合（`keySet()`）







```java
HashMap<String, Integer> map = new HashMap<>();
// 1. 遍历键，再通过键获取值
for (String key : map.keySet()) {
    Integer value = map.get(key);
}
```

- **原理**：先获取所有键的集合（`keySet`），再通过 `get(key)` 查值。
- **缺点**：`get(key)` 是 O (1) 操作，但遍历键后再查值相当于两次哈希计算，效率略低。
- **适用场景**：仅需要键（Key），或同时需要键和值但数据量小、对性能要求不高。

#### 2. 遍历值集合（`values()`）




```java
// 2. 仅遍历值（无法获取对应键）
for (Integer value : map.values()) {
    // 处理 value
}
```

- **原理**：直接获取所有值的集合（`values`），遍历值。
- **缺点**：只能获取值，无法获取对应键。
- **适用场景**：仅需要值（Value），无需关心键（如统计所有值的总和）。

#### 3. 遍历键值对（`entrySet()`，推荐）





```java
// 3. 遍历键值对（Entry），同时获取键和值（推荐）
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    String key = entry.getKey();
    Integer value = entry.getValue();
}
```

- **原理**：获取键值对集合（`entrySet`），每个 `Entry` 对象直接包含键和值，无需二次查询。
- **优点**：一次遍历即可获取键和值，性能最优（推荐使用）。
- **适用场景**：需要同时处理键和值，且对性能有要求（大多数业务场景）。

#### 4. 迭代器（`Iterator`）遍历（支持删除）










```java
// 4. 使用迭代器遍历 entrySet（支持在遍历中安全删除元素）
Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
while (iterator.hasNext()) {
    Map.Entry<String, Integer> entry = iterator.next();
    String key = entry.getKey();
    Integer value = entry.getValue();
    // 遍历中删除元素（必须用 iterator.remove()，否则抛 ConcurrentModificationException）
    if (key.startsWith("test")) {
        iterator.remove();
    }
}
```

- **原理**：通过迭代器遍历 `entrySet`，支持在遍历中修改集合（删除元素）。
- **优点**：可在遍历中安全删除元素（`for-each` 循环中删除会抛异常）。
- **适用场景**：需要在遍历过程中删除元素（如过滤无效键值对）。

#### 5. JDK 8+ 流式遍历（`forEach` + Lambda）








```java
// 5. 流式遍历（JDK 8+）
map.forEach((key, value) -> {
    // 处理 key 和 value
});
```

- **原理**：基于函数式接口，简化遍历代码（内部仍基于 `entrySet`）。
- **优点**：代码简洁，适合 JDK 8+ 环境。
- **缺点**：遍历中无法删除元素（会抛异常）。
- **适用场景**：仅需要遍历处理，无需修改集合，追求代码简洁。

### 三、遍历方式对比及最佳实践

| 遍历方式       | 能否获取键 | 能否获取值   | 能否删除元素                  | 性能 | 适用场景                     |
| -------------- | ---------- | ------------ | ----------------------------- | ---- | ---------------------------- |
| `keySet()`     | 能         | 能（需查询） | 否（`for-each` 中删除抛异常） | 一般 | 仅需键，或数据量小           |
| `values()`     | 否         | 能           | 否                            | 较好 | 仅需值                       |
| `entrySet()`   | 能         | 能           | 否                            | 最优 | 需键值对，无删除操作         |
| `Iterator`     | 能         | 能           | 能（安全删除）                | 最优 | 需键值对，且需要删除元素     |
| 流式 `forEach` | 能         | 能           | 否                            | 较好 | JDK 8+，仅遍历处理，代码简洁 |

**最佳实践**：

- 大多数场景优先用 `entrySet()` 遍历（性能最优，兼顾键值对）；
- 需要删除元素时用 `Iterator` 遍历 `entrySet()`；
- 仅需值时用 `values()`；
- JDK 8+ 且无需删除时，用流式 `forEach` 简化代码。

### 总结

HashMap 的构造函数通过初始容量和负载因子优化性能，遍历方式则根据 “是否需要键 / 值”“是否删除元素”“代码简洁性” 选择：`entrySet()` 是最通用的高效方式，迭代器适合需要删除元素的场景，流式遍历适合简洁代码。
