Maven 的生命周期是 **“标准化的项目构建流程”**，核心价值是 **统一项目构建步骤、消除构建差异**—— 无论什么 Java 项目（Web 项目、Java 库、Spring Boot 项目），都能通过相同的 Maven 命令（如 `mvn clean package`）完成构建，无需关注底层细节。

Maven 生命周期的核心特点：**“阶段（Phase）串行执行、插件（Plugin）绑定实现具体功能”**，即生命周期定义 “要做什么”，插件定义 “怎么做”。

## 一、Maven 三大核心生命周期（独立且并行）

Maven 有 3 个完全独立的生命周期，彼此无依赖，可单独执行：

| 生命周期名称                          | 核心作用                                   | 典型场景                   |
| ------------------------------------- | ------------------------------------------ | -------------------------- |
| **Clean Lifecycle**（清理生命周期）   | 清理项目构建产物（如 `target` 目录）       | 构建前清理旧产物，避免冲突 |
| **Default Lifecycle**（默认生命周期） | 项目核心构建流程（编译、测试、打包、部署） | 日常开发、打包、发布项目   |
| **Site Lifecycle**（站点生命周期）    | 生成项目文档站点（API 文档、测试报告）     | 项目文档归档、团队协作共享 |

> 关键：每个生命周期内部的 “阶段（Phase）” 是 **串行执行** 的（执行后面的阶段，会自动执行前面所有阶段）；不同生命周期之间是 **并行独立** 的（执行默认生命周期，不会影响清理或站点生命周期）。

## 二、核心生命周期详解（重点：Default 生命周期）

### 1. Clean Lifecycle（清理生命周期）

只有 3 个阶段，用于清理构建产物：

| 阶段名称     | 作用描述                                         |
| ------------ | ------------------------------------------------ |
| `pre-clean`  | 清理前的准备工作（如备份旧产物，默认无绑定插件） |
| `clean`      | 核心清理：删除 `target` 目录（构建产物存放目录） |
| `post-clean` | 清理后的收尾工作（如删除备份，默认无绑定插件）   |

> 常用命令：`mvn clean`（执行 `clean` 阶段，会自动执行 `pre-clean` 阶段）。

### 2. Default Lifecycle（默认生命周期）

Maven 最核心的生命周期，包含 23 个阶段，覆盖从 “编译源码” 到 “部署到仓库” 的全流程。日常开发中，仅需关注以下常用阶段（按执行顺序排列）：

