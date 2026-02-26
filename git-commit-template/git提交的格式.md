# Git Commit Message 规范模板

## 格式规范

```
<type>(<scope>): <subject>

<body>

<footer>
```

---

## 一、Type 类型说明

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| `docs` | 文档更新 | 新增/修改 Markdown 文档 |
| `feat` | 新增内容 | 添加新的知识点文章 |
| `fix` | 修正错误 | 修复文档中的错误内容 |
| `refactor` | 重构优化 | 整理、重组文档结构 |
| `style` | 格式调整 | 语法格式、标点、错别字 |
| `chore` | 维护操作 | 配置更新、依赖调整 |

---

## 二、Scope 范围（根据你的项目）

### 文档分类
- `Java基础` - Java 基础相关
- `Java并发` - 并发编程相关
- `JVM` - 虚拟机相关
- `MySQL` - 数据库相关
- `Redis` - 缓存相关
- `Linux` - Linux 系统相关
- `Maven` - 构建工具相关
- `Git` - Git 相关

### 通用范围
- `readme` - README 文件
- `config` - 配置文件
- `workflow` - 工作流配置

---

## 三、Subject 主题规则

1. **动词开头**，使用现在时态
2. **首字母小写**（除非专有名词）
3. **不超过 50 个字符**
4. **结尾不加句号**

### 常用动词
- 添加：add
- 更新：update/modify
- 修正：fix
- 补充：supplement
- 完善：improve
- 整理：organize
- 梳理：sort
- 优化：optimize
- 解释：explain
- 分析：analyze

---

## 四、Body 正文规则

- 说明 **what** 和 **why**，而不是 how
- 每行不超过 72 个字符
- 可以分为多行

---

## 五、Footer 脚注（可选）

- 关联 Issue：`Closes #123`
- 破坏性变更：`BREAKING CHANGE: ...`

---

## 六、常用模板示例

### 1. 新增文档
```
docs(Java并发): 添加 ThreadLocal 内存泄漏相关文档

补充 ThreadLocal 到 TTL 的演进过程分析
```

### 2. 新增知识点
```
feat(JVM): 添加垃圾回收器调优实践文档

新增 G1 和 CMS 垃圾回收器的对比分析
```

### 3. 补充内容
```
docs(MySQL): 补充索引失效场景分析

增加联合索引最左前缀原则的详细说明
```

### 4. 修正错误
```
fix(Java基础): 修正 ArrayList 初始化方式的错误描述

更正构造方法参数说明
```

### 5. 整理重构
```
refactor(Redis): 整理 Redis 缓存相关知识点

按数据类型重新分类文档结构
```

### 6. 格式调整
```
style(Java并发): 统一并发编程章节的文档格式

规范化代码示例的缩进和注释风格
```

### 7. 配置更新
```
chore(config): 添加 Git hooks 配置

集成 commit message 规范检查
```

---

## 七、快速参考

```
# 格式速查
docs(类别): 动词 + 文档主题

# 示例
docs(Java并发): 添加 ThreadLocal 内存泄漏相关文档
docs(JVM): 补充对象创建过程分析
feat(Redis): 新增分布式锁实现方案
fix(MySQL): 修正 B+树结构描述错误
```

---

## 八、使用建议

1. **保持简洁**：一句话能说清的，不要啰嗦
2. **分类清晰**：准确选择 type 和 scope
3. **动词精准**：准确表达文档变更意图
4. **内容相关**：subject 和 body 要对应
