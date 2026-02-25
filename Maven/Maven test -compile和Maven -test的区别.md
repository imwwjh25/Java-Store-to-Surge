### 先准备基础代码

假设你有一个简单的业务类和对应的测试类：

#### 1. 业务代码（src/main/java/com/demo/Calculator.java）




```
package com.demo;

// 一个简单的计算器类，提供加法功能
public class Calculator {
    public int add(int a, int b) {
        return a + b; // 核心逻辑：两数相加
    }
}
```

#### 2. 测试代码（src/test/java/com/demo/CalculatorTest.java）










```
package com.demo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

// 测试Calculator的加法功能
public class CalculatorTest {
    
    // 测试用例：验证1+2是否等于3
    @Test
    public void testAdd() {
        Calculator calculator = new Calculator();
        // 断言：预期结果是3，实际是calculator.add(1,2)的返回值
        assertEquals(3, calculator.add(1, 2));
    }
}
```

### 示例 1：仅编译测试源码（执行 `mvn test-compile`）

#### 操作步骤

打开终端，进入项目根目录（包含 pom.xml 的目录），执行命令：











```
mvn test-compile
```

#### 执行结果（核心输出）






```
[INFO] --- maven-compiler-plugin:3.11.0:testCompile (default-testCompile) @ demo ---
[INFO] Changes detected - recompiling the module! :source
[INFO] Compiling 1 source file to /demo/target/test-classes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  1.234 s
```

#### 关键解读

1. 这个命令只做了两件事：

    - 检查`CalculatorTest.java`的语法：比如有没有少分号、import 的 Junit 包是否存在、类名 / 方法名是否合法；
    - 把`CalculatorTest.java`编译成字节码文件（`CalculatorTest.class`），存到`target/test-classes`目录下。



2. **完全没运行`testAdd()`方法**：你看不到任何关于 “1+2 是否等于 3” 的验证结果，只知道测试代码能被编译（语法 / 依赖没问题）。

3. 哪怕你故意把测试代码改出

   语法错误 （比如少写一个分号）：










   ```
   // 错误的测试代码（少分号）
   @Test
   public void testAdd() {
       Calculator calculator = new Calculator() // 这里少了分号
       assertEquals(3, calculator.add(1, 2));
   }
   ```



再执行```mvn test-compile```，会直接报错：







   ```
   [ERROR] /demo/src/test/java/com/demo/CalculatorTest.java:[14,40] 缺少分号
   [INFO] ------------------------------------------------------------------------
   [INFO] BUILD FAILURE
   ```



这就是编译的核心作用： 静态检查代码语法 / 依赖，不运行任何逻辑 。

### 示例 2：执行测试用例（执行 `mvn test`）

#### 操作步骤

同样在终端执行：










```
mvn test
```

#### 执行结果（核心输出）








```
[INFO] --- maven-compiler-plugin:3.11.0:testCompile (default-testCompile) @ demo ---
[INFO] Compiling 1 source file to /demo/target/test-classes  # 第一步：先编译测试源码（和test-compile一样）
[INFO] --- surefire-plugin:3.2.2:test (default-test) @ demo ---
[INFO] Running com.demo.CalculatorTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.056 s -- in com.demo.CalculatorTest
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

#### 关键解读

1. 这个命令分两步走：

    - 第一步：自动先执行`test-compile`（编译测试源码），确保测试代码能编译通过；
    - 第二步：真正运行`CalculatorTest`中的`testAdd()`方法，执行`calculator.add(1,2)`并检查结果是否等于 3。



2. 能看到**测试执行结果**：`Tests run: 1, Failures: 0`（运行了 1 个用例，0 个失败），这是`test-compile`完全没有的信息。

3. 如果你故意把测试逻辑改错（比如预期结果写成 4）：








   ```
   @Test
   public void testAdd() {
       Calculator calculator = new Calculator();
       assertEquals(4, calculator.add(1, 2)); // 预期结果错写成4
   }
   ```
执行```mvn test```会报错，且错误信息是 测试执行失败 ，而非编译失败：










```
   [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
   [ERROR] Failures: 
   [ERROR]   CalculatorTest.testAdd:15 expected: <4> but was: <3>
```



这个错误是 “运行测试逻辑后发现结果不符合预期”，和编译时的语法错误完全不是一回事。

### 两个操作的直观对比表（基于上面的例子）




|         操作          |          `mvn test-compile`          |               `mvn test`                |
| :-------------------: | :----------------------------------: | :-------------------------------------: |
|  是否编译测试源码？   |            是（核心动作）            |             是（前提动作）              |
| 是否运行`testAdd()`？ |        否（完全不碰这个方法）        |    是（真正执行加法计算并验证结果）     |
|     输出核心信息      |         仅 “编译成功 / 失败”         | 编译状态 + “1 个用例，0 失败 / 1 失败”  |
|     能发现的问题      | 测试代码语法错误（少分号）、依赖缺失 | 除了编译问题，还能发现逻辑错误（1+2≠4） |
|         耗时          |               1 秒左右               |     2 秒左右（多了运行测试的时间）      |

### 总结

1. **仅编译测试源码**：就像老师只看你的作业格式对不对（有没有抄错字、漏标点），但完全不看作业答案对不对；
2. **执行测试用例**：老师先检查作业格式（编译），再批改答案（运行测试），告诉你哪道题对、哪道题错。

回到例子里：

- `test-compile`只确认`CalculatorTest.java`能被翻译成字节码，不管`1+2`算得对不对；
- `test`不仅确认编译没问题，还会真的算`1+2`，并检查结果是不是你预期的 3。