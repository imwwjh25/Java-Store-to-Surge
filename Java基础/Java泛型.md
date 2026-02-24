Java 泛型（Generics）是 JDK 5 引入的特性，核心目的是**在编译阶段提供类型安全检查**，同时实现代码复用（如通用容器类 `List<T>`、`Map<K,V>`）。其底层实现依赖 **“类型擦除”（Type Erasure）**，而非真正的 “运行时泛型”，这与 C# 等语言的泛型实现有本质区别。

### 一、泛型的核心实现：类型擦除（Type Erasure）

Java 泛型仅在**编译阶段有效**，编译器会在编译时 “擦除” 泛型类型信息，将泛型代码转换为普通的非泛型代码（即 “原始类型”），运行时不保留泛型类型。具体规则如下：

1. **擦除类型参数**：

    - 若泛型类型有**上限**（如 `T extends Number`），则擦除为该上限类型；
    - 若泛型类型无上限（如 `T`），则擦除为 `Object`。

   示例：






   ```java
   // 泛型类
   class Box<T> {
       private T value;
       public T getValue() { return value; }
       public void setValue(T value) { this.value = value; }
   }
   
   // 编译后被擦除为（伪代码）：
   class Box {
       private Object value;
       public Object getValue() { return value; }
       public void setValue(Object value) { this.value = value; }
   }
   ```



带上限的泛型：








   ```java
   class NumberBox<T extends Number> {
       private T value;
       public T getValue() { return value; }
   }
   
   // 编译后擦除为：
   class NumberBox {
       private Number value;
       public Number getValue() { return value; }
   }
   ```



2. **插入类型转换**：编译器会在泛型代码的 “使用处” 自动插入类型转换代码，保证类型安全。

   示例：




   ```java
   Box<String> box = new Box<>();
   box.setValue("hello");
   String str = box.getValue(); // 编译时自动插入 (String) 转换
   ```



编译后等价于：



   ```java
   Box box = new Box();
   box.setValue("hello");
   String str = (String) box.getValue(); // 显式类型转换
   ```



3. **处理泛型方法**：泛型方法的类型参数同样会被擦除，若有多个重载的泛型方法，擦除后可能出现冲突，此时编译器会通过 “桥接方法”（Bridge Method）解决。

   示例：





   ```java
   class Parent<T> {
       public void setValue(T value) {}
   }
   
   class Child extends Parent<String> {
       @Override
       public void setValue(String value) {} // 重写父类方法
   }
   ```



擦除后父类方法变为 `setValue(Object)`，子类方法 `setValue(String)` 并非重写（参数类型不同），编译器会自动生成**桥接方法**：





   ```java
   class Child extends Parent {
       // 桥接方法：重写父类的 setValue(Object)
       public void setValue(Object value) {
           setValue((String) value); // 调用子类的 setValue(String)
       }
       // 子类自己的方法
       public void setValue(String value) {}
   }
   ```



### 二、泛型擦除的局限（为什么 Java 泛型不是 “真泛型”）

由于类型信息在运行时被擦除，Java 泛型存在一些天然限制：

1. **无法实例化泛型类型**：不能直接 `new T()`，因为运行时 `T` 已被擦除为 `Object`（或上限类型），编译器无法确定具体类型。解决：通过反射 `clazz.newInstance()`，需传入类型 `Class<T>`。
2. **无法使用基本类型作为泛型参数**：泛型参数必须是引用类型（如 `List<Integer>` 可行，`List<int>` 编译报错），因为类型擦除后会转换为 `Object`（基本类型不能赋值给 `Object`）。
3. **泛型数组创建受限**：不能直接 `new T[10]`，但可通过 `(T[]) new Object[10]` 间接创建（需抑制 unchecked 警告）。
4. **运行时无法判断泛型类型**：`instanceof` 不能用于泛型类型（如 `if (list instanceof List<String>)` 编译报错），因为运行时 `List<String>` 和 `List<Integer>` 都被擦除为 `List`。

### 三、总结

Java 泛型的实现核心是 **“编译期类型擦除”**：

- 编译时，编译器检查泛型类型的合法性，确保类型安全；
- 编译后，泛型类型被擦除为原始类型（`Object` 或上限类型），并自动插入类型转换代码；
- 运行时，JVM 无法感知泛型类型，仅处理原始类型。

这种设计是为了兼容 JDK 5 之前的非泛型代码（向后兼容），但也带来了一些局限。理解类型擦除，是掌握 Java 泛型坑点（如泛型数组、桥接方法）的关键。
