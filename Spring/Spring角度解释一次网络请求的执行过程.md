用过 Spring 框架，HTTP 请求从打到服务器到返回的全过程，是 **“网络层 → 服务器容器层 → Spring 容器层 → 业务层 → 响应层”** 的完整链路，涉及 TCP 连接、容器转发、Spring 核心组件（DispatcherServlet、HandlerMapping 等）、业务处理、响应封装等多个环节，以下是超详细拆解（以 Spring Boot 2.x + Tomcat 容器为例，基于 HTTP/1.1 协议）：

### 前置背景

- 服务器环境：Spring Boot 内置 Tomcat（默认端口 8080），部署在 Linux 服务器；
- 请求场景：用户通过浏览器 / 客户端发送 `GET http://xxx.xxx.xxx:8080/api/user/1001` 请求，获取用户信息；
- 核心组件：Tomcat（Servlet 容器）、Spring MVC（DispatcherServlet、HandlerMapping 等）、Spring 容器（Bean 管理）、业务 Service、数据库（MySQL）。

## 全过程链路拆解（12 个核心步骤）

### 步骤 1：建立 TCP 连接（HTTP 基于 TCP 可靠传输）

1. 客户端（浏览器 / APP）解析目标 URL，通过 DNS 获取服务器 IP 地址（如 `192.168.1.100`）；

2. 客户端与服务器发起 TCP 三次握手 ：

- 客户端发送 SYN 包，请求建立连接；
- 服务器（Tomcat 监听 8080 端口）回复 SYN+ACK 包，确认连接；
- 客户端发送 ACK 包，TCP 连接建立完成（进入 ESTABLISHED 状态）；

3. 若为 HTTPS 请求，还需额外执行 **TLS 握手**（协商加密算法、交换密钥），建立加密通道（HTTP 则直接传输明文数据）。

### 步骤 2：Tomcat 接收请求（容器层监听与接收）

1. Tomcat 启动时，会初始化 **Connector 组件**（默认使用 NIO 模式），监听 8080 端口的 TCP 连接；
2. Connector 内部维护一个 **线程池**（默认核心线程数 10，最大线程数 200），用于处理请求；
3. 网卡接收到客户端的 HTTP 请求数据包后，通过 Linux 内核的 TCP/IP 协议栈解析，传递给 Tomcat 的 Connector；
4. Connector 从线程池分配一个工作线程（Worker Thread），将请求数据读取到内存缓冲区，封装为 `org.apache.coyote.Request` 对象（Tomcat 内部请求对象）。

### 步骤 3：Tomcat 解析 HTTP 请求（容器层协议解析）

1. Tomcat 的 **CoyoteAdapter 适配器** 接收 `coyote.Request` 对象，将其转换为 **Servlet 规范的 HttpServletRequest 对象**（标准化请求接口，包含请求行、请求头、请求体等信息）；
2. 解析核心内容：
    - 请求行：Method（GET）、RequestURI（`/api/user/1001`）、HTTP 版本（HTTP/1.1）；
    - 请求头：Host（目标主机）、User-Agent（客户端类型）、Accept（接收的响应格式）、Cookie（会话信息）等；
    - 请求体：GET 请求无请求体，POST 请求会解析表单数据 / JSON 数据（如 `application/json` 格式的参数）；
3. 同时创建对应的 `HttpServletResponse` 对象（用于后续封装响应数据）。

### 步骤 4：Tomcat 路由请求到 Spring MVC（DispatcherServlet 转发）

1. Tomcat 启动时，Spring Boot 会自动配置 **DispatcherServlet**（Spring MVC 的核心前端控制器），并注册到 Tomcat 的 Servlet 容器中（URL 映射为 `/`，即接收所有请求）；
2. Tomcat 的工作线程将 `HttpServletRequest` 和 `HttpServletResponse` 传递给 DispatcherServlet，调用其 `service()` 方法；
3. DispatcherServlet 是 Spring MVC 的 “入口”，负责协调所有后续组件（HandlerMapping、HandlerAdapter 等），实现请求的分发与处理。

### 步骤 5：DispatcherServlet 查找 Handler（处理器映射）

