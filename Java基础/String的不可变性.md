Java 中的 `String` 类通过**类设计、字段修饰和方法实现**三个层面保证了不可变性，具体如下：

### 1. 类声明为 `final`，禁止继承

`String` 类被 `final` 关键字修饰，意味着它不能被继承。这避免了子类通过重写方法（如 `substring`、`replace` 等）破坏其不可变特性。



```java
public final class String { ... }
```

### 2. 内部字符数组 `value` 被 `private final` 修饰

`String` 的核心数据存储在一个 `char[]` 数组（JDK 9+ 改为 `byte[]`，优化空间占用）中，这个数组被 `private` 和 `final` 双重修饰：

- `private`：确保外部无法直接访问或修改该数组；
- `final`：确保数组引用一旦初始化后，不能指向其他数组（但数组本身的元素理论上可被修改，这一点通过其他机制限制）。


```java
// JDK 8 及之前的实现
private final char[] value;

// JDK 9+ 改为 byte[]，并增加编码标识 coder
private final byte[] value;
private final byte coder;
```

### 3. 所有修改方法返回新对象，不修改原对象

`String` 类中所有看似 “修改” 字符串的方法（如 `substring`、`replace`、`concat` 等），实际上都不会修改原对象的 `value` 数组，而是**创建新的 `String` 对象**，并复制原数组的部分内容到新数组中。

例如 `substring` 方法（简化逻辑）：


```java
public String substring(int beginIndex) {
    // 复制原数组的子序列，创建新的String对象
    return new String(Arrays.copyOfRange(value, beginIndex, value.length));
}
```

再如 `concat` 方法：


```java
public String concat(String str) {
    int newLength = value.length + str.value.length;
    char[] newValue = Arrays.copyOf(value, newLength); // 复制原数组
    System.arraycopy(str.value, 0, newValue, value.length, str.value.length); // 拼接新内容
    return new String(newValue); // 返回新对象
}
```

### 4. 禁止外部修改内部数组（防御性拷贝）

虽然 `final` 修饰的数组引用不可变，但数组本身是可变的（例如通过反射可以修改数组元素）。不过 `String` 类通过以下方式避免这种情况：

- 所有返回内部数组的方法（如 `toCharArray()`）都会返回数组的**拷贝**，而非原数组引用，防止外部通过修改拷贝影响原对象。


```java
public char[] toCharArray() {
    char[] result = new char[value.length];
    System.arraycopy(value, 0, result, 0, value.length); // 拷贝数组
    return result;
}
```

### 总结

`String` 的不可变性是通过：

1. `final` 类禁止继承；
2. `private final` 修饰的内部数组防止直接修改；
3. 所有 “修改” 操作返回新对象而非修改原对象；
4. 防御性拷贝避免内部数组被外部篡改。

这些设计共同保证了 `String` 对象一旦创建，其内部字符序列就无法被修改，从而实现了不可变性。
