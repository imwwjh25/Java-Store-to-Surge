### 1. **可重入性的本质一致性**

两者的核心可重入特性是一致的：



- 同一线程在持有锁的情况下，可以再次获取该锁而不会被阻塞。
- 锁内部会记录「持有线程」和「重入次数」，每次重入时次数加 1，释放时减 1，直到次数为 0 时真正释放锁。

### 2. **可重入性的实现差异**

| 特性                     | `synchronized`                                          | `ReentrantLock`                                              |
| ------------------------ | ------------------------------------------------------- | ------------------------------------------------------------ |
| **实现层面**             | JVM 内置实现（字节码指令 `monitorenter`/`monitorexit`） | 基于 AQS（AbstractQueuedSynchronizer）的 Java 代码实现       |
| **重入次数记录**         | 由 JVM 维护（隐式记录在对象头的 Mark Word 中）          | 由 AQS 的 `state` 变量显式记录（每次重入 `state+1`，释放 `state-1`） |
| **异常处理对重入的影响** | 若发生异常，JVM 会自动释放锁（保证 `monitorexit` 执行） | 若发生异常，需手动在 `finally` 中调用 `unlock()`，否则可能导致锁无法释放 |

### 3. **可重入性的使用差异**

- **`synchronized`**：

    - 重入是隐式的，无需手动操作，由 JVM 自动管理重入次数。

    - 示例：


    

```java
    public synchronized void methodA() {
        methodB(); // 同一线程可重入
    }
    
    public synchronized void methodB() {
        // 执行逻辑
    }
```

- **`ReentrantLock`**：

    - 重入需要显式调用 `lock()` 和 `unlock()`，且重入次数必须与释放次数一致（否则会导致其他线程无法获取锁）。

    - 示例：

    
```java
    private final ReentrantLock lock = new ReentrantLock();
    
    public void methodA() {
        lock.lock();
        try {
            methodB(); // 同一线程可重入
        } finally {
            lock.unlock(); // 释放一次
        }
    }
    
    public void methodB() {
        lock.lock();
        try {
            // 执行逻辑
        } finally {
            lock.unlock(); // 释放一次（与重入次数匹配）
        }
    }
```

### 总结

- **核心一致性**：`synchronized` 和 `ReentrantLock` 均支持可重入性，允许同一线程多次获取锁。
- **差异点**：实现层面（JVM 内置 vs Java 代码）、重入次数的管理方式（隐式 vs 显式）、异常处理对锁释放的影响（自动 vs 手动）。



实际使用中，`synchronized` 更简洁（无需手动释放），而 `ReentrantLock` 更灵活（可中断、超时获取等），但需注意手动释放锁的次数与重入次数保持一致。
