在 Java 中，对 `List` 进行**一边遍历一边删除**的操作需要格外注意，直接使用普通 `for` 循环（从头到尾）或增强 `for` 循环（foreach）可能会抛出 `ConcurrentModificationException` 异常，而从尾到头遍历则可以安全实现。

### 1. 为什么 “从头到尾遍历删除” 会出问题？

#### （1）增强 for 循环（foreach）的问题

增强 for 循环底层依赖 `Iterator` 迭代器，迭代器内部维护了一个 “修改次数计数器”。当通过 `List` 的 `remove()` 方法删除元素时，计数器不会同步更新，迭代器会检测到 “预期修改次数” 与 “实际修改次数” 不一致，从而抛出 `ConcurrentModificationException`。







```java
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
// 错误示例：增强 for 循环中删除元素，会抛异常
for (String s : list) {
    if (s.equals("b")) {
        list.remove(s); // 抛出 ConcurrentModificationException
    }
}
```

#### （2）普通 for 循环（从头到尾）的问题

普通 for 循环通过索引遍历，删除元素后会导致**后续元素的索引前移**，若不调整索引，会跳过下一个元素（漏删）。









```java
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "b", "c"));
// 错误示例：从头到尾遍历，删除后索引未调整，导致漏删
for (int i = 0; i < list.size(); i++) {
    if (list.get(i).equals("b")) {
        list.remove(i); // 删除索引 i 的元素后，后续元素前移
        // 若不 i--，下一次循环会跳过原索引 i+1（现在的 i）的元素
    }
}
System.out.println(list); // 输出 [a, b, c]（漏删了第二个 "b"）
```

**修复方式**：删除后手动将索引减 1（`i--`），但这种方式容易出错，且效率较低（删除元素后 `size()` 会动态变化，需反复计算）。

### 2. 为什么 “从尾到头遍历删除” 可以安全实现？

从尾到头遍历（索引从 `size-1` 递减到 `0`）时，删除当前元素不会影响**前面未遍历元素的索引**（因为前面的元素在左侧，删除右侧元素后左侧索引不变），因此不会漏删，也不会触发异常。









```java
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "b", "c"));
// 正确示例：从尾到头遍历，安全删除
for (int i = list.size() - 1; i >= 0; i--) {
    if (list.get(i).equals("b")) {
        list.remove(i); // 删除后，前面的元素索引不受影响
    }
}
System.out.println(list); // 输出 [a, c]（正确删除所有 "b"）
```

### 3. 更推荐的方式：使用迭代器（`Iterator`）删除

`Iterator` 自身提供了 `remove()` 方法，删除时会同步更新 “修改次数计数器”，避免 `ConcurrentModificationException`，是最规范的做法（无需关心遍历方向）。










```java
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "b", "c"));
Iterator<String> iterator = list.iterator();
// 正确示例：用迭代器的 remove() 方法
while (iterator.hasNext()) {
    String s = iterator.next();
    if (s.equals("b")) {
        iterator.remove(); // 迭代器内部会同步修改次数，安全删除
    }
}
System.out.println(list); // 输出 [a, c]
```

### 总结

- **不推荐**：增强 for 循环（会抛异常）、普通 for 循环从头到尾（易漏删，需手动调整索引）。

- 推荐 ：

    1. 从尾到头遍历（通过普通 for 循环，适合简单场景）。
    2. 使用 `Iterator` 的 `remove()` 方法（最规范，无副作用，优先选择）。

本质上，边遍历边删除的核心风险是 “元素索引变化” 或 “迭代器状态不一致”，选择合适的遍历方式即可避免问题。