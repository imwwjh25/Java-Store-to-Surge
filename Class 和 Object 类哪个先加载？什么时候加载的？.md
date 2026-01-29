## 一、核心结论：Object 类先加载，依赖关系决定加载顺序



JVM 加载类时遵循 **“父类优先于子类加载”** 的规则，而 `Class` 类与 `Object` 类存在明确的继承关系：**`java.lang.Class` 是 `java.lang.Object` 的直接子类**（可通过 `Class.class.getSuperclass()` 验证，返回结果为 `class java.lang.Object`）。

根据 “父类优先加载” 原则：

1. 加载 `Class` 类前，必须先加载其直接父类 `Object` 类；
2. `Object` 类是 Java 继承体系的 “根类”（无父类），可独立加载，无需依赖其他类。

因此，**`Object` 类加载顺序早于 `Class` 类**。

## 二、加载时机：JVM 启动阶段（初始化核心类库）



`Object` 类和 `Class` 类均属于 **Java 核心类库（`rt.jar`）** 的类，加载时机是 **JVM 启动过程中的 “类初始化阶段”**，早于用户代码（如 `main` 方法）的执行，具体流程如下：

1. **JVM 启动第一步：初始化 Bootstrap ClassLoader（启动类加载器）**JVM 启动时，首先初始化最顶层的 **启动类加载器**（由 C++ 实现），其职责是加载 `JAVA_HOME/lib` 目录下的核心类库（如 `rt.jar`、`charsets.jar`），`Object` 类和 `Class` 类均存储在 `rt.jar` 中。

2. 加载 Object 类

   启动类加载器优先加载

    

   ```
   Object
   ```

    

   类：

   - `Object` 是所有类的父类，是 Java 类型体系的基础，必须最早加载；
   - 加载过程包括 “加载（读取 `Object.class` 字节码）→ 验证（校验字节码合法性）→ 准备（分配静态变量内存）→ 解析（符号引用转直接引用）→ 初始化（执行静态代码块、赋值静态变量）”，最终在方法区生成 `Object` 类的 `Class` 对象。

3. 加载 Class 类

   ```
   Object
   ```

    

   类加载完成后，启动类加载器加载

    

   ```
   Class
   ```

    

   类：

   - `Class` 类的定义依赖 `Object` 类（继承自 `Object`），需确认父类已加载；
   - 加载完成后，方法区生成 `Class` 类的 `Class` 对象（即 `Class.class`），后续所有类的加载（包括用户自定义类）都需通过 `Class` 类的实例来描述（每个类对应一个 `Class` 对象）。

4. **加载其他核心类，准备执行 main 方法**加载完 `Object`、`Class` 等核心类后，JVM 继续加载其他核心类库（如 `java.lang.String`、`java.lang.Thread`），最终初始化用户指定的主类（含 `main` 方法的类），执行 `main` 方法。

## 三、验证：通过代码和 JVM 机制确认加载顺序



### 1. 代码验证：Class 类的父类是 Object 类



通过 `Class` 类的 `getSuperclass()` 方法，可直接验证两者的继承关系：

java

运行

```
public class ClassLoadOrder {
    public static void main(String[] args) {
        // 获取 Class 类的父类
        Class<?> superClassOfClass = Class.class.getSuperclass();
        // 获取 Object 类的父类（Object 无父类，返回 null）
        Class<?> superClassOfObject = Object.class.getSuperclass();
        
        System.out.println("Class 类的父类：" + superClassOfClass); // 输出：class java.lang.Object
        System.out.println("Object 类的父类：" + superClassOfObject); // 输出：null
    }
}
```



结果证明 `Class` 是 `Object` 的子类，根据 “父类优先加载”，`Object` 必然先加载。

### 2. JVM 机制验证：启动类加载器的加载顺序



JVM 启动时，启动类加载器加载 `rt.jar` 的顺序是 “按类的依赖关系排序”，核心类库的加载清单中，`Object.class` 的加载优先级高于 `Class.class`。可通过以下方式间接观察：

- 使用 `jinfo -flags <pid>` 查看 JVM 启动参数，确认 `rt.jar` 是启动类加载器的默认加载路径；
- 使用 `jmap -histo:live <pid>` 查看 JVM 内存中的类实例，`Object` 类的 `Class` 对象（`java.lang.Object`）会早于 `Class` 类的 `Class` 对象（`java.lang.Class`）出现。

## 四、关键误区：“Class 类是描述所有类的类，所以先加载”



很多人误以为 “`Class` 类负责描述所有类，必须先加载才能加载其他类”，这是错误的。核心原因是：

- `Class` 类本身也是一个 “类”，其定义依赖 `Object` 类（继承关系），必须遵循 “父类优先加载” 的规则；
- JVM 加载 `Object` 类时，会临时生成 `Object` 类的 `Class` 对象（用于描述 `Object` 类），但此时 `Class` 类尚未完全加载 ——JVM 内部有特殊机制支持 “加载父类时临时生成其 `Class` 对象”，无需等待 `Class` 类加载完成。

## 五、总结



1. **加载顺序**：`Object` 类先加载，`Class` 类后加载，核心原因是 `Class` 继承自 `Object`，父类必须优先于子类加载；
2. **加载时机**：两者均在 **JVM 启动阶段**（执行 `main` 方法前）由启动类加载器加载，属于核心类库的初始化步骤；
3. **核心逻辑**：Java 类加载遵循 “依赖关系优先”，根类 `Object` 无依赖可独立加载，子类 `Class` 需等待父类加载完成后再加载。

简单说：`Object` 是 “根”，`Class` 是 “根的孩子”，孩子必须等根长出来才能存在，因此 `Object` 先加载。
