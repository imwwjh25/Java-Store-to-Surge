# HashSet 实现原理与去重机制（Java 核心解析）



HashSet 是 Java 集合框架中 `Set` 接口的经典实现，核心特点是 **无序、不重复、允许存储 `null` 元素**。其底层完全依赖 `HashMap` 实现（可以理解为 “用 HashMap 封装的特殊集合”），去重机制也直接复用了 HashMap 的键唯一性特性。

## 一、HashSet 底层实现原理（核心依赖 HashMap）



### 1. 类结构与核心成员



先看 `HashSet` 的关键源码（基于 JDK 8），能直观理解其实现逻辑：
```
public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, java.io.Serializable {
    // 核心：HashSet 内部持有一个 HashMap 实例（所有操作都委托给它）
    private transient HashMap<E, Object> map;

    // 固定的“占位值”：HashMap 是键值对结构，HashSet 只需用键存元素，值用这个常量填充
    private static final Object PRESENT = new Object();

    // 构造方法：初始化内部的 HashMap
    public HashSet() {
        map = new HashMap<>();
    }

    // 其他构造方法（如指定初始容量、加载因子、传入集合）本质都是初始化 HashMap
    public HashSet(int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }
}
```



**核心结论**：HashSet 本身没有独立的数据结构，完全是 HashMap 的 “包装类”——

- HashSet 存储的「元素」，对应 HashMap 的「键（Key）」；
- HashSet 不需要存储值，所以用一个固定的 `PRESENT`（空对象）作为 HashMap 的「值（Value）」；
- HashMap 的 Key 天然不允许重复（重复 Key 会覆盖），这就是 HashSet 去重的核心基础。

### 2. 核心操作的委托逻辑



HashSet 的所有方法（添加、删除、查询）都直接委托给内部的 HashMap 执行，示例如下：

| HashSet 方法         | 底层 HashMap 操作             | 说明                                                         |
| -------------------- | ----------------------------- | ------------------------------------------------------------ |
| `add(E e)`           | `map.put(e, PRESENT) == null` | 把元素 e 作为 Key 存入 HashMap，值为 PRESENT；若 Key 已存在，put 会返回旧值（非 null），add 则返回 false |
| `remove(E e)`        | `map.remove(e) == PRESENT`    | 移除 HashMap 中 Key 为 e 的条目，若存在则返回 true           |
| `contains(Object o)` | `map.containsKey(o)`          | 判断 HashMap 中是否存在 Key 为 o 的条目                      |
| `size()`             | `map.size()`                  | 直接返回 HashMap 中 Key 的数量（即 HashSet 元素个数）        |
| `clear()`            | `map.clear()`                 | 清空 HashMap 所有条目                                        |

示例代码验证：


```
HashSet<String> set = new HashSet<>();
set.add("a"); // 底层执行 map.put("a", PRESENT)，返回 null → add 返回 true
set.add("a"); // 底层执行 map.put("a", PRESENT)，返回旧值 PRESENT → add 返回 false（去重）
System.out.println(set.contains("a")); // 底层 map.containsKey("a") → true
System.out.println(set.size()); // 底层 map.size() → 1
set.remove("a"); // 底层 map.remove("a") → 返回 PRESENT → remove 返回 true
```



## 二、HashSet 如何保证元素不重复？（核心去重机制）



去重的核心依赖 **HashMap 的 Key 唯一性**，而 HashMap 保证 Key 唯一的逻辑是「哈希值校验 + 相等性校验」，具体流程如下：

### 1. 去重的完整流程（以 `add(E e)` 为例）



当调用 `hashSet.add(e)` 时，底层会通过 HashMap 的 `put(K key, V value)` 方法实现去重，步骤拆解：

1. **计算元素的哈希值**：调用元素 e 的 `hashCode()` 方法，得到哈希值（hash）；

2. **定位哈希桶位置**：HashMap 根据哈希值计算元素应存入的 “哈希桶”（数组索引）；

3. 校验哈希桶中是否有重复元素

   ：

   - 若哈希桶为空：直接将元素 e 作为 Key 存入该桶，add 返回 true；

   - 若哈希桶不为空：遍历桶中的元素（链表或红黑树结构），对每个元素```k```做双重校验：

     a. 先比较哈希值： ```e.hashCode() == k.hashCode()``` （快速排除不相等元素）；

     b. 再比较实际相等性：```e == k```（同一对象）或```e.equals(k)```（内容相等）；

4. 判断是否重复：

   - 若存在满足 “哈希值相等 + equals 相等” 的元素 `k`：说明元素重复，HashMap 会用新的 Value（PRESENT）覆盖旧 Value，`put` 方法返回旧 Value（非 null），因此 HashSet 的 `add` 方法返回 false，元素不被添加；
   - 若不存在上述元素：说明元素不重复，将其存入哈希桶，`put` 返回 null，`add` 返回 true。

