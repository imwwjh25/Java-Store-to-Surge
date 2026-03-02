### 12. 对象锁（实例锁）vs 类锁

- 对象锁 ： 锁的是 类的实例对象 ，属于对象级别的锁，不同实例的锁相互独立。

- 类锁 ： 锁的是 类的 Class 对象 (每个类只有一个 Class 对象），属于类级别的锁，所有实例共享这把锁。

本质：都是对象锁（类锁是 Class 对象的锁），只是锁定的对象不同。

### 13. synchronized 实现实例锁和静态锁

#### （1）实例锁（对象锁）：

方式 1：修饰实例方法（锁当前实例对象）：





```
public class Test {
    // 实例锁：锁的是this（当前Test实例）
    public synchronized void instanceMethod() {
        // 临界区
    }
}
```

方式 2：同步代码块（锁指定实例对象）：








```
public class Test {
    private final Object lock = new Object();
    public void method() {
        synchronized (lock) { // 锁自定义实例
            // 临界区
        }
    }
}
```

#### （2）静态锁（类锁）：

方式 1：修饰静态方法（锁 Test.class 对象）：







```
public class Test {
    // 类锁：锁的是Test.class
    public static synchronized void staticMethod() {
        // 临界区
    }
}
```

方式 2：同步代码块（锁 Class 对象）：










```
public class Test {
    public void method() {
        synchronized (Test.class) { // 锁Class对象
            // 临界区
        }
    }
}
```