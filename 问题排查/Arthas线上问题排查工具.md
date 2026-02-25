# Arthas 常用命令速查手册

## 目录

- [1. 系统信息类命令](#1-系统信息类命令)
- [2. 类加载相关命令](#2-类加载相关命令)
- [3. 方法监控类命令](#3-方法监控类命令)
- [4. 内存相关命令](#4-内存相关命令)
- [5. 类加载器相关命令](#5-类加载器相关命令)
- [6. 其他常用命令](#6-其他常用命令)
- [7. 高级用法](#7-高级用法)
- [8. 常用场景命令组合](#8-常用场景命令组合)
- [9. 注意事项](#9-注意事项)

---

## 1. 系统信息类命令

### 1.1 dashboard - 实时监控面板

**用途**: 查看系统的实时数据面板，包括线程、内存、GC 等信息

**语法**:
```bash
dashboard
```

**示例**:
```bash
# 查看实时监控面板
dashboard

# 每隔 5 秒刷新一次
dashboard -i 5000
```

**输出说明**:
- `ID`: 线程 ID
- `NAME`: 线程名称
- `GROUP`: 线程组
- `PRIORITY`: 线程优先级
- `STATE`: 线程状态
- `CPU%`: CPU 使用率
- `TIME`: 线程运行时间
- `INTERRUPTED`: 是否被中断
- `DAEMON`: 是否为守护线程

---

### 1.2 thread - 线程相关命令

**用途**: 查看线程堆栈、线程状态、查找死锁等

**语法**:
```bash
thread [thread-id] [options]
```

**示例**:
```bash
# 查看所有线程
thread

# 查看指定线程的堆栈
thread 1

# 查看最忙的 3 个线程
thread -n 3

# 查找死锁
thread -b

# 查看处于 BLOCKED 状态的线程
thread -b -n 5

# 查看线程的 CPU 使用情况
thread -n 5 -i 1000
```

**线程状态说明**:
- `RUNNABLE`: 正在运行
- `WAITING`: 等待中
- `TIMED_WAITING`: 定时等待
- `BLOCKED`: 阻塞
- `NEW`: 新建
- `TERMINATED`: 终止

---

### 1.3 sysprop - 查看系统属性

**用途**: 查看和修改 JVM 的系统属性

**语法**:
```bash
sysprop [property-name] [property-value]
```

**示例**:
```bash
# 查看所有系统属性
sysprop

# 查看指定属性
sysprop java.version

# 修改系统属性
sysprop user.timezone GMT+8

# 查看多个属性
sysprop java.version java.home
```

**常用属性**:
- `java.version`: Java 版本
- `java.home`: Java 安装目录
- `user.dir`: 当前工作目录
- `user.timezone`: 时区
- `file.encoding`: 文件编码

---

### 1.4 sysenv - 查看环境变量

**用途**: 查看操作系统的环境变量

**语法**:
```bash
sysenv [env-name]
```

**示例**:
```bash
# 查看所有环境变量
sysenv

# 查看指定环境变量
sysenv JAVA_HOME

# 查看多个环境变量
sysenv JAVA_HOME PATH
```

---

## 2. 类加载相关命令

### 2.1 sc - 查看类信息

**用途**: 查看已加载的类的信息

**语法**:
```bash
sc [-d] [class-name-pattern]
```

**示例**:
```bash
# 查看所有已加载的类
sc

# 查看指定类
sc com.example.demo.controller.MemoryLeakController

# 查看类的详细信息
sc -d com.example.demo.controller.MemoryLeakController

# 查看类的类加载器
sc -d com.example.demo.controller.MemoryLeakController | grep classLoader

# 模糊匹配类名
sc *Controller

# 查看类的继承关系
sc -d com.example.demo.controller.MemoryLeakController | grep super
```

**输出字段说明**:
- `class-name`: 类名
- `class-loader`: 类加载器
- `code-source`: 类的来源
- `isNode`: 是否为节点
- `isInterface`: 是否为接口
- `isAnnotation`: 是否为注解
- `isEnum`: 是否为枚举
- `isAnonymousClass`: 是否为匿名类
- `isArray`: 是否为数组
- `isLocalClass`: 是否为局部类
- `isMemberClass`: 是否为成员类
- `isPrimitive`: 是否为基本类型
- `isSynthetic`: 是否为合成类
- `simple-name`: 简单类名
- `modifier`: 修饰符
- `interfaces`: 实现的接口
- `super-class`: 父类
- `class-loaders`: 类加载器

---

### 2.2 sm - 查看类的方法信息

**用途**: 查看已加载的类的方法信息

**语法**:
```bash
sm [-d] [class-name] [method-name]
```

**示例**:
```bash
# 查看指定类的所有方法
sm com.example.demo.controller.MemoryLeakController

# 查看指定方法的详细信息
sm -d com.example.demo.controller.MemoryLeakController leakMemory

# 查看方法的参数和返回值类型
sm -d com.example.demo.controller.MemoryLeakController leakMemory | grep descriptor

# 模糊匹配方法名
sm com.example.demo.controller.MemoryLeakController *Memory

# 查看方法的声明类
sm -d com.example.demo.controller.MemoryLeakController leakMemory | grep declaring-class
```

**输出字段说明**:
- `declaring-class`: 声明类
- `method-name`: 方法名
- `modifier`: 修饰符
- `parameters`: 参数列表
- `return-type`: 返回类型
- `exceptions`: 异常列表
- `annotations`: 注解列表

---

### 2.3 jad - 反编译类

**用途**: 反编译已加载的类，查看实际运行的代码

**语法**:
```bash
jad [class-name] [method-name]
```

**示例**:
```bash
# 反编译整个类
jad com.example.demo.controller.MemoryLeakController

# 反编译指定方法
jad com.example.demo.controller.MemoryLeakController leakMemory

# 反编译并保存到文件
jad com.example.demo.controller.MemoryLeakController > /tmp/MemoryLeakController.java

# 反编译并指定输出格式
jad --source-only com.example.demo.controller.MemoryLeakController

# 反编译并显示行号
jad --lineNumber com.example.demo.controller.MemoryLeakController
```

**使用场景**:
- 查看实际运行的代码（可能被 AOP 修改）
- 排查代码版本问题
- 分析字节码增强后的代码

---

### 2.4 mc - 内存编译器

**用途**: 在内存中编译 Java 文件

**语法**:
```bash
mc /path/to/Class.java
```

**示例**:
```bash
# 编译 Java 文件
mc /tmp/MemoryLeakController.java

# 编译并指定类路径
mc -c 327a647b /tmp/MemoryLeakController.java

# 编译并指定输出目录
mc -d /tmp/classes /tmp/MemoryLeakController.java

# 编译多个文件
mc /tmp/*.java
```

---

### 2.5 redefine - 热更新类

**用途**: 重新定义已加载的类（热更新）

**语法**:
```bash
redefine /path/to/Class.class
```

**示例**:
```bash
# 热更新类
redefine /tmp/com/example/demo/controller/MemoryLeakController.class

# 热更新多个类
redefine /tmp/Class1.class /tmp/Class2.class

# 查看热更新历史
redefine -d
```

**注意事项**:
- 只能修改方法体，不能修改方法签名
- 不能添加或删除字段
- 不能修改类继承关系
- 热更新可能影响应用稳定性

---

## 3. 方法监控类命令

### 3.1 watch - 观察方法调用

**用途**: 观察方法的入参、返回值、异常等

**语法**:
```bash
watch [class-name] [method-name] [expression] [condition] [options]
```

**示例**:
```bash
# 观察方法的入参和返回值
watch com.example.demo.controller.MemoryLeakController leakMemory '{params, returnObj}'

# 观察方法执行时的异常
watch com.example.demo.controller.MemoryLeakController leakMemory '{params, throwExp}' -e

# 观察方法执行时间
watch com.example.demo.controller.MemoryLeakController leakMemory '{params, returnObj, #cost}'

# 指定展开深度
watch com.example.demo.controller.MemoryLeakController leakMemory '{params, returnObj}' -x 2

# 添加条件过滤
watch com.example.demo.controller.MemoryLeakController leakMemory '{params, returnObj}' 'params[0] > 5'

# 观察静态字段
watch com.example.demo.controller.MemoryLeakController getClass().getDeclaredField("LEAK_LIST").get(null) '{target}'

# 观察多个字段
watch com.example.demo.controller.MemoryLeakController leakMemory '{params[0], params[1], returnObj}'

# 格式化输出
watch com.example.demo.controller.MemoryLeakController leakMemory 'params[0] + "MB -> " + returnObj'

# 限制输出次数
watch com.example.demo.controller.MemoryLeakController leakMemory '{params, returnObj}' -n 5
```

**表达式说明**:
- `params`: 方法参数数组
- `returnObj`: 返回值
- `throwExp`: 异常对象
- `#cost`: 方法执行时间（毫秒）
- `#this`: 当前对象
- `target`: 目标对象

**选项说明**:
- `-x`: 展开深度
- `-n`: 输出次数限制
- `-e`: 只输出异常情况
- `-s`: 指定类加载器
- `-M`: 指定最大输出长度

---

### 3.2 trace - 追踪方法调用链

**用途**: 追踪方法的内部调用路径和耗时

**语法**:
```bash
trace [class-name] [method-name] [condition] [options]
```

**示例**:
```bash
# 追踪方法调用链
trace com.example.demo.service.SlowService slowMethod

# 追踪并限制调用深度
trace com.example.demo.service.SlowService slowMethod --depth 3

# 添加条件过滤
trace com.example.demo.service.SlowService slowMethod '#cost > 1000'

# 追踪并跳过某些类
trace com.example.demo.service.SlowService slowMethod --skipJDKMethod

# 追踪并限制输出条数
trace com.example.demo.service.SlowService slowMethod -n 10

# 追踪并显示调用次数
trace com.example.demo.service.SlowService slowMethod --numberLimit 10

# 追踪并排除某些包
trace com.example.demo.service.SlowService slowMethod --excludePackagePattern 'java.*'
```

**输出说明**:
- `#cost`: 方法执行时间
- `#depth`: 调用深度
- 调用链会显示每个方法的执行时间

---

### 3.3 stack - 查看方法调用路径

**用途**: 查看方法被调用的路径

**语法**:
```bash
stack [class-name] [method-name] [condition] [options]
```

**示例**:
```bash
# 查看方法调用路径
stack com.example.demo.service.SlowService slowMethod

# 添加条件过滤
stack com.example.demo.service.SlowService slowMethod 'params[0] == "test"'

# 限制输出条数
stack com.example.demo.service.SlowService slowMethod -n 5

# 查看多个方法的调用路径
stack com.example.demo.service.SlowService *

# 查看调用路径的详细信息
stack com.example.demo.service.SlowService slowMethod -v
```

**使用场景**:
- 查找方法被谁调用
- 分析调用链路
- 排查方法未执行的原因

---

### 3.4 monitor - 方法调用统计

**用途**: 统计方法的调用次数和耗时

**语法**:
```bash
monitor [class-name] [method-name] [condition] [options]
```

**示例**:
```bash
# 统计方法调用
monitor com.example.demo.service.SlowService slowMethod

# 添加条件过滤
monitor com.example.demo.service.SlowService slowMethod 'params[0] != null'

# 设置统计周期（默认 120 秒）
monitor -c 60 com.example.demo.service.SlowService slowMethod

# 统计多个方法
monitor com.example.demo.service.SlowService *

# 查看统计结果
monitor -l
```

**输出说明**:
- `timestamp`: 时间戳
- `class`: 类名
- `method`: 方法名
- `total`: 总调用次数
- `success`: 成功次数
- `fail`: 失败次数
- `rt`: 平均响应时间
- `fail-rate`: 失败率

---

### 3.5 tt - 方法调用记录

**用途**: 记录方法调用，支持回放和重新调用

**语法**:
```bash
tt [options]
```

**示例**:
```bash
# 记录方法调用
tt -t com.example.demo.service.SlowService slowMethod

# 查看记录
tt -l

# 查看指定记录的详细信息
tt -i 1000

# 重新调用记录的方法
tt -i 1000 -p

# 搜索记录
tt -s 'method.name=="slowMethod"'

# 删除所有记录
tt --delete-all

# 记录并限制条数
tt -t -n 100 com.example.demo.service.SlowService slowMethod

# 查看记录的参数
tt -i 1000 -w 'params'

# 修改参数后重新调用
tt -i 1000 -p --replay-times 3
```

**使用场景**:
- 记录方法调用用于后续分析
- 重新调用方法进行测试
- 修改参数后重新调用

---

## 4. 内存相关命令

### 4.1 memory - 查看内存信息

**用途**: 查看 JVM 的内存使用情况

**语法**:
```bash
memory
```

**示例**:
```bash
# 查看内存使用情况
memory

# 查看堆内存
memory heap

# 查看非堆内存
memory nonheap

# 查看内存详情
memory -a
```

**输出说明**:
- `heap`: 堆内存
- `nonheap`: 非堆内存
- `direct`: 直接内存
- `mapped`: 映射内存

---

### 4.2 heapdump - 导出堆转储

**用途**: 导出堆转储文件用于分析

**语法**:
```bash
heapdump [file-path]
```

**示例**:
```bash
# 导出堆转储文件
heapdump /tmp/heapdump.hprof

# 导出并指定是否只导出存活对象
heapdump --live /tmp/heapdump-live.hprof

# 导出并压缩
heapdump /tmp/heapdump.hprof.gz

# 导出并显示进度
heapdump -v /tmp/heapdump.hprof
```

**注意事项**:
- 导出堆转储会暂停应用
- 文件可能很大，确保磁盘空间充足
- 使用 MAT 或 jvisualvm 等工具分析

---

### 4.3 vmtool - JVM 工具

**用途**: 执行 JVM 相关操作，如强制 GC、查看对象等

**语法**:
```bash
vmtool [options]
```

**示例**:
```bash
# 强制执行 GC
vmtool --action getInstances --className java.lang.String --limit 10

# 查看指定类的实例
vmtool --action getInstances --className com.example.demo.controller.MemoryLeakController --limit 10

# 查看对象的详细信息
vmtool --action getInstances --className java.lang.String --limit 1 --express '#this'

# 强制 GC
vmtool --action forceGc

# 查看对象引用
vmtool --action getInstances --className java.lang.String --limit 1 --express '#this'

# 调用对象方法
vmtool --action getInstances --className java.lang.String --limit 1 --express '#this.toString()'
```

**操作类型**:
- `getInstances`: 获取实例
- `forceGc`: 强制 GC
- `getEnv`: 获取环境变量

---

### 4.4 ognl - 执行 OGNL 表达式

**用途**: 执行 OGNL 表达式，可以调用静态方法、访问静态字段等

**语法**:
```bash
ognl [expression]
```

**示例**:
```bash
# 调用静态方法
ognl '@java.lang.System@currentTimeMillis()'

# 访问静态字段
ognl '@com.example.demo.controller.MemoryLeakController@LEAK_LIST.size()'

# 修改静态字段
ognl '@com.example.demo.controller.MemoryLeakController@LEAK_LIST.clear()'

# 调用实例方法
ognl '#user = new com.example.demo.User(), #user.setName("test"), #user'

# 执行复杂表达式
ognl '#list = new java.util.ArrayList(), #list.add("test"), #list'

# 访问系统属性
ognl '@java.lang.System@getProperty("java.version")'

# 创建对象
ognl 'new java.util.HashMap()'

# 执行数学运算
ognl '100 * 200'
```

**使用场景**:
- 调用静态方法
- 修改静态字段
- 测试表达式
- 快速创建对象

---

## 5. 类加载器相关命令

### 5.1 classloader - 查看类加载器

**用途**: 查看类加载器的继承树和加载的类

**语法**:
```bash
classloader [options]
```

**示例**:
```bash
# 查看类加载器树
classloader

# 查看指定类加载器加载的类
classloader -l

# 查看指定类由哪个类加载器加载
classloader -c 327a647b

# 按哈希值查看类加载器
classloader -t

# 查看类加载器的详细信息
classloader -a

# 查看类加载器的 URL
classloader -c 327a647b | grep URLs
```

**类加载器类型**:
- `BootstrapClassLoader`: 启动类加载器
- `ExtClassLoader`: 扩展类加载器
- `AppClassLoader`: 应用类加载器
- `CustomClassLoader`: 自定义类加载器

---

## 6. 其他常用命令

### 6.1 logger - 日志相关

**用途**: 查看和修改日志级别

**语法**:
```bash
logger [options]
```

**示例**:
```bash
# 查看所有 logger 的日志级别
logger

# 修改指定 logger 的日志级别
logger --name com.example.demo --level debug

# 重置日志级别
logger --name com.example.demo --level reset

# 修改根 logger 的日志级别
logger --name ROOT --level debug

# 查看指定 logger 的配置
logger --name com.example.demo
```

**日志级别**:
- `TRACE`: 最详细的日志
- `DEBUG`: 调试日志
- `INFO`: 信息日志
- `WARN`: 警告日志
- `ERROR`: 错误日志
- `FATAL`: 致命错误日志

---

### 6.2 vmoption - 查看 JVM 参数

**用途**: 查看和修改 JVM 参数

**语法**:
```bash
vmoption [option-name] [option-value]
```

**示例**:
```bash
# 查看所有 JVM 参数
vmoption

# 查看指定参数
vmoption PrintGCDetails

# 修改 JVM 参数
vmoption PrintGCDetails true

# 查看可修改的参数
vmoption | grep writable

# 查看内存相关参数
vmoption | grep -i heap
```

**常用参数**:
- `PrintGCDetails`: 打印 GC 详情
- `HeapDumpOnOutOfMemoryError`: OOM 时导出堆转储
- `MaxHeapFreeRatio`: 最大堆空闲比例
- `MinHeapFreeRatio`: 最小堆空闲比例

---

### 6.3 getstatic - 获取静态字段

**用途**: 获取类的静态字段值

**语法**:
```bash
getstatic [class-name] [field-name]
```

**示例**:
```bash
# 获取静态字段值
getstatic com.example.demo.controller.MemoryLeakController LEAK_LIST

# 获取并展开对象
getstatic com.example.demo.controller.MemoryLeakController LEAK_LIST -x 2

# 获取多个静态字段
getstatic com.example.demo.controller.MemoryLeakController LEAK_LIST STRING_LIST

# 获取静态字段并格式化输出
getstatic com.example.demo.controller.MemoryLeakController LEAK_LIST -x 1 | grep size
```

---

### 6.4 options - 查看 Arthas 选项

**用途**: 查看 Arthas 的全局选项

**语法**:
```bash
options
```

**示例**:
```bash
# 查看所有选项
options

# 修改选项
options unsafe true

# 查看指定选项
options | grep unsafe
```

**常用选项**:
- `unsafe`: 是否允许不安全操作
- `dump`: 是否导出类文件
- `save-result`: 是否保存结果

---

### 6.5 help - 帮助命令

**用途**: 查看命令的帮助信息

**语法**:
```bash
help [command-name]
```

**示例**:
```bash
# 查看所有命令
help

# 查看指定命令的帮助
help watch

# 查看命令的示例
help watch | grep -A 10 "Examples"

# 查看命令的语法
help watch | grep -A 5 "Usage"
```

---

### 6.6 history - 命令历史

**用途**: 查看命令历史记录

**语法**:
```bash
history
```

**示例**:
```bash
# 查看命令历史
history

# 清空历史
history -c

# 查看最近的 10 条命令
history -n 10
```

---

### 6.7 quit/exit - 退出 Arthas

**用途**: 退出 Arthas 控制台

**语法**:
```bash
quit
# 或
exit
```

**示例**:
```bash
# 退出 Arthas
quit

# 退出并关闭会话
exit
```

---

### 6.8 stop - 停止 Arthas

**用途**: 停止 Arthas 并断开连接

**语法**:
```bash
stop
```

**示例**:
```bash
# 停止 Arthas
stop
```

---

## 7. 高级用法

### 7.1 组合命令

```bash
# 查找最忙的线程并查看堆栈
thread -n 1 | grep 'tid' | awk '{print $2}' | xargs thread

# 监控多个方法
watch com.example.demo.controller.* * '{params, returnObj}'

# 追踪并限制输出
trace com.example.demo.service.* * --depth 2 -n 10

# 查找特定状态的线程
thread | grep 'BLOCKED' | awk '{print $1}' | xargs -I {} thread {}
```

---

### 7.2 条件过滤

```bash
# 只监控耗时超过 1000ms 的方法调用
watch com.example.demo.service.SlowService slowMethod '{params, returnObj, #cost}' '#cost > 1000'

# 只监控特定参数的方法调用
watch com.example.demo.controller.MemoryLeakController leakMemory '{params, returnObj}' 'params[0] > 5'

# 只监控异常情况
watch com.example.demo.controller.ExceptionController * '{params, throwExp}' -e

# 只监控特定返回值
watch com.example.demo.service.SlowService slowMethod '{params, returnObj}' 'returnObj != null'
```

---

### 7.3 输出格式化

```bash
# 格式化输出 JSON
watch com.example.demo.controller.MemoryLeakController leakMemory '{#toJson(params)}'

# 自定义输出格式
watch com.example.demo.controller.MemoryLeakController leakMemory 'params[0] + "MB -> " + returnObj'

# 格式化输出时间
watch com.example.demo.service.SlowService slowMethod '{params, returnObj, #cost + "ms"}'

# 格式化输出对象
watch com.example.demo.controller.MemoryLeakController leakMemory '{#toJson(returnObj)}'
```

---

### 7.4 正则表达式

```bash
# 使用正则表达式匹配类名
sc com\.example\.demo\..*Controller

# 使用正则表达式匹配方法名
sm com.example.demo.controller.* *Memory

# 使用正则表达式过滤输出
watch com.example.demo.controller.* * '{params, returnObj}' | grep 'test'
```

---

## 8. 常用场景命令组合

### 8.1 内存泄漏排查

```bash
# 1. 查看内存使用情况
dashboard

# 2. 查看内存详情
memory

# 3. 导出堆转储
heapdump /tmp/heapdump.hprof

# 4. 查看类信息
sc -d com.example.demo.controller.MemoryLeakController

# 5. 监控方法调用
watch com.example.demo.controller.MemoryLeakController leakMemory '{params, returnObj, #cost}'

# 6. 查看静态字段
getstatic com.example.demo.controller.MemoryLeakController LEAK_LIST

# 7. 查看对象实例
vmtool --action getInstances --className byte[] --limit 10
```

---

### 8.2 性能问题排查

```bash
# 1. 查看线程状态
thread -n 5

# 2. 查看最忙的线程
thread -n 3

# 3. 追踪方法调用链
trace com.example.demo.service.SlowService slowMethod

# 4. 统计方法调用
monitor com.example.demo.service.SlowService slowMethod

# 5. 查看方法调用路径
stack com.example.demo.service.SlowService slowMethod

# 6. 监控方法执行时间
watch com.example.demo.service.SlowService slowMethod '{params, returnObj, #cost}' '#cost > 1000'
```

---

### 8.3 死锁排查

```bash
# 1. 检测死锁
thread -b

# 2. 查看死锁线程详情
thread <thread-id>

# 3. 查看所有线程状态
thread

# 4. 查看线程堆栈
thread -n 10

# 5. 查看线程的锁信息
thread -b -v
```

---

### 8.4 异常排查

```bash
# 1. 监控异常
watch com.example.demo.controller.ExceptionController * '{params, throwExp}' -e

# 2. 反编译代码
jad com.example.demo.controller.ExceptionController

# 3. 查看方法调用
stack com.example.demo.controller.ExceptionController nullPointerException

# 4. 追踪方法调用链
trace com.example.demo.controller.ExceptionController nullPointerException

# 5. 查看异常堆栈
watch com.example.demo.controller.ExceptionController * '{#e}' -e
```

---

### 8.5 类加载问题排查

```bash
# 1. 查看类加载器
classloader

# 2. 查看类信息
sc -d com.example.demo.controller.MemoryLeakController

# 3. 查看类由哪个类加载器加载
classloader -c <classloader-hash>

# 4. 反编译类
jad com.example.demo.controller.MemoryLeakController

# 5. 查看类的 URL
sc -d com.example.demo.controller.MemoryLeakController | grep code-source
```

---

## 9. 注意事项

### 9.1 生产环境使用

- **避免在高峰期使用**: 尽量在低峰期使用 Arthas
- **避免执行耗时命令**: 如 `heapdump`、`jad` 等
- **及时退出**: 使用 `quit` 退出后，Arthas 会自动清理资源
- **限制输出**: 使用 `-n` 参数限制输出条数
- **使用条件过滤**: 避免输出过多无用信息

---

### 9.2 命令参数

- **使用帮助**: 使用 `-h` 或 `--help` 查看命令帮助
- **自动补全**: 使用 Tab 键自动补全命令和参数
- **管道过滤**: 使用 `|` 管道命令进行过滤
- **条件过滤**: 使用条件表达式过滤输出

---

### 9.3 性能影响

- **heapdump**: 会暂停应用，谨慎使用
- **trace**: 会产生性能开销
- **watch**: 会产生性能开销
- **monitor**: 会产生性能开销
- **使用 `-n` 参数**: 限制输出条数
- **使用条件过滤**: 减少输出量

---

### 9.4 权限要求

- **Java 进程权限**: 需要 Java 进程的运行权限
- **管理员权限**: 某些命令可能需要管理员权限
- **文件访问权限**: 导出文件需要相应的文件访问权限

---

### 9.5 安全注意事项

- **不要在生产环境随意修改代码**: 使用 `redefine` 要谨慎
- **不要泄露敏感信息**: 输出可能包含敏感信息
- **不要长时间占用资源**: 使用完毕后及时退出
- **不要在关键操作时使用**: 避免影响业务

---

## 10. 快速参考

### 10.1 最常用命令

```bash
# 查看实时监控
dashboard

# 查看线程
thread

# 查找死锁
thread -b

# 监控方法调用
watch com.example.Service method '{params, returnObj}'

# 追踪方法调用链
trace com.example.Service method

# 查看内存
memory

# 反编译类
jad com.example.Service

# 查看类信息
sc -d com.example.Service

# 查看方法信息
sm -d com.example.Service method

# 导出堆转储
heapdump /tmp/heapdump.hprof
```

---

### 10.2 常用选项

```bash
# 限制输出次数
-n 10

# 展开深度
-x 2

# 只输出异常
-e

# 指定类加载器
-c <hash>

# 指定间隔
-i 1000

# 条件过滤
'params[0] > 5'

# 跳过 JDK 方法
--skipJDKMethod

# 限制深度
--depth 3
```

---

### 10.3 表达式

```bash
# 参数
params
params[0]
params[0].field

# 返回值
returnObj
returnObj.field

# 异常
throwExp
throwExp.message

# 执行时间
#cost

# 当前对象
#this

# 目标对象
target

# JSON 格式化
#toJson(obj)

# 调用方法
obj.method()

# 访问字段
obj.field
```

---

## 11. 故障排查

### 11.1 无法连接到目标进程

**原因**: 进程未运行或权限不足

**解决方案**:
- 确认进程运行状态
- 使用管理员权限运行 Arthas
- 检查防火墙设置
- 检查 Arthas 版本兼容性

---

### 11.2 命令执行超时

**原因**: 命令执行时间过长

**解决方案**:
- 避免在生产高峰期执行耗时命令
- 使用 Ctrl+C 中断命令
- 调整命令参数减少输出
- 使用条件过滤减少输出量

---

### 11.3 堆转储文件过大

**原因**: 应用内存占用高

**解决方案**:
- 仅在必要时导出堆转储
- 使用 `jmap` 命令在本地导出
- 使用 MAT 等工具分析堆转储
- 考虑只导出存活对象 `--live`

---

### 11.4 反编译失败

**原因**: 类加载异常或版本不兼容

**解决方案**:
- 确认类路径正确
- 使用兼容的 Arthas 版本
- 检查类是否被修改
- 尝试使用 `sc` 命令查看类信息

---

### 11.5 热更新失败

**原因**: 类结构变化或权限不足

**解决方案**:
- 只修改方法体，不修改方法签名
- 不添加或删除字段
- 不修改类继承关系
- 使用管理员权限

---

## 12. 附录

### 12.1 Arthas 版本

```bash
# 查看 Arthas 版本
version

# 更新 Arthas
java -jar arthas-boot.jar
```

---

### 12.2 相关资源

- [Arthas 官方文档](https://arthas.aliyun.com/doc/)
- [Arthas GitHub](https://github.com/alibaba/arthas)
- [Arthas IDEA 插件](https://github.com/WangJi92/arthas-idea-plugin)
- [Arthas 教程](https://arthas.aliyun.com/doc/arthas-tutorials.html)

---

### 12.3 常见问题

**Q: Arthas 会影响应用性能吗？**

A: Arthas 会占用一定的系统资源，某些命令（如 `trace`、`watch`）会产生性能开销。建议在低峰期使用，并及时退出。

---

**Q: 可以在生产环境使用 Arthas 吗？**

A: 可以，但需要谨慎。避免在高峰期使用耗时命令，使用条件过滤减少输出，使用完毕后及时退出。

---

**Q: Arthas 支持哪些 JDK 版本？**

A: Arthas 支持 JDK 6+，包括 JDK 8、11、17 等版本。

---

**Q: 如何退出 Arthas？**

A: 使用 `quit` 或 `exit` 命令退出 Arthas，会自动清理资源。

---

**Q: Arthas 可以热更新代码吗？**

A: 可以使用 `redefine` 命令热更新代码，但只能修改方法体，不能修改方法签名、字段等。

---

## 13. 更新日志

- **v1.0.0** (2026-02-26): 初始版本，包含所有常用命令

---

## 14. 许可证

本文档仅供学习和参考使用。

---

## 15. 贡献

欢迎提交 Issue 和 Pull Request 来改进本文档！

---

**最后更新**: 2026-02-26
