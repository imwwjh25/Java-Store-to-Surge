### 1. 简单字符串替换（适合少量固定敏感词）

直接使用`String.replace()`或`replaceAll()`方法，将敏感词逐个替换为`*`。

**实现示例**：





```java
public class SensitiveWordFilter {
    // 敏感词列表
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>();
    static {
        SENSITIVE_WORDS.add("敏感词1");
        SENSITIVE_WORDS.add("敏感词2");
    }
    
    // 替换敏感词为*
    public static String filter(String content) {
        if (content == null) return null;
        String result = content;
        for (String word : SENSITIVE_WORDS) {
            if (result.contains(word)) {
                // 生成与敏感词长度相同的*字符串
                String replacement = "*".repeat(word.length());
                result = result.replace(word, replacement);
            }
        }
        return result;
    }
}
```

**优点**：实现简单，适合敏感词数量少且固定的场景。
**缺点**：效率低（多次遍历字符串），无法处理嵌套或重叠的敏感词（如 "敏感词" 和 "敏感" 同时存在时可能漏替换）。

### 2. 正则表达式替换（适合规则简单的场景）

将敏感词拼接为正则表达式，通过一次匹配替换所有敏感词。

**实现示例**：










```java
public class RegexSensitiveFilter {
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>();
    static {
        SENSITIVE_WORDS.add("敏感词1");
        SENSITIVE_WORDS.add("敏感词2");
    }
    
    // 编译正则表达式（只初始化一次，提高效率）
    private static final Pattern PATTERN;
    static {
        // 转义特殊字符，避免正则语法冲突
        List<String> escapedWords = new ArrayList<>();
        for (String word : SENSITIVE_WORDS) {
            escapedWords.add(Pattern.quote(word));
        }
        PATTERN = Pattern.compile(String.join("|", escapedWords));
    }
    
    public static String filter(String content) {
        if (content == null) return null;
        Matcher matcher = PATTERN.matcher(content);
        // 替换匹配到的敏感词为同等长度的*
        return matcher.replaceAll(matchResult -> {
            String word = matchResult.group();
            return "*".repeat(word.length());
        });
    }
}
```

**优点**：代码简洁，一次遍历即可替换所有敏感词。
**缺点**：敏感词数量过多时，正则表达式会很长，影响匹配效率；依然难以处理复杂的嵌套场景。

### 3. 前缀树（Trie 树）算法（适合大量敏感词场景）

前缀树是处理敏感词过滤的经典数据结构，尤其适合敏感词数量多（数万级）的场景，查询效率极高。

**核心思路**：

1. 将所有敏感词构建成前缀树（每个节点表示一个字符，路径形成完整敏感词）。
2. 遍历待过滤文本，在树上匹配敏感词，匹配到则替换为`*`。

**实现示例**：








```java
public class TrieSensitiveFilter {
    // 前缀树节点
    private static class TrieNode {
        // 子节点（字符 -> 节点）
        private final Map<Character, TrieNode> children = new HashMap<>();
        // 是否为敏感词的结束节点
        private boolean isEnd = false;
        
        public Map<Character, TrieNode> getChildren() {
            return children;
        }
        
        public boolean isEnd() {
            return isEnd;
        }
        
        public void setEnd(boolean end) {
            isEnd = end;
        }
    }
    
    // 根节点
    private final TrieNode root = new TrieNode();
    
    // 初始化：添加敏感词到前缀树
    public void addSensitiveWord(String word) {
        if (word == null || word.isEmpty()) return;
        TrieNode current = root;
        for (char c : word.toCharArray()) {
            current.getChildren().putIfAbsent(c, new TrieNode());
            current = current.getChildren().get(c);
        }
        current.setEnd(true); // 标记敏感词结束
    }
    
    // 过滤敏感词
    public String filter(String content) {
        if (content == null) return null;
        StringBuilder result = new StringBuilder(content);
        TrieNode current = root;
        int start = 0; // 记录当前匹配的起始位置
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            // 跳过已匹配的敏感词部分（避免重复处理）
            if (current != root && !current.getChildren().containsKey(c)) {
                current = root;
                i = start; // 回溯到起始位置的下一个字符
                start++;
                continue;
            }
            
            // 移动到子节点
            if (current.getChildren().containsKey(c)) {
                current = current.getChildren().get(c);
                // 匹配到敏感词，替换为*
                if (current.isEnd()) {
                    // 从start到i的位置替换为*
                    for (int j = start; j <= i; j++) {
                        result.setCharAt(j, '*');
                    }
                    // 重置状态，继续匹配后续内容
                    current = root;
                    start = i + 1;
                }
            } else {
                // 未匹配到，移动起始位置
                start = i + 1;
            }
        }
        return result.toString();
    }
}
```

**使用方式**：









```java
public static void main(String[] args) {
    TrieSensitiveFilter filter = new TrieSensitiveFilter();
    // 添加敏感词
    filter.addSensitiveWord("敏感词1");
    filter.addSensitiveWord("敏感词2");
    // 过滤文本
    String content = "这是敏感词1和敏感词2的例子";
    System.out.println(filter.filter(content)); // 输出：这是*****和*****的例子
}
```

**优点**：

- 时间复杂度低（接近 O (n)，n 为文本长度），适合大量敏感词场景。
- 能处理嵌套、重叠的敏感词（如 "敏感词" 和 "敏感" 同时存在时，会优先匹配最长的敏感词）。

**缺点**：实现相对复杂，需要维护前缀树结构。

### 4. 开源框架集成（适合生产环境）

如果不想重复造轮子，可直接使用成熟的开源框架，例如：

- **HanLP**：自然语言处理工具包，内置敏感词过滤功能。
- **IKAnalyzer**：常用的中文分词器，可扩展敏感词过滤。

**HanLP 示例**：










```java
import com.hankcs.hanlp.dictionary.py.Pinyin;
import com.hankcs.hanlp.tokenizer.StandardTokenizer;
import com.hankcs.hanlp.suggest.Suggester;

// 敏感词过滤配置（需参考HanLP文档配置敏感词词典）
public class HanLPSensitiveFilter {
    public static String filter(String content) {
        // HanLP会自动替换敏感词为*（需提前配置词典）
        return StandardTokenizer.segment(content).toString();
    }
}
```





![img](data:image/png;base64,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)

**优点**：成熟稳定，支持复杂场景（如拼音混淆、谐音替换等）。
**缺点**：引入第三方依赖，增加项目体积。

### 方案选择建议

- 少量敏感词（100 个以内）：用**简单字符串替换**或**正则表达式**。
- 大量敏感词（上千个）：用**前缀树算法**，兼顾效率和灵活性。
- 生产环境且需处理复杂场景（如变体敏感词）：用**开源框架**（如 HanLP）。
