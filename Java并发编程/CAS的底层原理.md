### 一、先明确核心目标

这段汇编的功能等价于：


```c
// 原子操作：如果 dest 地址的值 == compare_value，则将其改为 exchange_value
// 返回值：dest 地址原来的值（通过 exchange_value 带出）
int cmpxchg(int exchange_value, int* dest, int compare_value) {
    atomic {
        old_value = *dest;
        if (old_value == compare_value) {
            *dest = exchange_value;
        }
        return old_value;
    }
}
```

Java 中 `compareAndSwapInt` 会根据这个返回值判断是否交换成功（若返回值 == expected，则成功）。

### 二、逐部分拆解代码

代码结构：`__asm__ volatile (汇编指令 : 输出操作数 : 输入操作数 : 破坏描述符);`


```cpp
__asm__ volatile (
    LOCK_PREFIX "cmpxchgl %1,(%3)"  // 核心汇编指令
    : "=a" (exchange_value)         // 输出操作数（结果存储到哪里）
    : "r" (exchange_value),         // 输入操作数1：要交换的值
      "a" (compare_value),          // 输入操作数2：预期的值
      "r" (dest)                    // 输入操作数3：目标内存地址
    : "cc", "memory"                // 破坏描述符（告诉编译器执行后哪些状态变了）
);
```

#### 1. 关键字与宏定义

- `__asm__`：GCC 扩展关键字，用于嵌入内联汇编（也可简写为 `asm`）；
- `volatile`：告诉编译器「不要优化这段汇编代码」—— 禁止编译器重排指令顺序、或把汇编代码删除（并发场景下指令重排会导致逻辑错误）；
- `LOCK_PREFIX`：宏定义（来自 JVM 源码），等价于 x86 汇编的 `lock` 前缀 ——**这是原子性的核心保证**（后面详细说）。

#### 2. 核心汇编指令：`cmpxchgl %1,(%3)`

- ```
  cmpxchgl
  ```

  ：x86 架构的「比较并交换」指令，后缀

   

  ```
  l
  ```

   

  表示「long（4 字节）」，对应 C 语言的

   

  ```
  int
  ```

  、Java 的

   

  ```
  int
  ```

  ；

  - 指令逻辑：

    ```
    cmpxchg 源操作数, 目标操作数
    ```

     

    → 比较「EAX 寄存器的值」与「目标操作数的值」：

    1. 若相等：将「源操作数」写入「目标操作数」，并设置 CPU 标志位 `ZF=1`（零标志位）；
    2. 若不相等：将「目标操作数」的值写入「EAX 寄存器」，并设置 `ZF=0`；

- `%1`：内联汇编的「操作数占位符」，对应后面输入操作数列表的第 1 个（从 0 开始计数）—— 即 `exchange_value`（要交换的值）；

- `(%3)`：`%3` 是输入操作数列表的第 3 个（`dest`，目标内存地址），括号 `()` 表示「间接寻址」—— 即「`dest` 指针指向的内存地址中的值」。

综上，`cmpxchgl %1,(%3)` 的意思是：「比较 EAX 寄存器的值（存的是 `compare_value`）与 `dest` 地址的值，若相等则将 `exchange_value` 写入 `dest` 地址，否则将 `dest` 地址的值读入 EAX」。

#### 3. 输入操作数（`: "r" (exchange_value), "a" (compare_value), "r" (dest)`）

输入操作数的格式：`"约束符" (C语言变量)` → 告诉编译器「将 C 变量放到哪里（寄存器 / 内存）」，供汇编指令使用。

- `"r" (exchange_value)`：`"r"` 约束符表示「将变量放到任意一个通用寄存器（如 EBX、ECX、EDX 等）」，这里 `exchange_value`（要交换的值）会被存入一个寄存器，汇编中用 `%1` 引用；
- `"a" (compare_value)`：`"a"` 约束符是「将变量放到 EAX 寄存器」—— 这是 `cmpxchgl` 指令的要求（必须用 EAX 存预期值 `compare_value`），汇编中无需显式引用（`cmpxchgl` 会自动读取 EAX）；
- `"r" (dest)`：`dest` 是目标内存地址（int* 类型），被放到另一个通用寄存器，汇编中用 `%3` 引用。

补充：操作数占位符的编号规则：从输出操作数开始计数，输出数在前，输入数在后。这里输出操作数是 `%0`，输入操作数依次是 `%1`（exchange_value）、`%2`（compare_value）、`%3`（dest）—— 但 `%2` 因用了 `"a"` 约束（EAX），汇编指令中无需显式使用。

#### 4. 输出操作数（`: "=a" (exchange_value)`）

