### 一、AQS 的 state 为什么用 volatile 修饰

AQS（AbstractQueuedSynchronizer）的核心字段 `state` 是用来表示同步状态的（比如锁的持有次数），它被 `volatile` 修饰主要有两个核心原因：

#### 1. 保证可见性

多线程环境下，一个线程对 `state` 的修改，能立刻被其他线程感知到。

- 没有 `volatile` 时，线程会将 `state` 缓存到自己的工作内存中，修改后不会立即同步到主内存，其他线程读取的可能是过期的缓存值，导致同步逻辑出错（比如一个线程释放了锁，但其他线程看不到，还认为锁被持有）。
- `volatile` 强制所有线程直接从主内存读取 `state`，修改后也立即刷回主内存，保证了状态的全局可见性。

#### 2. 配合 CAS 实现原子操作

AQS 对 `state` 的修改（比如加锁 `compareAndSetState`）是通过 CAS（Unsafe 类的 compareAndSwapInt）实现的，而 `volatile` 是 CAS 能正确工作的前提：

- CAS 操作需要读取 `state` 的当前值，对比后再更新，如果 `state` 不是 `volatile`，读取的可能是缓存值，CAS 会基于错误的值进行判断，导致同步逻辑失效。
- 简单来说：`volatile` 保证可见性，CAS 保证原子性，两者结合才能实现 `state` 字段的线程安全修改。

示例代码（AQS 中 state 的定义和 CAS 修改）：

java



运行









```
// AQS 中 state 的核心定义
private volatile int state;

// CAS 修改 state 的核心方法
protected final boolean compareAndSetState(int expect, int update) {
    // Unsafe 类的 CAS 操作，依赖 volatile 保证读取的是最新值
    return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
}
```

### 二、CLH 队列中节点的状态（Node 类的 waitStatus）

AQS 的 CLH 队列（一种基于链表的自旋锁队列）中，每个节点（`Node` 类）都有一个 `waitStatus` 字段，表示节点的等待状态，核心状态有以下 5 种（均为 `Node` 类的静态常量）：

|  状态常量   |  值  |                           含义说明                           |
| :---------: | :--: | :----------------------------------------------------------: |
| `CANCELLED` |  1   |       节点已取消（比如线程超时、中断，不再参与锁竞争）       |
|  `SIGNAL`   |  -1  | 节点的后继节点正在等待被唤醒，当前节点释放锁 / 取消时需要唤醒后继节点 |
| `CONDITION` |  -2  |   节点处于条件队列中（等待 Condition 唤醒），不在同步队列    |
| `PROPAGATE` |  -3  | 仅用于共享模式，标识后续的共享锁获取可以无条件传播（比如 CountDownLatch） |
|     `0`     |  0   |        初始状态，节点刚加入同步队列，未被设置任何状态        |

#### 关键状态补充说明：

1. **SIGNAL（-1）**：最常用的状态。当一个节点加入队列时，会将前驱节点的状态设为 SIGNAL，这样前驱节点释放锁时，就知道需要唤醒当前节点。
2. **CANCELLED（1）**：节点一旦进入此状态，就不会再改变。遍历队列时会跳过这类节点，清理出队列。
3. **CONDITION（-2）**：只有调用 `Condition.await()` 时，节点才会进入此状态，调用 `Condition.signal()` 时，节点会从条件队列转移到同步队列，状态改为 0。

### 总结

1. `state` 用 `volatile` 修饰的核心：保证多线程间的**可见性**，同时为 CAS 原子操作提供正确的内存语义，确保同步状态的线程安全修改。
2. CLH 队列节点的核心状态：`CANCELLED(1)`、`SIGNAL(-1)`、`CONDITION(-2)`、`PROPAGATE(-3)`、初始状态 `0`，其中 `SIGNAL` 是同步队列中最核心的状态，用于唤醒后继节点。
