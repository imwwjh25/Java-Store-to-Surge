# 重写 hashCode/equals 不匹配的后果：核心原理 + 场景示例

要理解这个问题，首先要记住 Java 中 `hashCode()` 和 `equals()` 的**核心约定**（来自 Object 类规范）：

1. 若两个对象 `equals()` 返回 `true`，则它们的 `hashCode()` 必须返回相同值；
2. 若两个对象 `hashCode()` 返回不同值，则它们的 `equals()` 必须返回 `false`；
3. 若两个对象 `equals()` 返回 `false`，`hashCode()` 可相同可不同（但相同会降低哈希表效率）。

简单说：**`equals()` 决定 “对象是否相等”，`hashCode()` 决定 “对象在哈希结构中的存储位置”**，二者必须同步重写，否则会破坏哈希容器（如 HashMap、HashSet）的核心逻辑。

## 一、只重写 hashCode ()，不重写 equals ()：相等对象被判定为不同

### 核心后果

- 两个逻辑上相等的对象（如两个 `User` 实例，id 相同），因未重写 `equals()`，会调用 Object 类的默认 `equals()`（比较对象内存地址），判定为 `false`；
- 但因重写了 `hashCode()`，它们的哈希值相同，会被放入哈希容器的同一个桶中；
- 最终导致：哈希容器（如 HashSet）无法去重，HashMap 中相同逻辑的 Key 会被当作不同 Key 存储。

### 场景示例（User 类仅重写 hashCode ()）











```java
class User {
    private Integer id;
    private String name;

    // 仅重写 hashCode()：按 id 计算哈希值
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // 未重写 equals()，使用 Object 类默认实现（比较内存地址）

    // 构造器、getter 省略
}

// 测试代码
public class Test {
    public static void main(String[] args) {
        User u1 = new User(1, "张三");
        User u2 = new User(1, "张三"); // 逻辑上相等（id 相同）

        // 1. equals() 结果：false（默认比较内存地址，u1 和 u2 是不同对象）
        System.out.println(u1.equals(u2)); // 输出 false

        // 2. hashCode() 结果：true（按 id 计算，哈希值相同）
        System.out.println(u1.hashCode() == u2.hashCode()); // 输出 true

        // 3. HashSet 无法去重（认为 u1 和 u2 是不同对象）
        HashSet<User> set = new HashSet<>();
        set.add(u1);
        set.add(u2);
        System.out.println(set.size()); // 输出 2（预期是 1）

        // 4. HashMap 中相同逻辑 Key 存为不同 Entry
        HashMap<User, String> map = new HashMap<>();
        map.put(u1, "北京");
        map.put(u2, "上海");
        System.out.println(map.size()); // 输出 2（预期是 1，覆盖 value）
    }
}
```

### 原因分析

- Object 类默认 `equals()`：`return this == obj;`，仅当两个对象是同一个内存地址时才返回 `true`；
- 虽然 u1 和 u2 逻辑相等（id 相同），但 `equals()` 返回 `false`，哈希容器会认为是两个不同对象，即使它们的 `hashCode()` 相同（存入同一个桶），也会通过链表 / 红黑树存储在同一桶中，导致去重、Key 唯一等特性失效。

## 二、只重写 equals ()，不重写 hashCode ()：相等对象被散列到不同位置

### 核心后果

- 两个逻辑上相等的对象（`equals()` 返回 `true`），因未重写 `hashCode()`，会调用 Object 类的默认 `hashCode()`（按对象内存地址计算哈希值），返回不同值；
- 哈希容器（如 HashMap）会将它们散列到不同的桶中，导致：HashSet 无法去重、HashMap 中相同逻辑的 Key 无法覆盖，且查询效率暴跌（哈希表退化为链表遍历）。

### 场景示例（User 类仅重写 equals ()）







