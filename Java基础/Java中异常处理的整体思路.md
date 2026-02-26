# Java 异常体系与异常处理原则（含实际业务案例）

Java 的异常体系是围绕「**问题分类**」和「**处理责任边界**」设计的，核心目标是：**明确异常类型、统一错误反馈、不隐藏问题、不滥用处理机制**。下面先梳理异常体系的核心结构，再讲 “哪些异常自己处理、哪些抛上游” 的原则，最后结合实际业务案例说明。

## 一、Java 异常体系核心结构

Java 异常的根类是 `java.lang.Throwable`，其下分为两大子类，核心区别是「是否可恢复」和「处理义务」：

### 1. 体系结构图（核心层级）









```plaintext
Throwable（所有异常/错误的根类）
├─ Error（错误：不可恢复，JVM级别的问题）
│  ├─ StackOverflowError（栈溢出，如递归死循环）
│  ├─ OutOfMemoryError（OOM，内存耗尽）
│  └─ NoClassDefFoundError（类未找到，编译/依赖问题）
│
└─ Exception（异常：可处理，程序级别的问题）
   ├─ Checked Exception（受检异常：必须声明或处理）
   │  ├─ IOException（IO操作异常，如文件未找到）
   │  ├─ SQLException（数据库操作异常）
   │  └─ ClassNotFoundException（类加载异常）
   │
   └─ Unchecked Exception（非受检异常：无需声明，RuntimeException子类）
      ├─ NullPointerException（空指针，编程错误）
      ├─ IllegalArgumentException（参数非法）
      ├─ IndexOutOfBoundsException（索引越界）
      └─ BusinessException（自定义业务异常，如“余额不足”）
```

### 2. 核心区别（面试高频）

| 类型                | 代表异常                  | 处理义务        | 核心特点                                         |
| ------------------- | ------------------------- | --------------- | ------------------------------------------------ |
| Error（错误）       | OOM、栈溢出               | 无需处理        | 系统级问题，程序无法恢复，通常导致 JVM 退出      |
| Checked Exception   | IOException、SQLException | 必须处理 / 声明 | 可预期的外部问题（如文件不存在、数据库连接失败） |
| Unchecked Exception | 空指针、参数非法          | 可选处理        | 编程错误或运行时不可预期问题（如逻辑漏洞）       |

## 二、异常处理核心原则：该抛还是该 catch？

核心判断标准：**「谁能决策 / 恢复，谁处理」** + **「责任边界清晰」**。简单说：如果当前方法能「修复异常、恢复流程」，就自己 catch 处理；如果当前方法无法处理，需要上游（调用方）做决策（如返回错误提示、重试、降级），就抛上游。

### 1. 必须自己 catch 处理的场景（当前方法能恢复 / 兜底）

满足以下任一条件，优先自己处理，不抛给上游：

- 异常是「局部问题」，可通过默认值、重试、降级等方式修复，不影响整体流程；
- 异常是「预期内的外部依赖问题」（如网络波动、文件不存在），当前方法可独立兜底；
- 异常是「不重要的非核心流程问题」（如日志打印失败），无需上游关注。

#### 典型例子：

- **场景 1：读取配置文件时，文件不存在（FileNotFoundException）**处理：catch 异常后，使用默认配置（如 `config = new DefaultConfig()`），不影响业务主流程。







  ```java
  public Config loadConfig(String path) {
      try (FileInputStream fis = new FileInputStream(path)) {
          return Config.parse(fis);
      } catch (FileNotFoundException e) {
          log.warn("配置文件{}不存在，使用默认配置", path, e);
          return new DefaultConfig(); // 兜底，恢复流程
      } catch (IOException e) {
          // 解析失败，当前方法无法修复，抛上游
          throw new ConfigParseException("配置文件解析失败", e);
      }
  }
  ```



