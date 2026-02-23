
### 一、先明确核心结论：正确执行顺序

先给你一个准确的执行链路（基于 Spring Boot 2.x/3.x）：




```plaintext
容器初始化Bean → @PostConstruct → InitializingBean.afterPropertiesSet() → 容器刷新完成 → ApplicationStartedEvent（应用启动完成） → CommandLineRunner/ApplicationRunner.run()
```

用代码验证的话，执行日志会是这样：


```plaintext
@PostConstruct 执行
afterPropertiesSet 执行
ApplicationStartedEvent 监听方法执行
run 方法执行（CommandLineRunner）
```

### 二、逐个拆解：含义、时机、使用场景

#### 1. @PostConstruct（最早执行）

- **本质**：JDK 自带的注解（不属于 Spring），标记在非静态方法上。

- **执行时机**：Bean 的属性注入完成后（依赖注入完成），但 Bean 还未完全初始化时执行。

- 特点 ：

    - 优先级最高，比 Spring 的`afterPropertiesSet`更早；
    - 每个 Bean 只会执行一次；
    - 不能传递参数，仅用于 Bean 自身的初始化逻辑（如初始化成员变量、加载本地配置）。

- 示例 ：

  ```java
  @Component
  public class InitDemo {
      @Value("${app.name}")
      private String appName;
  
      @PostConstruct
      public void init() {
          System.out.println("@PostConstruct 执行，appName=" + appName);
      }
  }
  ```



#### 2. InitializingBean.afterPropertiesSet ()（次早）

- **本质**：Spring 提供的接口，实现该接口需重写`afterPropertiesSet()`方法。

- **执行时机**：在`@PostConstruct`之后执行，同样是 Bean 属性注入完成后。

- 特点 ：

    - 属于 Spring 原生机制，比`@PostConstruct`晚，但比应用启动事件早；
    - 与`@PostConstruct`作用类似，都是 Bean 级别的初始化；
    - 推荐优先用`@PostConstruct`（无需实现接口，更灵活）。

- 示例 ：





  ```java
  @Component
  public class AfterPropertiesSetDemo implements InitializingBean {
      @Override
      public void afterPropertiesSet() throws Exception {
          System.out.println("afterPropertiesSet 执行");
      }
  }
  ```



#### 3. ApplicationStartedEvent（应用启动完成）

- **本质**：Spring Boot 的应用事件（属于 ApplicationEvent 体系）。

- **执行时机**：Spring 容器完全刷新（所有 Bean 初始化完成）、应用上下文启动完成后，在`run`方法之前。

- 特点 ：

    - 属于 “应用级” 事件，而非 “Bean 级”，表示整个应用已启动（但还未处理业务请求）；
    - 可通过`@EventListener`监听，适合做应用启动后的全局初始化（如加载缓存、注册监听器）。

- 示例 ：







  ```java
  @Component
  public class StartedEventDemo {
      @EventListener(ApplicationStartedEvent.class)
      public void onApplicationStarted(ApplicationStartedEvent event) {
          System.out.println("ApplicationStartedEvent 执行");
      }
  }
  ```



#### 4. CommandLineRunner/ApplicationRunner.run ()（最晚）

- **本质**：Spring Boot 提供的接口，实现后重写`run()`方法。

- **执行时机**：应用完全启动（包括内置容器如 Tomcat 启动）、`ApplicationStartedEvent`之后，是服务启动后最后执行的初始化逻辑。

- 特点 ：

    - 优先级最低，执行完后应用就可以接收外部请求了；
    - `CommandLineRunner`可接收命令行参数（String []），`ApplicationRunner`可接收封装后的`ApplicationArguments`（更友好）；
    - 适合做应用启动后的业务初始化（如预热接口、执行定时任务、检查依赖服务是否可用）。

- 示例 ：





  ```java
  @Component
  public class RunnerDemo implements CommandLineRunner {
      @Override
      public void run(String... args) throws Exception {
          System.out.println("CommandLineRunner.run 执行，参数=" + Arrays.toString(args));
      }
  }
  ```



### 三、完整验证代码（可直接运行）






```java
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;

@SpringBootApplication
public class StartOrderDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(StartOrderDemoApplication.class, args);
    }

    @Component
    static class Init1 implements InitializingBean {
        @Value("${app.name:demo}")
        private String appName;

        @PostConstruct
        public void postConstruct() {
            System.out.println("1. @PostConstruct 执行，appName=" + appName);
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            System.out.println("2. afterPropertiesSet 执行");
        }
    }

    @Component
    static class Init2 {
        @EventListener(ApplicationStartedEvent.class)
        public void onStarted(ApplicationStartedEvent event) {
            System.out.println("3. ApplicationStartedEvent 执行");
        }
    }

    @Component
    static class Init3 implements ApplicationRunner {
        @Override
        public void run(ApplicationArguments args) throws Exception {
            System.out.println("4. ApplicationRunner.run 执行，参数=" + Arrays.toString(args.getSourceArgs()));
        }
    }
}
```

**运行输出**：





```plaintext
1. @PostConstruct 执行，appName=demo
2. afterPropertiesSet 执行
3. ApplicationStartedEvent 执行
4. ApplicationRunner.run 执行，参数=[]
```

### 四、使用场景总结（核心）

| 方法 / 事件             | 执行时机               | 核心使用场景                                         |
| ----------------------- | ---------------------- | ---------------------------------------------------- |
| @PostConstruct          | Bean 属性注入后        | Bean 自身的初始化（如初始化成员变量、加载本地配置）  |
| afterPropertiesSet      | @PostConstruct 之后    | 兼容老的 Spring 代码，功能同 @PostConstruct          |
| ApplicationStartedEvent | 容器刷新完成、应用启动 | 全局初始化（如加载缓存、注册全局监听器）             |
| CommandLineRunner.run   | 应用完全启动、可接请求 | 业务初始化（如预热接口、检查依赖服务、执行启动任务） |

### 总结

1. **正确执行顺序**：`@PostConstruct` → `afterPropertiesSet` → `ApplicationStartedEvent` → `run`（你之前把前两个顺序搞反了，这是核心纠正点）；
2. **优先级逻辑**：Bean 级初始化（@PostConstruct/afterPropertiesSet）早于应用级初始化（事件 / Runner）；
3. **选型原则**：初始化 Bean 内部逻辑用`@PostConstruct`，应用全局初始化用`ApplicationStartedEvent`，业务启动任务用`CommandLineRunner/ApplicationRunner`。