1. DispatcherServlet 调用 **HandlerMapping 组件**（Spring 容器中的 Bean），根据请求的 URI 和 Method，查找对应的 **处理器（Handler）**（即我们写的 `@Controller` 中的接口方法）；
2. Spring 支持的 HandlerMapping 实现（默认启用多个，按优先级匹配）：
    - `RequestMappingHandlerMapping`：处理 `@RequestMapping`/`@GetMapping`/`@PostMapping` 注解的处理器（最常用）；
    - `BeanNameUrlHandlerMapping`：按 Bean 名称匹配 URL（如 Bean 名称为 `/api/user`，则匹配该 URL 请求）；
3. 匹配过程：
    - 解析 RequestURI（`/api/user/1001`），与 `@RequestMapping` 注解的 `value`（如 `"/api/user/{id}"`）匹配；
    - 匹配成功后，返回一个 **HandlerExecutionChain 对象**（包含目标 Handler 方法 + 拦截器链 InterceptorChain）。

### 步骤 6：执行拦截器前置逻辑（Spring MVC 拦截器）

1. HandlerExecutionChain 包含配置的 **拦截器（HandlerInterceptor）**（如登录验证拦截器、日志拦截器）；

2. 依次调用每个拦截器的```preHandle()```方法：

- 若 `preHandle()` 返回 `true`，继续执行后续流程；
- 若返回 `false`，中断请求（直接返回响应，如未登录则返回 401 状态码）；

3. 拦截器可用于：登录验证、权限校验、请求日志记录、参数预处理等（如统一打印请求 URL、耗时统计）。

### 步骤 7：HandlerAdapter 执行 Handler 方法（参数绑定 + 业务调用）

1. DispatcherServlet 调用 **HandlerAdapter 组件**（适配器模式，适配不同类型的 Handler），默认使用 `RequestMappingHandlerAdapter`；

2. 核心工作：

   请求参数绑定

   （将 HttpServletRequest 中的参数转换为 Handler 方法的入参）：

    - 路径参数：解析 `@PathVariable("id")`（如 `1001` 绑定到 `Long id` 参数）；
    - 请求头参数：解析 `@RequestHeader`（如 `User-Agent`）；
    - 请求参数：解析 `@RequestParam`（如 `?name=zhangsan`）；
    - 请求体参数：解析 `@RequestBody`（如 JSON 格式参数，依赖 `MappingJackson2HttpMessageConverter` 转换为 Java 对象）；
    - 其他参数：`HttpServletRequest`、`HttpServletResponse`、`Model` 等（Spring 自动注入）；

3. 参数绑定完成后，HandlerAdapter 调用目标 Handler 方法（即 `@Controller` 中的 `getUserById(Long id)` 方法），执行业务逻辑。

### 步骤 8：业务逻辑执行（Service + 数据库操作）

1. Handler 方法（Controller 接口）调用 Spring 容器中的 **Service 层 Bean**（如 `UserService`），执行核心业务逻辑；

2. Service 层可能调用其他 Service 或



DAO 层（MyBatis Mapper）

，操作数据库：

- Spring 管理的 `DataSource` 组件从 **连接池**（如 HikariCP，Spring Boot 默认）获取数据库连接；

- MyBatis 执行 SQL 语句（如 `SELECT * FROM user WHERE id=1001`），通过 JDBC 操作 MySQL；

- 若方法加了``` @Transactiona```注解，Spring 会通过 事务管理器（PlatformTransactionManager） 管理事务：

     - 开启事务：设置连接的 `autoCommit=false`；
     - 执行 SQL 后无异常：提交事务（`COMMIT`）；
     - 若出现异常：回滚事务（`ROLLBACK`）；

3. 业务逻辑执行完成后，返回结果数据（如 `User` 对象：`id=1001, name=zhangsan`）。

### 步骤 9：HandlerAdapter 封装响应结果（ModelAndView/JSON）

1. Handler 方法返回结果后，HandlerAdapter 将其封装为



ModelAndView 对象

（或直接返回数据对象，如 JSON）：

- 若返回 `String`（如 `"user/detail"`）：视为视图名称，ModelAndView 包含视图名 + 模型数据（如 `model.addAttribute("user", user)`）；
- 若返回 `User` 对象且加了 `@ResponseBody` 注解（或 Controller 加 `@RestController`）：Spring 会通过 **MessageConverter 组件**（如 MappingJackson2HttpMessageConverter）将对象序列化为 JSON 字符串；