- **场景 2：调用第三方接口时，网络波动导致超时（TimeoutException）**处理：catch 异常后，重试 2 次，仍失败则记录告警，返回降级结果（如 “暂时无法查询，稍后重试”）。









  ```java
  public String callThirdPartyApi(String param) {
      int retryCount = 2;
      while (retryCount-- > 0) {
          try {
              return thirdPartyClient.query(param);
          } catch (TimeoutException e) {
              log.warn("调用第三方接口超时，剩余重试次数：{}", retryCount, e);
              TimeUnit.MILLISECONDS.sleep(100); // 重试间隔
          }
      }
      log.error("调用第三方接口失败，已耗尽重试次数");
      return "暂时无法查询，请稍后重试"; // 降级兜底
  }
  ```



- **场景 3：非核心流程的异常（如日志打印失败）**处理：catch 异常后仅记录日志，不影响主流程（如用户下单成功，但日志写入失败，不能让下单流程失败）。

### 2. 必须抛上游的场景（当前方法无法处理，需上游决策）

满足以下任一条件，优先抛上游，不强行处理：

- 异常是「业务规则违规」，需要上游返回用户提示（如 “余额不足”“参数非法”）；
- 异常是「核心流程不可恢复问题」，需要上游做决策（如重试、回滚事务、降级）；
- 异常是「编程错误」（如空指针、索引越界），需要上游感知并修复代码；
- 异常是「需要统一监控 / 告警」的问题（如数据库连接池耗尽），需上游触发告警机制。

#### 典型例子：

- **场景 1：业务规则违规（如支付时余额不足）**处理：抛自定义业务异常（`InsufficientBalanceException`），上游 Controller 捕获后返回 400 错误和提示。







  ```java
  public void deductBalance(Long userId, BigDecimal amount) {
      BigDecimal balance = userMapper.getBalance(userId);
      if (balance.compareTo(amount) < 0) {
          // 余额不足，当前方法无法决策（需告知用户），抛上游
          throw new InsufficientBalanceException("用户余额不足，当前余额：" + balance);
      }
      userMapper.updateBalance(userId, balance.subtract(amount));
  }
  ```



- **场景 2：参数非法（编程错误 / 用户输入错误）**处理：抛 `IllegalArgumentException`，上游校验层捕获后返回参数错误提示。







  ```java
  public Order createOrder(OrderDTO orderDTO) {
      if (orderDTO.getUserId() == null) {
          throw new IllegalArgumentException("订单创建失败：用户ID不能为空");
      }
      if (orderDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
          throw new IllegalArgumentException("订单创建失败：金额必须大于0");
      }
      // 正常创建订单...
  }
  ```



- **场景 3：核心流程依赖不可用（如数据库连接失败）**处理：不 catch `SQLException`，直接抛上游（或转译为业务异常），上游服务触发事务回滚和告警。











  ```java
  public void saveOrder(Order order) {
      try {
          orderMapper.insert(order);
      } catch (SQLException e) {
          // 数据库插入失败，当前方法无法恢复，转译为业务异常抛上游
          throw new OrderSaveFailedException("订单保存失败：数据库异常", e);
      }
  }
  ```



- **场景 4：编程错误（如空指针）**处理：不强行 catch（如 `catch (NullPointerException e) {}`），让异常抛上游，通过日志定位代码漏洞（如未判空）。

### 3. 特殊情况：异常转译（抛上游，但要 “包装”）

当底层异常（如 `SQLException`、`RPCException`）对上游不友好时，需要「转译」为上游能理解的异常（如业务异常），同时保留原始异常栈（便于排查）。

#### 例子：










```java
public User getUserById(Long id) {
    try {
        return userMapper.selectById(id);
    } catch (SQLException e) {
        // 转译：将底层SQL异常 → 业务异常，保留原始栈
        throw new UserQueryFailedException("查询用户失败，用户ID：" + id, e);
    }
}
```

### 4. 绝对不能做的 3 件事