### 2. 关键前提：元素必须正确重写 `hashCode()` 和 `equals()`



HashSet 的去重逻辑依赖元素的 `hashCode()` 和 `equals()` 方法，若未正确重写，会导致 “逻辑上重复的元素无法去重”。

#### 反例：未重写 `hashCode()` 和 `equals()`


```
class Person {
    private String name;
    private int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

public class Test {
    public static void main(String[] args) {
        HashSet<Person> set = new HashSet<>();
        set.add(new Person("张三", 20));
        set.add(new Person("张三", 20)); // 逻辑上重复，但 HashSet 无法识别
        System.out.println(set.size()); // 输出 2（去重失败）
    }
}
```



**原因**：两个 `Person` 对象是不同的实例，默认的 `hashCode()` 会返回不同的哈希值（基于对象内存地址），`equals()` 也会比较内存地址，因此 HashMap 认为是两个不同的 Key，HashSet 无法去重。

#### 正例：正确重写 `hashCode()` 和 `equals()`


```
class Person {
    private String name;
    private int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // 重写 equals()：当 name 和 age 都相同时，认为是同一个对象
    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // 同一对象直接返回 true
        if (o == null || getClass() != o.getClass()) return false; // 非 Person 类型或 null 返回 false
        Person person = (Person) o;
        return age == person.age && Objects.equals(name, person.name); // 比较核心字段
    }

    // 重写 hashCode()：基于 equals() 中的核心字段计算哈希值
    @Override
    public int hashCode() {
        return Objects.hash(name, age); // 用 Objects 工具类生成哈希值，保证 equals 相等时 hashCode 也相等
    }
}

public class Test {
    public static void main(String[] args) {
        HashSet<Person> set = new HashSet<>();
        set.add(new Person("张三", 20));
        set.add(new Person("张三", 20)); // 逻辑重复，HashSet 识别并去重
        System.out.println(set.size()); // 输出 1（去重成功）
    }
}
```



**核心原则**：

- 若 `a.equals(b) == true`，则 `a.hashCode()` 必须等于 `b.hashCode()`（保证同一元素存入同一个哈希桶）；
- 若 `a.hashCode() == b.hashCode()`，`a.equals(b)` 不一定为 true（哈希冲突，需进一步比较内容）。

### 3. 特殊情况：存储 `null` 元素



HashSet 允许存储一个 `null` 元素，原因是 HashMap 的 Key 可以为 `null`：

- `null` 的 `hashCode()` 会被 HashMap 特殊处理，固定存入索引为 0 的哈希桶；
- 再次添加 `null` 时，HashMap 会发现该 Key 已存在，因此 HashSet 只允许一个 `null`。

示例：


```
HashSet<String> set = new HashSet<>();
set.add(null);
set.add(null); // 重复添加，失败
System.out.println(set.size()); // 输出 1
```



## 三、补充：HashSet 的无序性与性能



### 1. 无序性原因



HashSet 的元素顺序由「元素的哈希值 + 哈希桶的结构」决定，而非插入顺序：

- 元素的哈希值可能不连续，导致存入的哈希桶索引无序；
- 同一哈希桶中若元素较多（哈希冲突），会以链表或红黑树存储，遍历顺序也与插入顺序无关。

若需要 “有序的 Set”，可使用 `LinkedHashSet`（底层是 `LinkedHashMap`，维护插入顺序）。

### 2. 性能特点



- **添加 / 查询 / 删除效率**：理想情况下（哈希分布均匀，无冲突），时间复杂度为 O (1)（直接通过哈希值定位哈希桶）；
- **最坏情况**：所有元素哈希冲突，存入同一个哈希桶（链表结构），时间复杂度退化为 O (n)；JDK 8 后若哈希桶中元素超过 8 个，会转为红黑树，最坏时间复杂度优化为 O (log n)；
- **初始容量与加载因子**：默认初始容量 16，加载因子 0.75（当元素个数达到 16×0.75=12 时，HashMap 会扩容为原来的 2 倍），可通过构造方法自定义（如 `new HashSet<>(32, 0.8f)`）。

## 四、总结



1. **实现原理**：HashSet 底层基于 HashMap，元素存储在 HashMap 的 Key 上，Value 用固定的 `PRESENT` 填充，无独立数据结构；
2. **去重机制**：依赖 HashMap 的 Key 唯一性，通过「哈希值校验（hashCode ()）+ 内容校验（equals ()）」实现，二者必须正确重写；
3. **核心特点**：无序、不重复、允许一个 `null`，理想时间复杂度 O (1)；
4. **关键注意**：自定义对象存入 HashSet 时，必须重写 `hashCode()` 和 `equals()`，否则会导致去重失效。
