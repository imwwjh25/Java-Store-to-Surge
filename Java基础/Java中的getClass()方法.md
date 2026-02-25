### 1. Java 中 Object 类的方法

Object 类是所有 Java 类的根类，包含以下核心方法：

- `toString()`：返回对象的字符串表示（默认是类名 + 哈希码），通常子类会重写。
- `equals(Object obj)`：判断对象是否相等（默认比较引用地址），子类可重写实现值比较（如 String）。
- `hashCode()`：返回对象的哈希码，需满足 “equals 相等则 hashCode 相等” 的约定。
- `getClass()`：返回对象的运行时类（`Class`对象），属于 final 方法。
- `notify()`：唤醒当前对象监视器上等待的单个线程。
- `notifyAll()`：唤醒当前对象监视器上等待的所有线程。
- `wait()`/`wait(long)`/`wait(long, int)`：使当前线程等待，释放对象锁，需在`synchronized`块中调用。
- `clone()`：创建对象的副本（浅拷贝），需实现`Cloneable`接口，否则抛`CloneNotSupportedException`。
- `finalize()`：对象被 GC 回收前调用（Java 9 后标记为 Deprecated，推荐用 Cleaner 替代）。

### 2. `getClass()`方法的使用场景

`getClass()`用于获取对象的**运行时类**（区别于编译时类），常见场景：

- **反射操作**：通过`getClass()`获取`Class`对象，进而动态创建实例、调用方法（如`clazz.newInstance()`）。
- **类型判断**：如`obj.getClass() == String.class`判断对象实际类型（优于`instanceof`的精确类型匹配）。
- **序列化 / 反序列化**：获取类的元数据（如类名、字段）用于序列化。
- **框架中的类型处理**：如 Spring 中通过`getClass()`识别 Bean 的实际类型，MyBatis 中获取实体类的映射信息。
- **日志 / 调试**：打印对象的实际类名，便于定位问题。
