Spring（尤其 Spring Boot）启动 Tomcat 的核心是 **“嵌入式容器自动配置”**——Spring Boot 通过 `spring-boot-starter-web` 依赖引入嵌入式 Tomcat，再通过自动配置机制（`AutoConfiguration`）初始化 Tomcat 容器、注册 Web 组件（Servlet/Filter/Listener），最终绑定端口启动服务。

整个流程可拆解为「依赖引入→自动配置→Tomcat 初始化→Web 组件注册→启动服务」五个核心阶段，以下结合源码和底层原理详细说明：

### 一、前置基础：嵌入式 Tomcat 与依赖关系

#### 1. 核心依赖（Spring Boot 场景）

Spring Boot 无需手动安装 Tomcat，只需引入 `spring-boot-starter-web` 依赖，会自动传递嵌入式 Tomcat 核心包：


```xml
<!-- Spring Boot Web  starter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

依赖传递的关键组件：

- `tomcat-embed-core`：嵌入式 Tomcat 核心包（包含 Tomcat 容器、连接器等核心类）；
- `tomcat-embed-jasper`（可选）：支持 JSP 解析；
- `spring-boot-autoconfigure`：Spring Boot 自动配置核心包，包含 Tomcat 相关自动配置类。

#### 2. 核心设计理念

嵌入式 Tomcat 本质是将传统独立部署的 Tomcat 容器（`catalina.jar` 等）打包为可嵌入的 Jar 包，允许通过 Java 代码直接初始化、配置和启动 Tomcat，无需单独部署 WAR 包，实现 “一键启动”。

### 二、Spring 启动 Tomcat 的完整流程（Spring Boot 2.x+）

#### 阶段 1：Spring Boot 应用启动入口（`SpringApplication.run()`）

Spring 启动 Tomcat 的触发点是 `SpringApplication.run(Application.class)`，该方法会完成两件核心事：

1. 初始化 Spring 上下文（`ApplicationContext`）；
2. 触发 “嵌入式容器启动”（若为 Web 应用）。

关键判断逻辑：`SpringApplication` 会通过 `WebApplicationType` 检测应用类型（Web 应用需满足：classpath 存在 `Servlet`、`Tomcat` 相关类），若为 Web 应用，则启动嵌入式容器。

#### 阶段 2：自动配置加载 Tomcat 相关组件（`TomcatAutoConfiguration`）

Spring Boot 自动配置机制通过 `META-INF/spring.factories` 加载 `TomcatAutoConfiguration` 类，该类是嵌入式 Tomcat 配置的核心：


```java
// TomcatAutoConfiguration 核心源码（简化）
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ Servlet.class, Tomcat.class, UpgradeProtocol.class }) // 存在 Tomcat 和 Servlet 类才生效
@ConditionalOnMissingBean(value = ServletWebServerFactory.class, search = SearchStrategy.CURRENT)
public class TomcatAutoConfiguration {

