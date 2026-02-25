在 Linux 中查看日志文件的指令非常丰富，核心是根据**日志的状态**（静态文件 / 实时滚动）和**查看需求**（全文 / 关键词 / 行数）选择合适的命令。以下是高频使用的指令及场景：

### 一、基础查看：静态日志文件（已生成的日志）

适用于查看不再更新的日志文件，或一次性读取日志内容。

#### 1. `cat` —— 一次性打印文件全部内容

**用法**：`cat [选项] 日志文件`







```bash
# 查看单个日志
cat app.log

# 查看多个日志（合并输出）
cat app.log app-error.log

# 显示行号（排查问题时定位行数）
cat -n app.log
```

**优点**：简单直接；**缺点**：大文件会瞬间刷屏，无法分页查看。

#### 2. `more`/`less` —— 分页查看大日志

`more` 只能向下翻页，`less` 支持上下翻页、搜索，功能更强大（推荐）。**用法**：











```bash
# more 分页查看（空格翻页，q退出）
more app.log

# less 分页查看（↑↓翻行，空格翻页，q退出）
less app.log
```

`less` 核心优势：

- 搜索关键词：在 `less` 中输入 `/关键词`（如 `/ERROR`），按 `n` 下一个匹配项，`N` 上一个；
- 实时刷新：输入 `F` 可切换到实时监控模式（类似 `tail -f`），按 `Ctrl+C` 退出。

#### 3. `head`/`tail` —— 查看开头 / 结尾的指定行数

- `head`：默认查看文件前 10 行










  ```bash
  # 查看前10行（默认）
  head app.log
  # 查看前20行
  head -n 20 app.log
  ```



- `tail`：默认查看文件后 10 行（最常用的日志查看指令之一）







  ```bash
  # 查看后10行（默认）
  tail app.log
  # 查看后50行
  tail -n 50 app.log
  ```



### 二、实时监控：动态滚动的日志（程序正在输出的日志）

适用于实时查看程序运行时的日志输出（如后端服务的实时日志）。

#### 1. `tail -f` —— 实时跟踪日志新增内容

**核心用法**，程序新增的日志会实时打印到终端：






```bash
# 实时跟踪 app.log 的新增内容（默认后10行）
tail -f app.log

# 实时跟踪并显示行号
tail -fn 20 app.log

# 跟踪多个日志文件
tail -f app.log app-error.log
```

**退出方式**：`Ctrl+C`。

#### 2. `tail -F` —— 日志文件被切割后自动重新跟踪

日志文件通常会按大小 / 时间切割（如 `app.log` → `app.log.1`），`-f` 会失效，`-F` 会自动识别新文件并继续跟踪：










```bash
tail -F app.log
```

### 三、高级筛选：按关键词 / 条件过滤日志

查看日志时，通常需要筛选**错误信息**（如 `ERROR`/`Exception`）或特定关键词，结合管道符 `|` 和过滤命令实现。

#### 1. `grep` —— 按关键词筛选日志

`grep` 是 Linux 文本过滤的核心命令，支持正则表达式。







```bash
# 从 app.log 中筛选包含 ERROR 的行
grep "ERROR" app.log

# 筛选 ERROR 行并显示行号
grep -n "ERROR" app.log

# 忽略大小写（如匹配 error/Error/ERROR）
grep -i "error" app.log

# 显示匹配行的前后5行（上下文，排查异常栈很有用）
grep -C 5 "Exception" app.log
# -A 5：显示匹配行及后5行；-B 5：显示匹配行及前5行
```

#### 2. 组合指令：实时监控并筛选关键词

最常用的生产环境排查组合：实时跟踪日志，并只显示包含指定关键词的内容。








```bash
# 实时跟踪 app.log，只显示 ERROR 相关行
tail -f app.log | grep "ERROR"

# 实时跟踪，显示 ERROR 行及前后3行上下文
tail -f app.log | grep -C 3 "ERROR"
```

#### 3. `awk` —— 按列 / 复杂条件筛选

适用于日志格式规整（如空格 / 制表符分隔字段）的场景，按列提取内容。







```bash
# 假设日志格式：[时间] [级别] [内容]，提取所有 ERROR 级别的时间和内容
awk '$2 == "ERROR" {print $1, $3}' app.log
```