2. 若有异常抛出：

    - 触发 Spring 的 **异常处理器（HandlerExceptionResolver）**；
    - 捕获异常后，返回自定义错误响应（如 500 状态码 + 错误信息 JSON），或跳转到错误页面。

### 步骤 10：执行拦截器后置逻辑（拦截器收尾）

1. 响应结果封装完成后，DispatcherServlet 调用拦截器链的 `postHandle()` 方法（在视图渲染前执行）；
2. 可用于：修改响应数据、补充模型数据、记录业务执行结果等；
3. 若前面步骤抛出异常，`postHandle()` 不会执行，直接进入 `afterCompletion()` 方法。

### 步骤 11：视图渲染 / 响应数据写入（返回给客户端）

1. 若为 页面渲染场景

（如 Spring MVC 传统开发，返回 JSP/Thymeleaf 视图）：

- DispatcherServlet 调用 **ViewResolver 组件**（视图解析器），根据 ModelAndView 的视图名，解析为具体的视图对象（如 ThymeleafView）；
- 视图对象将模型数据（如 `user` 对象）渲染到模板文件（如 `user/detail.html`），生成 HTML 字符串；
- 将 HTML 字符串写入 HttpServletResponse 的输出流；

2. 若为 接口场景 @RestController/JSON 响应）：

- 无需视图渲染，直接将 JSON 字符串写入 HttpServletResponse 的输出流；

3. 补充响应信息：

    - 设置响应头（如 `Content-Type: application/json;charset=UTF-8`、`Cache-Control: no-cache`）；
    - 设置响应状态码（如 200 OK、404 Not Found、500 Internal Server Error）。

### 步骤 12：关闭 TCP 连接（或保持连接）

1. 服务器（Tomcat 工作线程）将响应数据通过 TCP 连接发送给客户端；
2. 客户端接收响应数据，解析 HTTP 响应（状态码、响应头、响应体），展示结果（如浏览器渲染 HTML、APP 解析 JSON）；
3. TCP 连接处理：
    - HTTP/1.1 默认开启 **Keep-Alive**（长连接），连接不会立即关闭，可复用为后续请求（减少三次握手开销）；
    - 若客户端发送 `Connection: close` 请求头，或长连接超时（Tomcat 默认超时 60 秒），服务器会主动关闭 TCP 连接（四次挥手）。









## 关键补充（容易忽略的细节）

1. Spring 容器的初始化时机 ：

    - Spring Boot 启动时，通过 `SpringApplication.run()` 初始化 Spring 容器，扫描并创建 `@Controller`、`@Service`、`@Repository` 等 Bean；
    - DispatcherServlet、HandlerMapping、HandlerAdapter 等 Spring MVC 核心组件，由 Spring Boot 自动配置（`WebMvcAutoConfiguration`），无需手动配置。

2. 线程模型 ：

    - Tomcat 使用 “线程池 + NIO” 模型，避免每个请求创建一个线程（减少资源开销）；
    - 一个请求从接收至响应，全程由 Tomcat 的一个工作线程处理（Spring 无额外线程切换，除非业务层手动开启异步线程）。

3. 异步请求场景 ：

    - 若 Handler 方法加 `@Async` 注解，或返回 `Callable`/`DeferredResult`，Spring 会将业务逻辑提交到异步线程池执行，Tomcat 工作线程可提前释放（处理更多请求）；
    - 异步任务执行完成后，通过回调机制写入响应流。

4. 异常处理链路 ：

    - 业务层抛出的异常，会被 `HandlerExceptionResolver` 捕获，可通过 `@ControllerAdvice + @ExceptionHandler` 全局统一处理（返回标准化错误响应）。

## 总结

HTTP 请求的全过程，本质是 **“协议解析 → 组件协作 → 业务执行 → 响应封装”** 的流水线：

- 底层依赖 TCP/IP 协议和 Tomcat 容器的请求接收与解析；
- 核心依赖 Spring MVC 的 DispatcherServlet 作为 “中枢”，协调 HandlerMapping、HandlerAdapter 等组件完成请求分发；
- 业务层依赖 Spring 的 Bean 管理和事务管理，确保代码解耦和数据一致性；
- 最终通过响应流将结果返回客户端，完成一次请求 - 响应闭环。

理解这个链路，能帮你快速定位问题（如请求 404 可能是 HandlerMapping 匹配失败，500 可能是业务层异常，401 可能是拦截器校验未通过）。