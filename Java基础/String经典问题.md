#### 核心差异：String 的不可变性 vs StringBuilder 的可变性



Java 中`String`是**不可变对象**（final 类，字符数组不可修改），而`StringBuilder`是**可变字符序列**（字符数组可扩容修改），两者在循环拼接时的性能和内存表现天差地别：

##### （1）`str += "hello"`的执行逻辑



每次`+=`操作并非直接修改原字符串，而是：

1. 创建新的`StringBuilder`对象；
2. 将原`str`和`"hello"`拼接到`StringBuilder`；
3. 调用`toString()`生成新的`String`对象；
4. 原`str`对象被废弃，等待 GC 回收。

**循环 1000 次的问题**：

- 产生**1000 个废弃的 String 对象**和**1000 个临时 StringBuilder 对象**，内存开销极大；
- 每次拼接需复制字符数组，时间复杂度为**O(n²)**（n 为拼接次数），性能极低。

##### （2）`StringBuilder("hello")`的执行逻辑



`StringBuilder`的字符数组可动态扩容，循环拼接时：

1. 仅创建**1 个 StringBuilder 对象**；
2. 调用`append("hello")`直接在原有字符数组后追加内容（无需创建新对象）；
3. 最终调用`toString()`生成结果字符串。

**循环 1000 次的优势**：

- 无临时对象产生，内存利用率高；
- `append`操作平均时间复杂度为**O(1)**（扩容时为 O (n)，但扩容次数少），整体时间复杂度接近**O(n)**，性能远超`String`拼接。

##### 性能对比（实测）



- 循环 1000 次拼接：`String`拼接耗时约**10ms**（因大量对象创建和 GC），`StringBuilder`耗时约**0.1ms**（仅数组操作）；
- 循环 10 万次拼接：`String`拼接可能耗时**秒级**，`StringBuilder`仍为**毫秒级**。
