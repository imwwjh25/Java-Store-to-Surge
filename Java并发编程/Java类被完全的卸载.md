### Java 类被卸载的核心条件

Java 类的卸载（Unloading）本质上是指该类的`Class`对象及其相关数据被垃圾回收（GC），而这需要满足**严格的前提条件**，核心可以总结为：**该类的所有实例都已被回收，且加载该类的类加载器也已被回收，同时没有任何地方引用该类的`Class`对象**。

下面分步骤详细解释这些条件：

#### 1. 基础前提：类的所有实例已被完全回收

这是最基础的条件：

- 该类创建的**所有对象实例**都已经成为垃圾，被 GC 回收（即堆中不存在该类的任何实例）。
- 即使有一个该类的实例还存在，这个类也绝对不会被卸载。

#### 2. 关键条件：加载该类的类加载器已无引用

这是类卸载最核心的条件，也是新手最容易忽略的点：

- JVM 中，类的生命周期与加载它的**类加载器**强绑定。每个`Class`对象都会持有对其类加载器的引用，反之类加载器也会持有它加载的所有类的引用。
- 只有当加载该类的类加载器本身成为 “无引用状态”（即没有任何地方引用这个类加载器），且该类加载器加载的所有类都满足实例回收条件时，这个类加载器和它加载的类才有可能被回收。
- **核心例外**：启动类加载器（Bootstrap ClassLoader）加载的核心类（如`java.lang.String`、`java.util.ArrayList`）永远不会被卸载，因为启动类加载器是 JVM 内置的，生命周期和 JVM 一致，始终有引用。

#### 3. 无任何直接引用该类的`Class`对象

除了实例和类加载器，还需要确保：

- 没有任何地方直接引用该类的`Class`对象（比如代码中通过`Class.forName()`获取的`Class`对象、反射缓存的`Class`对象、静态变量引用的`Class`对象等）。
- 该类的所有静态变量、静态方法都没有被其他活跃对象引用（静态变量属于类本身，持有静态变量的引用等同于持有类的引用）。

### 类卸载的典型场景

只有满足上述所有条件，且触发 GC 时，类才会被卸载。常见的可卸载场景：

1. **自定义类加载器场景**：比如 Web 容器（Tomcat）的 WebApp 类加载器，当 Web 应用停止时，Tomcat 会销毁该 WebApp 的类加载器，此时该加载器加载的所有类（如业务类）会被卸载。
2. **动态加载 / 卸载场景**：比如通过自定义类加载器动态加载一个类，使用完成后，主动释放对类加载器、类实例、`Class`对象的所有引用，触发 GC 后该类会被卸载。

### 代码示例：验证类卸载（简化版）







```
import java.lang.reflect.Method;

public class ClassUnloadDemo {
    public static void main(String[] args) throws Exception {
        // 自定义类加载器（匿名内部类，避免全局引用）
        ClassLoader customLoader = new ClassLoader() {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                // 只加载自定义的TestClass，其他类交给父类加载
                if ("TestClass".equals(name)) {
                    try {
                        byte[] bytes = readClassBytes("TestClass.class"); // 读取class文件字节（需实现）
                        return defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception e) {
                        throw new ClassNotFoundException(e.getMessage());
                    }
                }
                return super.loadClass(name);
            }

            private byte[] readClassBytes(String fileName) throws Exception {
                // 实现读取class文件为字节数组的逻辑（略）
                return new byte[0];
            }
        };

        // 1. 加载类并创建实例
        Class<?> testClass = customLoader.loadClass("TestClass");
        Object instance = testClass.newInstance();
        System.out.println("类加载完成：" + testClass.getClassLoader());

        // 2. 释放所有引用
        instance = null; // 释放实例引用
        testClass = null; // 释放Class对象引用
        customLoader = null; // 释放类加载器引用

        // 3. 触发GC（仅用于演示，实际GC时机由JVM决定）
        System.gc();
        System.runFinalization();

        // 此时TestClass满足卸载条件，GC后会被卸载
    }
}

// 待加载的测试类（需编译为TestClass.class）
class TestClass {
    // 空类，仅用于演示
}
```

### 总结

Java 类被卸载的核心条件可归纳为 3 点：

1. **实例全回收**：该类的所有对象实例都已被 GC 回收，堆中无该类的实例。
2. **类加载器无引用**：加载该类的类加载器本身无任何活跃引用（启动类加载器加载的类除外）。
3. **无 Class 对象引用**：没有任何地方直接引用该类的`Class`对象（如反射、静态变量等）。

补充关键点：JVM 对类的卸载是被动的（由 GC 触发），且只有自定义类加载器加载的类才有可能被卸载，核心类库的类永远不会被卸载。