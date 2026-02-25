以下是 **MyBatis 核心原理、MyBatis-Plus（MP）增强原理、优化手段及二者核心区别** 的结构化解答，聚焦面试高频考点，兼顾原理深度与实用场景：

### 一、MyBatis 核心原理

MyBatis 是一款 **半 ORM 框架**，核心是「解耦 SQL 与 Java 代码」，通过配置映射关系将 SQL 执行与结果封装自动化，底层依赖「动态代理 + 反射 + XML 解析」实现。

#### 1. 核心架构（从下到上）

| 层级       | 核心组件                               | 作用                                                        |
| ---------- | -------------------------------------- | ----------------------------------------------------------- |
| 基础支持层 | 数据源、事务管理、反射工具、类型处理器 | 提供底层技术支撑（如数据源连接池、SQL 参数 / 结果类型转换） |
| 核心处理层 | SqlSession、Executor、StatementHandler | 执行 SQL 的核心流程（SQL 解析、参数绑定、结果映射）         |
| 映射层     | Mapper 接口、XML 映射文件、注解        | 定义 SQL 与 Java 方法的映射关系（如``标签、`@Select`注解）  |
| 接口层     | SqlSessionFactory、SqlSession          | 对外提供 API（如获取 SqlSession、执行 SQL）                 |

#### 2. 核心执行流程（以 XML 映射为例）

1. **初始化阶段**：

    - 加载 MyBatis 配置文件（`mybatis-config.xml`）和 Mapper XML 文件（如`UserMapper.xml`）；
    - 解析 XML 中的`/`等标签，封装为`MappedStatement`（存储 SQL 语句、参数类型、结果类型等），存入`Configuration`（全局配置对象）；
    - 通过`SqlSessionFactoryBuilder`构建`SqlSessionFactory`（依赖`Configuration`）。

2. **运行阶段**：

    - 调用`SqlSessionFactory.openSession()`获取`SqlSession`（默认非线程安全，需手动关闭）；

    - 调用`SqlSession.getMapper(UserMapper.class)`，通过 **JDK 动态代理** 生成`UserMapper`接口的代理对象（`MapperProxy`）；

    - 调用代理对象的方法（如```userMapper.selectById(1)```），```MapperProxy```会：

        - 根据接口方法名 + 全类名，从`Configuration`中找到对应的`MappedStatement`；

        - 委托```Executor```（执行器，默认```SimpleExecutor```）执行 SQL：

            1. `ParameterHandler`：将 Java 参数绑定到 SQL 的`?`占位符（类型转换依赖`TypeHandler`）；
            2. `StatementHandler`：创建 JDBC 的`Statement/PreparedStatement`，执行 SQL；
            3. `ResultSetHandler`：将 JDBC 的`ResultSet`结果集，通过反射映射为 Java 对象（匹配字段名 / 别名）；

    - 返回映射后的 Java 对象，关闭`SqlSession`（提交 / 回滚事务）。

#### 3. 核心设计亮点

- **动态 SQL**：通过`//`等标签，在 XML 中拼接动态 SQL（底层通过`SqlNode`解析、`DynamicContext`拼接）；
- **SQL 与代码分离**：SQL 集中在 XML / 注解中，便于优化和维护（如 DBA 直接修改 XML 中的 SQL）；
- **轻量级**：不侵入业务代码，仅通过映射关系关联，灵活度高（支持原生 SQL、存储过程）。

### 二、MyBatis-Plus（MP）核心原理

MP 是 **MyBatis 的增强工具**，核心定位是「简化 MyBatis 开发」，遵循「不改变 MyBatis 原有功能」的原则，通过「注解 + 代码生成 + 内置 CRUD」减少重复编码，底层依赖「MyBatis 扩展点 + 动态 SQL 生成」。

#### 1. 核心增强原理

MP 的核心是「自动生成 CRUD SQL」，无需手动写 XML / 注解，底层流程：

1. **继承 BaseMapper**：业务 Mapper 接口继承`BaseMapper`（如`UserMapper extends BaseMapper`），`BaseMapper`内置了`selectById`、`insert`、`updateById`等 20+CRUD 方法；

2. **注解绑定表 / 字段**：通过`@TableName`（绑定表名）、`@TableId`（主键）、`@TableField`（普通字段）注解，建立 Java 实体与数据库表的映射关系（替代 MyBatis 的 XML 映射）；

3. 动态生成 SQL ：

    - MP 启动时，扫描所有继承`BaseMapper`的接口，通过`MybatisMapperAnnotationBuilder`解析注解，生成对应的`MappedStatement`（如`selectById`对应`select * from user where id = ?`）；
    - 对于复杂查询，通过`QueryWrapper`（条件构造器）拼接动态条件（如`queryWrapper.eq("name", "张三").ge("age", 18)`），底层通过`SqlCondition`生成对应的 SQL 片段（`where name = ? and age >= ?`）；

4. **集成 MyBatis 流程**：MP 生成的`MappedStatement`会存入 MyBatis 的`Configuration`，后续执行流程与 MyBatis 完全一致（通过`SqlSession`+ 动态代理执行）。

#### 2. 核心功能依赖的 MyBatis 扩展点

MP 没有改写 MyBatis 源码，而是通过 MyBatis 的原生扩展点实现增强：

