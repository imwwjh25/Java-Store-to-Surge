在 Tomcat 中调整线程池大小，以及在 Spring Boot 中修改 Tomcat 参数，主要通过配置文件或编程方式实现，具体方法如下：

### 一、Tomcat 独立部署时修改线程池大小

Tomcat 独立运行时（非 Spring Boot 嵌入），线程池配置在其安装目录的 `conf/server.xml` 文件中，通过 `<Connector>` 标签的属性调整：

#### 核心参数说明（针对 HTTP 连接器）：

- `maxThreads`：最大线程数（默认 200），即 Tomcat 可创建的最大工作线程数，决定了并发处理请求的能力上限。
- `minSpareThreads`：最小空闲线程数（默认 10），即使没有请求，也会保持该数量的线程待命，避免频繁创建 / 销毁线程的开销。
- `maxConnections`：最大连接数（默认 10000），超过该值后新连接会排队等待，直到有线程释放。
- `acceptCount`：等待队列长度（默认 100），当 `maxConnections` 满后，新请求会进入队列，超过该长度的请求会被拒绝。

#### 配置示例：


```xml
<!-- 在 server.xml 的 <Service> 标签内 -->
<Connector 
    port="8080" 
    protocol="HTTP/1.1"
    connectionTimeout="20000"
    redirectPort="8443"
    maxThreads="500"         <!-- 最大线程数调整为 500 -->
    minSpareThreads="50"     <!-- 最小空闲线程数调整为 50 -->
    maxConnections="20000"   <!-- 最大连接数调整为 20000 -->
    acceptCount="200"        <!-- 等待队列长度调整为 200 -->
/>
```

修改后需重启 Tomcat 生效。

### 二、Spring Boot 中修改嵌入式 Tomcat 参数

Spring Boot 内置了 Tomcat 作为默认容器，可通过以下方式修改其线程池及其他配置：

#### 1. 最推荐：`application.properties` 或 `application.yml` 配置文件

通过 `server.tomcat` 前缀的属性直接配置，无需编写代码，适合大多数场景。

##### `application.properties` 示例：







```properties
# 线程池配置
server.tomcat.max-threads=500        # 最大线程数（对应 maxThreads）
server.tomcat.min-spare-threads=50   # 最小空闲线程数（对应 minSpareThreads）
server.tomcat.max-connections=20000  # 最大连接数（对应 maxConnections）
server.tomcat.accept-count=200       # 等待队列长度（对应 acceptCount）

# 其他常用配置（可选）
server.port=8080                     # 端口号
server.tomcat.connection-timeout=30000  # 连接超时时间（毫秒）
```

##### `application.yml` 示例：



```yaml
server:
  port: 8080
  tomcat:
    max-threads: 500
    min-spare-threads: 50
    max-connections: 20000
    accept-count: 200
    connection-timeout: 30000
```

#### 2. 编程方式：自定义 `TomcatServletWebServerFactory`

若需要更复杂的配置（如自定义线程池名称、设置拒绝策略等），可通过 Java 代码配置 `TomcatServletWebServerFactory` bean：




```java
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public ConfigurableServletWebServerFactory webServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        
        // 线程池配置
        factory.setMaxThreads(500);                // 最大线程数
        factory.setMinSpareThreads(50);            // 最小空闲线程数
        factory.setMaxConnections(20000);          // 最大连接数
        factory.setAcceptCount(200);               // 等待队列长度
        
        // 可选：设置连接超时时间
        factory.setConnectionTimeout(30000);
        
        return factory;
    }
}
```

这种方式的灵活性更高，适合需要动态调整或特殊定制的场景。

### 三、配置建议

1. **线程池大小调整原则**：
    - 若服务器 CPU 核心数较少（如 4 核），`maxThreads` 不宜过大（通常 200-500），避免线程上下文切换开销过高。
    - 若 CPU 核心数多（如 16 核以上）且内存充足，可适当调大（如 500-1000），提升并发处理能力。
    - `minSpareThreads` 建议设置为日常请求量的峰值附近，避免频繁创建线程。
2. **避免过度调大**：线程数并非越大越好，过多的线程会导致 CPU 频繁切换上下文，反而降低性能，需结合服务器硬件（CPU、内存）和业务压力测试结果调整。

### 总结

- 独立 Tomcat 改 `conf/server.xml` 的 `<Connector>` 标签属性。
- Spring Boot 优先用 `application.properties` 配置 `server.tomcat` 相关参数，复杂场景用 `TomcatServletWebServerFactory` 编程配置。