1. **空 catch（隐藏问题）**：`catch (Exception e) {}` 会导致异常被掩盖，排查时无任何线索；
2. **滥用 throws（甩锅）**：方法签名上写 `throws Exception`，把所有异常抛给上游，让调用方不堪重负；
3. **catch 后不处理也不抛**：比如 `catch (FileNotFoundException e) { log.error("文件不存在"); }`，既不兜底也不通知上游，导致流程中断且无人知晓。

## 三、实际业务案例：订单支付流程的异常处理

以「用户支付订单」为例，结合前面的原则，看不同异常的处理方式：

### 业务流程：

用户发起支付 → 校验订单参数 → 调用支付网关 RPC → 扣减用户余额 → 更新订单状态

### 异常处理方案：

| 异常类型                                           | 处理方式                                 | 原因（符合原则）                                             |
| -------------------------------------------------- | ---------------------------------------- | ------------------------------------------------------------ |
| 订单 ID 为空（IllegalArgumentException）           | 抛上游                                   | 参数非法，上游 Controller 需返回 400 错误提示用户            |
| 支付网关 RPC 超时（TimeoutException）              | catch 后重试 2 次，仍失败则抛上游        | 网络波动是暂时的，重试可能恢复；重试失败后需上游返回 503（服务不可用）并告警 |
| 扣减余额时余额不足（InsufficientBalanceException） | 抛上游                                   | 业务规则违规，上游需返回 400 提示用户 “余额不足”             |
| 数据库更新失败（SQLException）                     | 转译为 OrderUpdateFailedException 抛上游 | 核心流程失败，上游需回滚事务、记录告警，便于运维排查         |
| 日志打印失败（IOException）                        | catch 后仅记录日志                       | 非核心流程，不影响支付主流程，无需上游关注                   |
| OOM（OutOfMemoryError）                            | 不处理                                   | 系统级错误，程序无法恢复，让 JVM 抛出，触发服务监控告警和重启 |

### 代码实现片段：







```java
@Transactional
public PayResult payOrder(Long orderId, BigDecimal amount, Long userId) {
    // 1. 参数校验：非法则抛上游
    if (orderId == null || amount == null || userId == null) {
        throw new IllegalArgumentException("支付失败：订单ID、金额、用户ID不能为空");
    }

    // 2. 调用支付网关：超时重试后抛上游
    int retry = 2;
    while (retry-- > 0) {
        try {
            payGatewayClient.pay(userId, amount); // 第三方RPC调用
            break;
        } catch (TimeoutException e) {
            log.warn("支付网关超时，订单ID：{}，剩余重试次数：{}", orderId, retry, e);
            TimeUnit.MILLISECONDS.sleep(200);
            if (retry == 0) {
                throw new PayGatewayException("支付失败：支付网关暂时不可用", e);
            }
        }
    }

    // 3. 扣减余额：业务异常抛上游，SQL异常转译后抛上游
    try {
        userService.deductBalance(userId, amount); // 内部方法，余额不足会抛InsufficientBalanceException
    } catch (SQLException e) {
        throw new OrderPayFailedException("支付失败：余额扣减异常，订单ID：" + orderId, e);
    }

    // 4. 更新订单状态：核心流程，异常转译后抛上游（触发事务回滚）
    try {
        orderMapper.updateStatus(orderId, OrderStatus.PAID);
    } catch (SQLException e) {
        throw new OrderUpdateFailedException("支付失败：订单状态更新异常", e);
    }

    // 5. 日志打印：非核心流程，catch后仅记录
    try {
        logService.recordPayLog(orderId, userId, amount);
    } catch (IOException e) {
        log.error("支付日志记录失败，订单ID：{}", orderId, e); // 不影响主流程
    }

    return PayResult.success("支付成功");
}
```

## 四、总结

Java 异常处理的核心是「**责任边界清晰**」，记住 3 句话：

1. 能修复、能兜底的异常，自己 catch 处理（不麻烦上游）；
2. 需决策、需通知的异常，抛上游（让负责的人处理）；
3. 底层异常转译为业务异常（方便上游理解，保留原始栈）。