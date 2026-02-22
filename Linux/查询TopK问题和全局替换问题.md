
### 一、Linux 命令解决 TopK 问题（取前 K 个高频元素）

TopK 核心需求：统计文件 / 输入中元素的出现频率，按频率降序排列后取前 K 个（如统计日志中出现最多的 5 个 IP、高频错误码等）。**核心命令组合**：`sort（排序去重） + uniq -c（统计次数） + sort -nr（按次数降序） + head -n K（取前 K 个）`。

#### 1. 基础场景：统计文件中高频行（每行一个元素）

例如：统计 `access.log` 中出现频率最高的 3 个 IP（假设每行一个 IP）。











```bash
# 步骤拆解：
# 1. sort 排序：让相同 IP 相邻（uniq 仅能统计连续相同元素）
# 2. uniq -c：统计每个 IP 的出现次数（-c 表示 count）
# 3. sort -nr：按次数降序排列（-n 按数字排序，-r 逆序）
# 4. head -n 3：取前 3 个高频 IP
sort access.log | uniq -c | sort -nr | head -n 3
```

**输出示例**：









```plaintext
  120 192.168.1.100  # 出现 120 次
   89 192.168.1.101  # 出现 89 次
   56 192.168.1.102  # 出现 56 次
```

#### 2. 进阶场景：统计文件中特定字段的高频元素

例如：统计 `nginx.log` 中第 1 列（IP）出现最多的 5 个（日志格式：`IP 时间 请求 状态码`）。需先用 `awk` 提取目标字段，再按基础场景处理：




```bash
# 步骤：awk 提取第 1 列 → 统计频率 → 取前 5
awk '{print $1}' nginx.log | sort | uniq -c | sort -nr | head -n 5
```

#### 3. 特殊场景：忽略大小写统计高频词

例如：统计 `test.txt` 中高频单词，忽略大小写（如 "Hello" 和 "hello" 视为同一词）。需先统一大小写（`tr 'A-Z' 'a-z'` 转小写），再统计：


```bash
tr 'A-Z' 'a-z' < test.txt | sort | uniq -c | sort -nr | head -n 4
```

### 二、Linux 命令解决全局替换问题

全局替换核心需求：替换文件中所有匹配的字符串（如将所有 "old" 替换为 "new"，支持单文件、多文件、递归替换）。**常用工具**：`sed`（轻量，适合简单替换）、`awk`（灵活，适合复杂逻辑）、`perl`（支持正则增强）。

#### 1. 基础场景：单文件全局替换（sed）

```
sed` 替换语法：`sed -i 's/旧字符串/新字符串/g' 文件名
```

- `-i`：直接修改文件（若加 `-i.bak`，会生成备份文件 `文件名.bak`，避免误操作）；
- `s`：表示 substitute（替换）；
- `g`：表示 global（全局替换，不加则只替换每行第一个匹配）。

**示例 1**：将 `test.txt` 中所有 "apple" 替换为 "banana"，并备份原文件：



```bash
sed -i.bak 's/apple/banana/g' test.txt
```

**示例 2**：替换包含特殊字符的字符串（如替换 "/" 为 "_"，需用 `\` 转义特殊字符）：






```bash
# 将 "/home/user" 替换为 "/data/user"，/ 需转义为 \/
sed -i 's/\/home\/user/\/data\/user/g' config.conf
```

#### 2. 进阶场景：多文件全局替换

例如：将当前目录下所有 `.txt` 文件中的 "old" 替换为 "new"。用 `sed` 结合通配符，或用 `xargs` 批量处理：




```bash
# 方法 1：sed 直接用通配符（部分系统支持）
sed -i 's/old/new/g' *.txt

# 方法 2：find 找到所有 .txt 文件，用 xargs 传递给 sed（更通用）
find . -type f -name "*.txt" | xargs sed -i 's/old/new/g'
```

#### 3. 高级场景：递归替换 + 匹配特定行

例如：在 `/opt/project` 目录下，递归替换所有 `.java` 文件中 **包含 "System.out" 的行** 中的 "println" 为 "print"。用 `find` 递归找文件，`sed` 加行匹配条件（`/匹配行内容/s/旧/新/g`）：




```bash
find /opt/project -type f -name "*.java" -exec sed -i '/System.out/s/println/print/g' {} +
```

- `/System.out/`：表示仅处理包含 "System.out" 的行；
- `{} +`：表示将 find 找到的文件批量传递给 sed（比 `{}` 更高效）。

#### 4. 复杂场景：用 awk 实现条件替换

例如：将 `data.csv` 中第 3 列（数值列）大于 100 的值替换为 "OverLimit"。`awk` 支持更灵活的逻辑判断，语法：`awk '条件 {替换操作} 1' 文件名`（`1` 表示打印所有行）：






```bash
awk -F ',' '$3 > 100 { $3 = "OverLimit" } 1' data.csv > new_data.csv
```

- `-F ','`：指定分隔符为逗号（CSV 文件）；
- `$3 > 100`：条件：第 3 列数值大于 100；
- `$3 = "OverLimit"`：满足条件时，将第 3 列替换为 "OverLimit"；
- `> new_data.csv`：输出到新文件（避免直接修改原文件，便于验证）。

### 三、关键总结

| 需求场景             | 核心命令组合 / 工具                     | 关键点                             |
| -------------------- | --------------------------------------- | ---------------------------------- |
| TopK（高频元素）     | `sort + uniq -c + sort -nr + head -n K` | 先排序让相同元素相邻，再统计频率   |
| 单文件全局替换       | `sed -i 's/旧/新/g' 文件名`             | `-i` 直接修改，特殊字符需转义      |
| 多文件 / 递归替换    | `find + xargs + sed`                    | 用 `find` 定位文件，批量传递给 sed |
| 条件替换（复杂逻辑） | `awk '条件 {替换} 1' 文件名`            | 支持数值判断、列操作，适合灵活场景 |