    // 注册 Tomcat 容器工厂（核心 Bean）
    @Bean
    public TomcatServletWebServerFactory tomcatServletWebServerFactory(...) {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        // 配置端口、协议、连接数等（默认端口 8080，可通过 server.port 配置）
        customizeFactory(factory, ...);
        return factory;
    }
}
```

核心作用：

- 注册 `TomcatServletWebServerFactory`（Tomcat 容器工厂 Bean），负责创建和配置 Tomcat 容器；
- 支持通过 `application.properties` 配置 Tomcat（如 `server.port=8081`、`server.tomcat.max-threads=200`），工厂会自动读取这些配置并应用到 Tomcat。

#### 阶段 3：Tomcat 容器初始化（`TomcatServletWebServerFactory` 工作）

`TomcatServletWebServerFactory` 的核心方法是 `getWebServer(ServletContextInitializer... initializers)`，该方法会初始化 Tomcat 容器，步骤如下：

1. 创建 Tomcat 实例：


   ```java
   Tomcat tomcat = new Tomcat();
   // 配置 Tomcat 工作目录（默认在临时目录，如 /tmp/tomcat.xxx）
   File baseDir = this.baseDirectory != null ? this.baseDirectory : createTempDir("tomcat");
   tomcat.setBaseDir(baseDir.getAbsolutePath());
   ```

   

2. 创建连接器（Connector）：

   - 连接器是 Tomcat 接收请求的组件，默认使用 NIO 协议（`org.apache.coyote.http11.Http11NioProtocol`）；

   - 配置端口（默认 8080）、连接超时时间、最大连接数等：


     ```java
     Connector connector = new Connector(this.protocol);
     connector.setPort(this.port); // 绑定配置的端口
     connector.setConnectionTimeout(this.connectionTimeout);
     tomcat.getService().addConnector(connector);
     ```

     

3. 创建 Engine、Host、Context 组件（Tomcat 架构核心）：

   Tomcat 容器架构是 “Engine → Host → Context → ServletWrapper” 的层级结构：

   - `Engine`：Tomcat 顶层容器，管理多个 Host；

   - `Host`：虚拟主机（默认 `localhost`），对应一个域名；

   - `Context`：Web 应用上下文（对应 Spring Boot 应用），管理 Servlet、Filter 等组件；

   - 代码逻辑：
     ```java
     // 创建 Host（默认 localhost）
     Engine engine = tomcat.getEngine();
     Host host = (Host) engine.findChild(engine.getDefaultHost());
     host.setAutoDeploy(false);
     // 创建 Context（上下文路径默认空，即 /）
     Context context = createContext(tomcat, host, ...);
     ```

     

#### 阶段 4：注册 Web 组件（Servlet/Filter/Listener）

Spring MVC 的核心组件（`DispatcherServlet`、`CharacterEncodingFilter` 等）需要注册到 Tomcat 的 `Context` 中才能生效，这一步通过 `ServletContextInitializer` 完成：

1. Spring 自动创建 `DispatcherServlet`： ```DispatcherServletAutoConfiguration```会自动注册```DispatcherServlet```（Spring MVC 核心 Servlet，负责分发请求），并封装为```ServletRegistrationBean```（Servlet 注册 Bean）。

2. 通过 `ServletContextInitializer` 注册组件：Spring Boot 会收集所有```ServletRegistrationBean```、```FilterRegistrationBean```、 ```ListenerRegistrationBean```，并通过```ServletContextInitializer```回调，将这些组件注册到 Tomcat 的```Context```中：

   ```java
   // 简化逻辑：将 DispatcherServlet 注册到 Tomcat
   ServletRegistrationBean<DispatcherServlet> dispatcherServletRegistration = new ServletRegistrationBean<>(dispatcherServlet, "/");
   dispatcherServletRegistration.setName("dispatcherServlet");
   ```
3. **关键绑定**：`DispatcherServlet` 会关联 Spring 上下文（`WebApplicationContext`），确保请求处理时能访问 Spring 管理的 Bean（如 Controller、Service）。

#### 阶段 5：启动 Tomcat 服务（`Tomcat.start()`）

Tomcat 容器初始化和组件注册完成后，`TomcatServletWebServerFactory` 会调用 `tomcat.start()` 启动服务，底层流程：

1. 启动 Tomcat 的 `Server` 组件（顶层组件，管理所有 Service）；
2. 启动 `Service` 组件（包含 Connector 和 Engine）；
3. 连接器（Connector）开始监听配置的端口（如 8080），接收客户端 HTTP 请求；
4. 引擎（Engine）开始处理请求：将请求转发到对应的 Host 和 Context，最终由 `DispatcherServlet` 分发到 Controller 处理。

#### 阶段 6：Spring 上下文刷新完成

Tomcat 启动成功后，Spring 上下文（`WebApplicationContext`）会完成刷新（`refresh()`），此时 Spring 管理的所有 Bean（Controller、Service、Repository 等）已初始化完成，应用正式就绪。

### 三、核心组件关系（源码层面）

| 组件类名                        | 作用                                             | 层级关系                            |
| ------------------------------- | ------------------------------------------------ | ----------------------------------- |
| `TomcatServletWebServerFactory` | Tomcat 容器工厂，创建和配置 Tomcat               | Spring Bean，顶层工厂类             |
| `Tomcat`                        | 嵌入式 Tomcat 核心实例                           | 被工厂创建，包含 Service、Engine 等 |
| `Connector`                     | 连接器，监听端口、接收请求                       | 属于 Service，绑定协议（NIO/HTTP2） |
| `Engine`                        | Tomcat 顶层容器，管理多个 Host                   | 属于 Service                        |
| `Host`                          | 虚拟主机（默认 [localhost](https://localhost/)） | 属于 Engine                         |
| `Context`                       | Web 应用上下文，管理 Servlet/Filter              | 属于 Host，对应 Spring Boot 应用    |
| `DispatcherServlet`             | Spring MVC 核心 Servlet，分发请求                | 注册到 Context，关联 Spring 上下文  |

### 四、关键配置与自定义（实际应用）

#### 1. 常用配置（`application.properties`）



```properties
# 端口配置（默认 8080，0 表示随机端口）
server.port=8081
# Tomcat 工作目录（默认临时目录，可指定固定目录）
server.tomcat.basedir=./tomcat-work
# 最大线程数（默认 200）
server.tomcat.max-threads=500
# 最大连接数（默认 10000）
server.tomcat.max-connections=20000
# 连接超时时间（默认 20000ms）
server.tomcat.connection-timeout=30000
```

#### 2. 自定义 Tomcat 配置（Java 代码）

通过 `WebServerFactoryCustomizer` 自定义 Tomcat 行为：




```java
@Configuration
public class TomcatConfig {
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            // 配置最大线程数
            factory.addConnectorCustomizers(connector -> {
                Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
                protocol.setMaxThreads(500);
                protocol.setMinSpareThreads(50); // 最小空闲线程数
            });
            // 配置上下文路径（默认 /，改为 /app）
            factory.setContextPath("/app");
        };
    }
}
```

### 五、传统 Spring（非 Boot）启动 Tomcat 的区别

传统 Spring（如 Spring MVC + 独立 Tomcat）与 Spring Boot 嵌入式 Tomcat 的核心区别：

| 对比维度 | 传统 Spring（独立 Tomcat）                                   | Spring Boot（嵌入式 Tomcat）                             |
| -------- | ------------------------------------------------------------ | -------------------------------------------------------- |
| 部署方式 | 打包为 WAR 包，部署到独立 Tomcat 的 `webapps` 目录           | 打包为 Jar 包，嵌入式 Tomcat 随应用启动                  |
| 启动触发 | Tomcat 启动时，通过 `ContextLoaderListener` 初始化 Spring 上下文 | Spring 应用启动时，通过自动配置初始化 Tomcat             |
| 配置方式 | 依赖 Tomcat 的 `server.xml`、`web.xml` 配置                  | 依赖 `application.properties` 和 Java 代码配置           |
| 组件注册 | 通过 `web.xml` 注册 `DispatcherServlet`                      | 自动配置注册 `DispatcherServlet`，无需手动编写 `web.xml` |

### 六、核心总结

Spring（Spring Boot）启动 Tomcat 的核心逻辑：

1. **依赖引入**：`spring-boot-starter-web` 传递嵌入式 Tomcat 包；
2. **自动配置**：`TomcatAutoConfiguration` 注册 `TomcatServletWebServerFactory` 工厂；
3. **容器初始化**：工厂创建 Tomcat 实例，配置 Connector、Engine、Context 等组件；
4. **组件注册**：将 `DispatcherServlet` 等 Web 组件注册到 Tomcat 上下文；
5. **启动服务**：调用 `tomcat.start()` 监听端口，Spring 上下文刷新完成，应用就绪。

本质是 **“Spring 主导，Tomcat 嵌入式化”**，通过自动配置简化了传统 Tomcat 的部署和配置，实现 “一键启动 Web 应用”。