### 四、其他实用指令

1. `wc` —— 统计日志行数 / 字数



常用于统计错误日志的数量：








   ```bash
   # 统计 app.log 中 ERROR 行的数量
   grep "ERROR" app.log | wc -l
   ```



2. `tee` —— 查看日志的同时保存到文件实时监控日志时，同时将内容保存到新文件：




   ```bash
   tail -f app.log | grep "ERROR" | tee error.log
   ```



### 五、常见场景示例

| 需求场景                   | 推荐指令                                                 |      |
| -------------------------- | -------------------------------------------------------- | ---- |
| 快速查看日志最后 100 行    | `tail -n 100 app.log`                                    |      |
| 实时监控并筛选异常信息     | `tail -f app.log                | grep -C 5 "Exception"` |      |
| 分页查看大日志并搜索关键词 | `less app.log` → 输入 `/关键词`                          |      |
| 统计日志中 ERROR 的数量    | `grep "ERROR" app.log           | wc -l`                 |      |
| 跟踪切割后的日志文件       | `tail -F app.log`                                        |      |

### 总结

- 静态小日志 → `cat`；静态大日志 → `less`；
- 实时日志 → `tail -f`/`tail -F`；
- 筛选关键词 → `grep`，组合 `tail -f` 是生产环境排查问题的黄金搭档。

需要我帮你整理一份**Linux 日志排查常用指令速查表**吗？方便你面试或工作中快速查阅。

需要

# Linux 日志排查常用指令速查表

| 操作类型               | 指令          | 核心用法                                                     | 适用场景                                |
| ---------------------- | ------------- | ------------------------------------------------------------ | --------------------------------------- |
| **一次性查看全部内容** | `cat`         | `cat app.log``cat -n app.log`（显示行号）                    | 小日志文件，快速浏览全部内容            |
| **分页查看大日志**     | `less`        | `less app.log`交互操作：↑↓ 翻行 / 空格翻页 / `q` 退出`/关键词` 搜索 / `n` 下一个匹配项`F` 切换实时监控 | 大日志文件，需要分页 / 搜索             |
| **查看开头内容**       | `head`        | `head app.log`（默认前 10 行）`head -n 20 app.log`（前 20 行） | 查看日志头部的配置 / 启动信息           |
| **查看结尾内容**       | `tail`        | `tail app.log`（默认后 10 行）`tail -n 50 app.log`（后 50 行） | 查看最新产生的日志                      |
| **实时跟踪日志**       | `tail -f`     | `tail -f app.log``tail -fn 30 app.log`（实时显示后 30 行）   | 实时监控程序运行日志（无文件切割场景）  |
| **跟踪切割后的日志**   | `tail -F`     | `tail -F app.log`                                            | 日志按大小 / 时间切割后，自动跟踪新文件 |
| **关键词筛选**         | `grep`        | `grep "ERROR" app.log`（筛选 ERROR 行）`grep -n "Exception" app.log`（显示行号）`grep -i "error" app.log`（忽略大小写）`grep -C 5 "WARN" app.log`（显示匹配行前后 5 行） | 精准定位错误、警告等关键日志            |
| **实时筛选关键词**     | `tail + grep` | `tail -f app.log | grep "ERROR"``tail -f app.log \| grep -C 3 "Exception"` | 实时监控并只显示目标关键词日志          |
| **统计日志数量**       | `grep + wc`   | `grep "ERROR" app.log | wc -l`                               | 统计错误日志的总条数                    |
| **查看并保存日志**     | `tee`         | `tail -f app.log | grep "ERROR" | tee error.log`             | 实时查看的同时，将筛选结果保存到文件    |
| **按列提取内容**       | `awk`         | `awk '$2=="ERROR" {print $1,$3}' app.log`（提取第 1、3 列）  | 日志格式规整时，按字段筛选内容          |

### 高频组合指令（生产环境必备）

1. 实时监控并筛选异常栈：








```bash
tail -F app.log | grep -C 10 "java.lang.Exception"
```

1. 查看今天的 ERROR 日志并保存：






```bash
grep "$(date +%Y-%m-%d)" app.log | grep "ERROR" > today_error.log
```

1. 分页查看错误日志并搜索：







```bash
grep "ERROR" app.log | less
```