| 阶段名称                  | 作用描述                                                     | 关键细节                                                     |
| ------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| `validate`                | 验证项目配置是否有效（如 POM 文件是否完整、依赖是否存在）    | 构建的第一步，提前发现配置错误                               |
| `initialize`              | 初始化构建环境（如创建临时目录、加载配置文件）               | 默认无绑定插件，需自定义插件时使用                           |
| `generate-sources`        | 生成源代码（如 MyBatis 逆向工程生成 Mapper 接口）            | 需绑定插件（如 `mybatis-generator-maven-plugin`）            |
| `process-sources`         | 处理源代码（如过滤资源文件中的占位符 `${xxx}`）              | 资源文件放在 `src/main/resources` 目录                       |
| `generate-resources`      | 生成资源文件（如动态生成配置文件）                           | 较少用到，需自定义插件                                       |
| `process-resources`       | 复制资源文件到编译目录（`target/classes`）                   | 自动执行，无需手动干预                                       |
| `compile`                 | 编译 Java 源码（`src/main/java`）到 `target/classes`         | 核心阶段，依赖 `maven-compiler-plugin`（默认绑定）           |
| `process-classes`         | 处理编译后的字节码（如类增强、字节码修改）                   | 需绑定 AOP 插件（如 `aspectj-maven-plugin`）                 |
| `generate-test-sources`   | 生成测试源代码（如自动生成测试用例）                         | 较少用到，需自定义插件                                       |
| `process-test-sources`    | 处理测试资源文件（`src/test/resources`）                     | 复制测试资源到 `target/test-classes`                         |
| `generate-test-resources` | 生成测试资源文件                                             | 较少用到                                                     |
| `process-test-resources`  | 复制测试资源到编译目录                                       | 自动执行                                                     |
| `test-compile`            | 编译测试源码（`src/test/java`）到 `target/test-classes`      | 依赖 `maven-compiler-plugin`，与主源码编译共用插件配置       |
| `process-test-classes`    | 处理测试字节码                                               | 较少用到                                                     |
| `test`                    | 执行单元测试（如 JUnit、TestNG 测试用例）                    | 依赖 `maven-surefire-plugin`，默认不执行失败的测试用例（可配置跳过） |
| `prepare-package`         | 打包前的准备工作（如优化字节码、整理资源）                   | 部分插件会绑定（如 Spring Boot 打包前的依赖整理）            |
| `package`                 | 打包项目为指定格式（如 JAR、WAR、EAR）                       | 核心打包阶段：- Java 库默认打 JAR 包（`maven-jar-plugin`）；- Web 项目打 WAR 包（`maven-war-plugin`）；- Spring Boot 打可执行 JAR 包（`spring-boot-maven-plugin`） |
| `pre-integration-test`    | 集成测试前准备（如启动测试环境、数据库）                     | 集成测试专用，需绑定插件（如 `tomcat-maven-plugin` 启动测试服务器） |
| `integration-test`        | 执行集成测试（区别于单元测试，需依赖外部环境）               | 可通过 `skipITs` 参数跳过（`mvn package -DskipITs`）         |
| `post-integration-test`   | 集成测试后清理（如关闭测试环境、删除测试数据）               | 集成测试专用                                                 |
| `verify`                  | 验证打包产物的有效性（如检查 JAR 包完整性、测试报告是否通过） | 发布前的校验，确保产物可用                                   |
| `install`                 | 将打包产物（JAR/WAR）安装到 **本地仓库**（默认 `~/.m2/repository`） | 关键阶段：本地多模块项目依赖、本地调试时使用（其他项目可依赖本地仓库的产物） |
| `deploy`                  | 将打包产物部署到 **远程仓库**（如公司私服 Nexus）            | 团队协作、项目发布时使用（需配置远程仓库地址和权限）         |

> 核心命令与执行逻辑：
>
> - `mvn compile`：执行 `compile` 阶段，会自动执行前面的 `validate`、`initialize`、`process-resources` 等所有阶段；
> - `mvn test`：执行 `test` 阶段，会自动执行到 `test-compile` 之前的所有阶段（编译主源码 + 测试源码，再执行测试）；
> - `mvn package`：执行 `package` 阶段，会自动执行到 `prepare-package` 之前的所有阶段（编译、测试、打包）；
> - `mvn install`：执行 `install` 阶段，会自动执行到 `package` 之前的所有阶段（打包后安装到本地仓库）；
> - `mvn deploy`：执行 `deploy` 阶段，会自动执行到 `verify` 之前的所有阶段（验证后部署到远程仓库）。

### 3. Site Lifecycle（站点生命周期）

用于生成项目文档站点，常用阶段：

| 阶段名称      | 作用描述                                                    |
| ------------- | ----------------------------------------------------------- |
| `pre-site`    | 生成站点前的准备工作（如整理文档资源）                      |
| `site`        | 核心阶段：生成 HTML 格式的项目文档（如 API 文档、测试报告） |
| `post-site`   | 生成站点后的收尾工作（如压缩文档、上传准备）                |
| `site-deploy` | 将生成的站点部署到远程服务器（如公司文档服务器）            |

> 常用命令：
>
> - `mvn site`：生成站点文档（默认在 `target/site` 目录）；
> - `mvn site-deploy`：生成并部署站点到远程服务器。

## 三、生命周期的核心机制：阶段（Phase）与插件（Plugin）的绑定

Maven 生命周期的 “阶段（Phase）” 本身 **不做任何实际工作**，只是 “流程节点标记”；真正的构建逻辑由 **插件（Plugin）** 实现 —— 每个阶段会绑定一个或多个插件的 “目标（Goal）”，执行阶段时，会触发绑定的插件目标。

### 1. 绑定方式分类

- 默认绑定

  ：Maven 内置的绑定关系（无需手动配置），例如：

    - `compile` 阶段绑定 `maven-compiler-plugin:compile`（编译主源码）；
    - `test` 阶段绑定 `maven-surefire-plugin:test`（执行单元测试）；
    - `package` 阶段绑定 `maven-jar-plugin:jar`（打 JAR 包）。