输出操作数的格式：`"=约束符" (C语言变量)` → 告诉编译器「汇编指令的结果要存入哪个 C 变量」，`=` 表示「这是一个输出操作数（只写）」。

- `"=a" (exchange_value)`：`"=a"` 表示「将 EAX 寄存器的值写入 `exchange_value` 变量」；
- 结合 `cmpxchgl` 指令：EAX 最终存储的是「`dest` 地址原来的值」（无论交换是否成功）—— 这就是为什么返回 `exchange_value` 能拿到原始值，Java 层据此判断 CAS 是否成功（原始值 == 预期值则成功）。

#### 5. 破坏描述符（`: "cc", "memory"`）

破坏描述符告诉编译器：「执行这段汇编后，哪些 CPU 状态或内存会被修改」，让编译器优化时避免出错。

- `"cc"`：表示「汇编指令会修改 CPU 的条件码寄存器（Flags Register）」——`cmpxchgl` 会设置 `ZF`（零标志位）、`CF`（进位标志位）等，编译器需要知道这一点，避免后续指令依赖旧的条件码；
- `"memory"`：表示「汇编指令会修改内存中的数据」—— 告诉编译器「不要把内存数据缓存到寄存器中」（避免寄存器与内存不一致），且「禁止指令重排」（保证汇编前后的内存操作顺序）。这对并发场景至关重要（比如避免 CAS 操作被重排到其他内存操作之前）。

### 三、原子性的核心保证：`LOCK_PREFIX`（lock 前缀）

`LOCK_PREFIX` 是 JVM 定义的宏，在 x86 架构下等价于汇编的 `lock` 前缀，作用是**保证指令的原子性**—— 这是 CAS 能用于并发同步的关键。

#### `lock` 前缀的底层原理（x86 架构）：

1. **总线锁**（早期 CPU）：执行指令时，CPU 会向系统总线发送 `LOCK#` 信号，阻塞其他 CPU 对内存的访问，直到当前指令执行完毕 —— 保证同一时间只有一个 CPU 能操作目标内存地址；
2. **缓存锁**（现代 CPU）：若目标内存地址的数据已在当前 CPU 的缓存中（L1/L2/L3），则不会锁总线，而是通过「缓存一致性协议（MESI）」保证原子性 —— 其他 CPU 若要修改该缓存行，会被阻塞，直到当前 CPU 执行完指令并释放缓存锁。

两种方式的核心目的：**防止多 CPU 核心同时操作同一个内存地址，保证 `cmpxchgl` 指令的原子性**。

### 四、完整流程梳理（结合 C++ 函数）

再看 JVM 中的 `Atomic::cmpxchg` 函数，结合汇编流程更清晰：


```cpp
inline jint Atomic::cmpxchg(jint exchange_value, jint* dest, jint compare_value) {
    __asm__ volatile (
        LOCK_PREFIX "cmpxchgl %1,(%3)"
        : "=a" (exchange_value)
        : "r" (exchange_value), "a" (compare_value), "r" (dest)
        : "cc", "memory");
    return exchange_value;
}
```

执行流程：

1. 传入参数：`exchange_value`（要写入的值）、`dest`（目标地址）、`compare_value`（预期值）；

2. 编译器将 `compare_value` 存入 EAX，`exchange_value` 存入某个通用寄存器（如 EBX），`dest` 存入另一个通用寄存器（如 ECX）；

3. 执行

    

   ```
   lock cmpxchgl %ebx, (%ecx)
   ```

   ：

   - 比较 EAX（compare_value）与 `dest` 地址的值；
   - 若相等：将 EBX（exchange_value）写入 `dest` 地址，EAX 保持不变（仍为 compare_value）；
   - 若不相等：将 `dest` 地址的值读入 EAX；

4. 汇编执行完，EAX 的值写入 `exchange_value` 变量，函数返回该值；

5. Java 层判断返回值是否等于预期值，若等于则 CAS 成功，否则失败。

### 五、面试关键点总结

1. 核心功能：x86 架构下的 4 字节原子 CAS 操作，是 `Unsafe.compareAndSwapInt` 的底层实现；
2. 关键指令：`cmpxchgl` 负责「比较并交换」，`lock` 前缀保证「原子性」；
3. 内联汇编语法：输出操作数（`"=a"`）将结果存入 C 变量，输入操作数（`"a"`/`"r"`）指定变量存储位置，破坏描述符（`"cc"`/`"memory"`）保证编译器优化正确性；
4. 核心意义：无需加锁（mutex）即可实现并发同步，减少线程切换开销，是无锁编程（如 AQS、ConcurrentHashMap）的基础。