```java
class User {
    private Integer id;
    private String name;

    // 重写 equals()：id 相同则认为相等
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    // 未重写 hashCode()，使用 Object 类默认实现（按内存地址计算）

    // 构造器、getter 省略
}

// 测试代码
public class Test {
    public static void main(String[] args) {
        User u1 = new User(1, "张三");
        User u2 = new User(1, "张三"); // 逻辑上相等（equals() 返回 true）

        // 1. equals() 结果：true（按 id 判定相等）
        System.out.println(u1.equals(u2)); // 输出 true

        // 2. hashCode() 结果：false（默认按内存地址计算，哈希值不同）
        System.out.println(u1.hashCode() == u2.hashCode()); // 输出 false

        // 3. HashSet 无法去重（散列到不同桶，认为是不同对象）
        HashSet<User> set = new HashSet<>();
        set.add(u1);
        set.add(u2);
        System.out.println(set.size()); // 输出 2（预期是 1）

        // 4. HashMap 中相同逻辑 Key 存为不同 Entry，无法覆盖
        HashMap<User, String> map = new HashMap<>();
        map.put(u1, "北京");
        map.put(u2, "上海");
        System.out.println(map.size()); // 输出 2（预期是 1）

        // 5. 查询时无法找到对应 Value（散列到错误桶）
        System.out.println(map.get(new User(1, "张三"))); // 输出 null（预期是 "上海"）
    }
}
```

### 原因分析

1. **违背核心约定**：`equals()` 返回 `true` 但 `hashCode()` 不同，直接破坏了 Java 的哈希约定；

2. 哈希容器逻辑失效

   ：

    - HashMap 存储 Key 时，先通过 `hashCode()` 定位桶位置，再通过 `equals()` 比较桶内元素是否相同；
    - 因 u1 和 u2 的 `hashCode()` 不同，会被放入不同桶，`equals()` 即使返回 `true`，也不会被判定为同一个 Key，导致去重、覆盖、查询功能全部失效；

3. **性能问题**：若大量逻辑相等的对象散列到不同桶，会导致哈希表的 “散列均匀性” 被破坏，查询时需要遍历多个桶，性能从 O (1) 退化为 O (n)。

## 三、关键补充：什么时候会 “没影响”？

只有一种场景下，不按规则重写不会出问题：**对象不用于任何哈希容器（HashMap、HashSet、HashTable 等）**，仅通过 `equals()` 比较对象是否相等。

例如：仅用于普通对象的相等判断（如 `if (u1.equals(u2)) { ... }`），此时 `hashCode()` 不会被调用，仅重写 `equals()` 或仅重写 `hashCode()` 都不会有直接影响。

但这种场景极少，且代码可维护性差（后续若改为哈希容器存储，会直接触发 bug），因此**无论是否使用哈希容器，都应同步重写 `equals()` 和 `hashCode()`**。

## 四、正确的重写姿势（IDE 自动生成，推荐）

重写的核心原则：**`equals()` 中用到的字段，必须全部在 `hashCode()` 中用到**（确保相等对象的哈希值一定相同）。

### 示例（按 id 和 name 判定相等）








```java
class User {
    private Integer id;
    private String name;

    // 同步重写 equals() 和 hashCode()，用到的字段完全一致
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(name, user.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name); // 与 equals() 字段一致
    }

    // 构造器、getter 省略
}
```

### 工具推荐

- IDE（IntelliJ IDEA/Eclipse）可自动生成：右键 → Generate → equals () and hashCode ()；
- 推荐使用 `Objects.hash(字段1, 字段2...)` 生成哈希值，避免手动计算导致的哈希碰撞风险。

## 总结

| 重写情况             | 核心后果                                                     | 影响场景                           |
| -------------------- | ------------------------------------------------------------ | ---------------------------------- |
| 只重写 hashCode ()   | 逻辑相等的对象 `equals()` 返回 false，哈希容器无法去重、Key 不唯一 | 所有哈希容器（HashMap/HashSet 等） |
| 只重写 equals ()     | 逻辑相等的对象 `hashCode()` 不同，散列到不同桶，哈希容器功能全失效 + 性能差 | 所有哈希容器（HashMap/HashSet 等） |
| 同步重写（正确姿势） | 遵循 Java 约定，哈希容器功能正常，对象相等判断符合预期       | 所有场景（推荐）                   |

核心记忆点：**`equals()` 和 `hashCode()` 必须 “同生共死”—— 要么都不重写（用默认），要么都重写（字段一致）**，否则哈希容器会出现逻辑错乱。