- 自定义绑定

  ：用户根据需求，将插件目标绑定到指定阶段，例如：

    - 将 `mybatis-generator-maven-plugin:generate` 绑定到 `generate-sources` 阶段（编译前生成 Mapper 代码）；
    - 将 `jacoco-maven-plugin:report` 绑定到 `test` 阶段（执行测试后生成覆盖率报告）。

### 2. 自定义绑定示例（POM.xml 配置）

例如，将 MyBatis 逆向工程插件绑定到 `generate-sources` 阶段：







```xml
<build>
  <plugins>
    <!-- MyBatis 逆向工程插件 -->
    <plugin>
      <groupId>org.mybatis.generator</groupId>
      <artifactId>mybatis-generator-maven-plugin</artifactId>
      <version>1.4.0</version>
      <executions>
        <execution>
          <!-- 绑定到 generate-sources 阶段 -->
          <phase>generate-sources</phase>
          <goals>
            <!-- 执行插件的 generate 目标 -->
            <goal>generate</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

执行 `mvn compile` 时，会自动触发 `generate-sources` 阶段，进而执行 MyBatis 逆向工程插件的 `generate` 目标，生成 Mapper 代码。

## 四、常用 Maven 命令与生命周期映射

日常开发中，高频使用的命令本质是 “执行生命周期的某个阶段”，以下是核心命令对应关系：

| Maven 命令          | 对应生命周期阶段                | 执行效果（自动执行前面所有阶段）                          |
| ------------------- | ------------------------------- | --------------------------------------------------------- |
| `mvn clean`         | Clean: clean                    | 清理 `target` 目录                                        |
| `mvn compile`       | Default: compile                | 编译 `src/main/java` 源码到 `target/classes`              |
| `mvn test`          | Default: test                   | 编译测试源码 + 执行单元测试（`src/test/java`）            |
| `mvn package`       | Default: package                | 编译 + 测试 + 打包（生成 JAR/WAR 包到 `target` 目录）     |
| `mvn install`       | Default: install                | 编译 + 测试 + 打包 + 安装到本地仓库（`~/.m2/repository`） |
| `mvn deploy`        | Default: deploy                 | 编译 + 测试 + 打包 + 验证 + 部署到远程仓库                |
| `mvn clean package` | Clean: clean + Default: package | 先清理旧产物，再执行打包（常用：避免旧包干扰）            |
| `mvn clean install` | Clean: clean + Default: install | 先清理，再打包并安装到本地仓库（多模块项目依赖时常用）    |
| `mvn site`          | Site: site                      | 生成项目文档站点                                          |

> 技巧：跳过测试的命令：`mvn package -DskipTests`（不执行测试用例，但编译测试源码）或 `mvn package -Dmaven.test.skip=true`（不编译测试源码，也不执行测试）。

## 五、关键注意点

1. **生命周期的串行执行**：执行后面的阶段，必然执行前面所有阶段（如 `mvn install` 会自动执行 `compile`、`test`、`package` 等阶段）；
2. **插件的可配置性**：默认绑定的插件可通过 POM.xml 自定义配置（如 `maven-compiler-plugin` 指定 JDK 版本：`<source>1.8</source><target>1.8</target>`）；
3. **多模块项目的生命周期**：父模块执行 `mvn package` 时，会自动按依赖顺序执行所有子模块的 `package` 阶段（先构建被依赖的子模块）；
4. **生命周期与阶段的区别**：生命周期是 “流程集合”（如 Default），阶段是 “流程中的节点”（如 compile），命令直接指向 “阶段” 而非 “生命周期”。

## 六、总结

Maven 生命周期的核心价值是 **“标准化构建流程”**：

- 3 大生命周期独立并行，Default 生命周期是核心；
- 阶段（Phase）定义 “做什么”，插件（Plugin）定义 “怎么做”；
- 日常开发只需掌握 `clean`、`compile`、`test`、`package`、`install`、`deploy` 等高频命令，即可完成绝大多数构建需求。

理解生命周期后，能轻松解决 “为什么执行 `mvn install` 会自动编译和测试”“如何自定义构建流程” 等问题，同时让不同项目的构建逻辑保持一致，降低团队协作成本。