- `MapperRegistry`：MP 重写了`MapperRegistry`的`addMapper`方法，扫描`BaseMapper`接口并生成 CRUD 的`MappedStatement`；
- `StatementHandler`：扩展`StatementHandler`实现分页插件（`PaginationInterceptor`），自动拼接`limit`语句（或数据库方言对应的分页 SQL）；
- `ParameterHandler`：扩展参数处理，支持`QueryWrapper`、`UpdateWrapper`等条件构造器的参数绑定。

### 三、MyBatis 与 MP 的优化手段

#### 1. MyBatis 自身优化

- SQL 优化 ：

    - 避免`select *`，只查需要的字段（减少 IO 和结果映射开销）；
    - 合理使用索引，避免 SQL 中的函数操作索引列（如`where date(create_time) = '2024'`）；
    - 批量操作使用`foreach`标签（`insert into user(id,name) values ...`），减少 SQL 执行次数。

- 配置优化：

    - 开启二级缓存（``标签），缓存查询结果（适合只读 / 少写数据）；
    - 配置`fetchSize`（批量读取行数）、`timeout`（SQL 超时时间）；
    - 使用连接池（如 Druid），配置合理的连接数（`maxActive`）、空闲时间（`maxIdle`）。

- 代码优化：

    - 复用`SqlSession`（如 Spring 整合后通过`@Autowired`注入 Mapper，Spring 管理 SqlSession 生命周期）；
    - 使用`TypeHandler`处理特殊类型（如枚举、JSON），避免手动类型转换；
    - 动态 SQL 中避免冗余``判断，使用`/`标签自动处理多余的`and/or`、逗号。

#### 2. MP 额外优化（基于 MyBatis 之上）

- 减少重复编码 ：

    - 内置 CRUD 方法，无需手动写 XML / 注解（如`insert`、`selectBatchIds`）；
    - 代码生成器（AutoGenerator）：根据数据库表自动生成实体类、Mapper 接口、XML 文件（支持自定义模板），减少 80% 重复工作。

- 查询效率优化 ：

    - 分页插件（`PaginationInnerInterceptor`）：自动拼接分页 SQL，支持 MySQL、Oracle 等多数据库方言，无需手动写`limit`；
    - 条件构造器（`QueryWrapper`）：类型安全的条件拼接（避免 SQL 语法错误），支持 Lambda 表达式（`queryWrapper.lambda().eq(User::getName, "张三")`），防止字段名写错；
    - 批量操作增强：`IService`接口（如`UserService extends IService`）内置`saveBatch`、`updateBatchById`等批量方法，底层优化为批量 SQL 执行（减少连接开销）。

- 功能增强优化 ：

    - 逻辑删除：通过`@TableLogic`注解标记逻辑删除字段（如`is_deleted`），MP 自动在查询时拼接`where is_deleted = 0`，删除时执行`update ... set is_deleted = 1`（无需手动处理）；
    - 自动填充：通过`@TableField(fill = FieldFill.INSERT)`标记创建时间、更新时间字段，MP 通过`MetaObjectHandler`自动填充值（如`new Date()`），无需手动设置；
    - 乐观锁：通过`@Version`注解标记版本字段，MP 自动在更新时拼接`where version = ?`，并更新版本号（`version = version + 1`），避免并发更新冲突。

### 四、MyBatis 与 MP 的核心区别

| 维度     | MyBatis                                                      | MyBatis-Plus（MP）                                           |
| -------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 定位     | 半 ORM 框架，专注 SQL 与 Java 代码解耦                       | MyBatis 增强工具，专注简化开发（不改变 MyBatis 核心）        |
| 开发效率 | 需手动写 XML / 注解（CRUD、动态 SQL）                        | 内置 CRUD + 代码生成器，无需手动写基础 SQL                   |
| 映射方式 | 依赖 XML 标签（``）或注解（`@Select`）                       | 依赖注解（`@TableName`、`@TableId`）+ 接口继承`BaseMapper`   |
| 动态 SQL | 需手动写`/`等标签                                            | 提供`QueryWrapper`条件构造器，支持 Lambda 表达式，无需手动拼接 |
| 功能支持 | 基础 SQL 执行、结果映射、二级缓存                            | 包含 MyBatis 所有功能，额外支持：逻辑删除、自动填充、乐观锁、分页插件、批量操作 |
| 学习成本 | 中等（需掌握 XML 配置、动态 SQL 语法）                       | 低（基于 MyBatis，只需学习 MP 的注解和条件构造器）           |
| 灵活性   | 高（支持原生 SQL、存储过程、复杂 SQL 场景）                  | 中等（基础场景无需配置，复杂 SQL 仍需手动写 XML / 注解）     |
| 适用场景 | 复杂 SQL 场景（如多表关联、自定义函数）、对灵活性要求高的项目 | 快速开发场景（如后台管理系统）、CRUD 为主的项目、需要减少重复编码的团队 |

### 总结

- **MyBatis** 是「基础核心」，灵活度高，适合需要精细控制 SQL 的场景，但开发效率较低；
- **MP** 是「增强工具」，基于 MyBatis 实现，简化了 CRUD 开发，提供了大量实用功能（分页、逻辑删除等），开发效率极高，且兼容 MyBatis 的所有原生功能（复杂 SQL 仍可手动写 XML）；
- 实际开发中，几乎所有使用 MyBatis 的项目都会搭配 MP（或类似增强工具），既保留 MyBatis 的灵活性，又提升开发效率。
