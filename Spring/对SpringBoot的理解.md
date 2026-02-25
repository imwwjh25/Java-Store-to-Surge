#### 核心理解

Spring Boot 是 Spring 框架的**快速开发脚手架**，它基于 Spring 框架，通过 “约定大于配置” 的理念，简化了 Spring 应用的搭建、配置和部署流程。它不是对 Spring 的替代，而是对 Spring 的封装和增强。

#### 为什么要用 Spring Boot？

- **简化配置**：告别 Spring 繁琐的 XML 配置，通过注解、自动配置实现零配置 / 少配置启动项目。
- **内嵌服务器**：内置 Tomcat/Jetty/Undertow，无需手动部署 WAR 包，直接`java -jar`启动。
- **起步依赖（Starter）**：如`spring-boot-starter-web`整合了 web 开发所需的 Spring MVC、Tomcat、jackson 等依赖，无需手动管理依赖版本。
- **自动配置**：根据类路径下的依赖自动配置 Bean（如引入`spring-boot-starter-data-jpa`就自动配置 JPA 相关 Bean）。
- **监控与运维**：通过`spring-boot-starter-actuator`可快速实现应用健康检查、指标监控。
- **快速开发**：从搭建项目到运行，几分钟就能完成，大幅降低开发门槛。\


##  Spring Boot 简化配置具体是如何简化的


Spring Boot 的配置简化体现在**消除冗余配置**和**自动化配置**，核心方式如下：

#### （1）消除 XML 配置

Spring Boot 完全摒弃了 Spring 的 XML 配置（如`applicationContext.xml`），转而使用：

- **注解配置**：如`@Configuration`（替代 XML 的`<beans>`）、`@Bean`（替代`<bean>`）、`@Autowired`（依赖注入）。
- **属性配置文件**：通过`application.properties`/`application.yml`集中管理配置，而非分散在多个 XML 中。

#### （2）自动配置（AutoConfiguration）

- **核心原理**：Spring Boot 启动时，通过`@EnableAutoConfiguration`注解扫描`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`文件，加载所有自动配置类（如`WebMvcAutoConfiguration`、`DataSourceAutoConfiguration`）。

- 条件触发 ：自动配置类通过```@Conditional```系列注解（如```@ConditionalOnClass```、```@ConditionalOnMissingBean```）判断是否生效：

    - 例：类路径下有`DispatcherServlet`才会启用 WebMvcAutoConfiguration；
    - 例：用户未自定义`DataSource` Bean 时，才会自动配置默认的数据源。



#### （3）起步依赖（Starter）

- 把相关依赖打包成一个 starter（如`spring-boot-starter-jdbc`包含 jdbc 核心包、连接池、Spring JDBC 等），只需引入一个 starter，无需手动引入多个依赖，且 Spring Boot 统一管理版本，避免版本冲突。

#### （4）默认配置值

- 为常用配置提供默认值，无需手动配置：

    - 例：内嵌 Tomcat 默认端口 8080；
    - 例：数据源默认连接池 HikariCP；
    - 例：日志默认使用 Logback。



#### （5）配置绑定

通过`@ConfigurationProperties`将配置文件中的属性绑定到 Java 类，无需手动读取配置：











```
// 绑定application.yml中prefix为"my.datasource"的属性
@ConfigurationProperties(prefix = "my.datasource")
public class DataSourceProperties {
    private String url;
    private String username;
    // getter/setter
}
```


##  Spring Boot 如何实现零配置

“约定大于配置” 核心是：**默认遵循一套约定规则，无需配置；仅当需要偏离约定时，才手动配置**。Spring Boot 的实现方式如下：

#### （1）默认目录结构约定

- 源码默认放在`src/main/java`，资源文件放在`src/main/resources`；
- 配置文件默认命名为`application.properties/yml`，且放在`src/main/resources`根目录；
- 静态资源（js/css/img）默认放在`src/main/resources/static`；
- 模板文件（如 Thymeleaf）默认放在`src/main/resources/templates`；
- 主启动类默认放在根包下（如`com.example.demo`），Spring Boot 会自动扫描该包及其子包的 Bean。

#### （2）自动配置类的约定

- 自动配置类遵循 “先判断条件，再提供默认 Bean” 的约定：

    - 例：`DataSourceAutoConfiguration`默认使用 HikariCP 连接池，仅当用户配置`spring.datasource.type`时才切换；
    - 例：`WebMvcAutoConfiguration`默认映射`/`到静态资源，默认视图解析器前缀`/templates/`、后缀`.html`。



#### （3）起步依赖的版本约定

- Spring Boot 通过`spring-boot-dependencies`父工程统一管理所有依赖的版本，引入 starter 时无需指定版本，遵循父工程的版本约定；
- 例：引入`spring-boot-starter-web`时，默认依赖 Spring MVC 6.x、Tomcat 10.x（取决于 Spring Boot 版本）。

#### （4）属性配置的命名约定

- 配置属性遵循统一的命名规范，如`spring.datasource.url`、`server.port`，用户只需按约定命名，就能被自动配置类识别；
- 若偏离约定（如自定义配置文件名），需手动指定：`@PropertySource("classpath:custom.properties")`。

#### （5）条件注解的约定

- `@ConditionalOnMissingBean`：约定 “用户未自定义 Bean 时，使用默认 Bean”；
- `@ConditionalOnClass`：约定 “类路径下有指定类时，才启用该配置”；
- 这些注解确保了 “默认遵循约定，自定义覆盖约定” 的核心逻辑。