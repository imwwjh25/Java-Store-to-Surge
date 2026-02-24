### 一、Java 异常处理机制

Java 异常处理机制的核心是通过**异常类型体系**、**捕获与抛出机制**实现 “错误识别 - 定位 - 处理” 的闭环，核心目标是增强程序健壮性。

#### 1. 异常体系核心结构

异常根类为`Throwable`，下设两大子类：

- **Error**：表示 JVM 层面的严重错误（如内存溢出），程序通常无法恢复，无需捕获。

- Exception

  ：表示程序可处理的异常，分为：

    - 受检异常（Checked Exception）：编译期强制要求捕获（如`IOException`）。
    - 非受检异常（Unchecked Exception）：运行时抛出，编译期不强制捕获（如`NullPointerException`、`ArrayIndexOutOfBoundsException`）。

#### 2. 核心处理关键字

- `try`：包裹可能抛出异常的代码块。
- `catch`：捕获指定类型的异常并处理，可多个`catch`按 “子类到父类” 顺序排列。
- `finally`：无论是否发生异常，均会执行（常用于资源释放，如关闭流、连接）。
- `throw`：手动抛出单个异常对象（如`throw new IllegalArgumentException("参数非法")`）。
- `throws`：在方法签名声明可能抛出的异常，由调用方处理。

### 二、Error 可以被捕获吗

**可以被捕获，但不推荐这样做**。

#### 1. 技术可行性

`Error`作为`Throwable`的子类，语法上可通过`try-catch`捕获，示例如下：






```java
try {
    // 触发StackOverflowError（递归调用无终止条件）
    recursiveCall();
} catch (StackOverflowError e) {
    System.out.println("捕获到Error：" + e.getMessage());
}

public static void recursiveCall() {
    recursiveCall();
}
```

上述代码可执行并打印捕获信息，证明`Error`能被捕获。

#### 2. 不推荐捕获的原因

- **不可恢复性**：`Error`多为 JVM 内部错误（如`OutOfMemoryError`、`VirtualMachineError`），发生时 JVM 已处于不稳定状态，即使捕获也无法修复底层问题，程序难以正常运行。
- **违背设计初衷**：Java 设计中`Error`与`Exception`分工明确 ——`Error`标识致命错误，需通过优化代码（如避免内存泄漏）或调整环境（如加大 JVM 内存）解决，而非程序内捕获处理。
