### 一、Java 中的 4 种核心访问修饰符

Java 提供了 4 种访问修饰符（按访问范围从宽到窄排序），用于控制类、方法、变量的可见性，核心作用是**封装代码、控制访问权限、提高代码安全性和可维护性**。





|     修饰符     | 同一类内 | 同一包内 | 不同包子类 | 不同包非子类 |                           核心作用                           |
| :------------: | :------: | :------: | :--------: | :----------: | :----------------------------------------------------------: |
|    `public`    |    ✅     |    ✅     |     ✅      |      ✅       | 完全公开，任何地方都能访问，常用于对外提供的接口、工具类方法等 |
|  `protected`   |    ✅     |    ✅     |     ✅      |      ❌       | 保护子类访问，兼顾包内复用和子类继承，常用于需要被子类重写的方法 / 变量 |
| 默认（无修饰） |    ✅     |    ✅     |     ❌      |      ❌       | 包级私有，仅同一包内可见，用于包内组件间的协作，对外隐藏实现 |
|   `private`    |    ✅     |    ❌     |     ❌      |      ❌       | 仅当前类可见，完全封装，常用于类内部的私有变量、工具方法，禁止外部直接访问 |

### 二、通俗解释与代码示例

#### 1. private（私有）

**作用**：把内容 “藏” 在类内部，外部完全访问不到，只能通过类提供的 `get/set` 方法间接访问，避免外部随意修改。








```
public class Person {
    // 私有变量，仅 Person 类内部可直接访问
    private String name;
    
    // 对外提供访问私有变量的方法（封装）
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
```

外部类无法直接 `person.name = "张三"`，必须通过 `person.setName("张三")`，可以在方法中加校验（比如名字非空），保证数据安全。

#### 2. 默认（无修饰）

**作用**：仅限同一包内的类协作使用，不同包的类（哪怕是子类）都访问不到，适合包内复用的工具类 / 变量。




```
// 包：com.example.utils
class Tool { // 无修饰符，包级私有
    void print() {
        System.out.println("包内可见");
    }
}

// 同包内的类可以访问
package com.example.utils;
public class Test {
    public static void main(String[] args) {
        Tool tool = new Tool();
        tool.print(); // 正常执行
    }
}

// 不同包的类无法访问
package com.example.test;
import com.example.utils.Tool;
public class Test2 {
    public static void main(String[] args) {
        Tool tool = new Tool(); // 编译报错：Tool 不是 public 的
    }
}
```

#### 3. protected（受保护）

**作用**：允许子类访问父类的成员，同时保留包内访问的权限，是 “继承” 场景下的核心修饰符。







```
// 包：com.example.parent
public class Parent {
    protected String msg = "子类可访问";
}

// 同包子类（可访问）
package com.example.parent;
public class Son1 extends Parent {
    public void show() {
        System.out.println(msg); // 正常执行
    }
}

// 不同包子类（可访问）
package com.example.child;
import com.example.parent.Parent;
public class Son2 extends Parent {
    public void show() {
        System.out.println(msg); // 正常执行
    }
}

// 不同包非子类（不可访问）
package com.example.child;
import com.example.parent.Parent;
public class Test {
    public static void main(String[] args) {
        Parent p = new Parent();
        System.out.println(p.msg); // 编译报错：msg 是 protected 的
    }
}
```

#### 4. public（公开）

**作用**：完全开放，任何地方都能访问，常用于对外提供的核心类、方法（比如 `java.lang.String` 类就是 public 的）。







```
// 包：com.example.publicdemo
public class PublicClass {
    public String info = "公开信息";
    
    public void showInfo() {
        System.out.println(info);
    }
}

// 任意包的类都能访问
package com.example.other;
import com.example.publicdemo.PublicClass;
public class Test {
    public static void main(String[] args) {
        PublicClass pc = new PublicClass();
        System.out.println(pc.info); // 正常输出
        pc.showInfo(); // 正常执行
    }
}
```

### 三、补充说明

1. 访问修饰符的适用范围：

    - `public`/ 默认：可修饰类（外部类）、方法、变量；
    - `private`/`protected`：**不能修饰外部类**，只能修饰类内的方法、变量、内部类。



2. 核心设计思想：遵循 “最小权限原则”—— 能使用更窄的访问范围，就不用更宽的（比如能用 `private` 就不用 `public`），减少代码耦合。

### 总结

1. Java 访问修饰符的核心是**控制可见性**，4 种修饰符的访问范围从宽到窄：`public` > `protected` > 默认 > `private`；
2. `private` 用于完全封装，`protected` 用于子类继承，默认用于包内协作，`public` 用于对外暴露接口；
3. 实际开发中优先遵循 “最小权限原则”，既能保证代码安全，也能降低维护成本。