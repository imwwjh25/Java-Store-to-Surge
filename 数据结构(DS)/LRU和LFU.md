### 一、LRU 与 LFU 的核心区别及适用场景

#### 1. 淘汰依据与核心逻辑

- LRU（最近最少使用） ：基于时间维度 ，淘汰 “最久未被访问” 的数据。

    - 例：缓存中有 A（10 次访问，最后访问 1 小时前）、B（5 次访问，最后访问 1 分钟前）→ LRU 淘汰 A（虽总访问次数高，但最久未用）。

- LFU（最不经常使用） ：基于频率维度 ，淘汰 “历史访问次数最少” 的数据；若次数相同，再淘汰 “最久未用” 的（兼顾时间维度）。

    - 例：缓存中有 A（10 次访问）、B（5 次访问）→ LFU 淘汰 B（总访问次数低）；若 A、B 次数相同，则淘汰更早未访问的。

#### 2. 适用场景对比

| 策略 | 适用场景                                                     | 典型例子                                                     |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| LRU  | ① 突发流量频繁（如秒杀活动）；② 短期重复访问（如用户刷新网页、数据库查询缓存）；③ 内存有限需低开销 | 电商秒杀的临时缓存、浏览器页面缓存、操作系统内存页面置换。   |
| LFU  | ① 长期热点数据（如视频平台热门电影、电商高频商品）；② 访问模式稳定（如静态资源、词典类词频统计）；③ 需防止缓存污染（如抗爬虫扫描） | YouTube 热门视频缓存、电商首页高频商品详情、广告系统用户行为分析。 |

### 二、LFU 的设计与实现

#### 1. 核心数据结构（以 “哈希表 + 频率桶” 为例）

LFU 需要同时跟踪**访问次数**和**时间顺序**，经典实现为 **“两层哈希表 + 双向链表”**：

- **第一层哈希表（key → Node）**：存储键到节点的映射，支持 O (1) 时间查找节点。
- **第二层哈希表（freq → DoubleLinkedList）**：存储 “频率 → 双向链表” 的映射，每个链表维护相同访问次数的节点（链表内按访问时间排序，队首为最久未用，队尾为最近使用）。
- **最小频率跟踪**：记录当前缓存中最小的访问频率，用于快速定位需淘汰的节点。

#### 2. 关键方法与逻辑

以 LeetCode 460 题的 LFU 实现为例，核心方法包括 `get`（查询）、`put`（插入 / 更新），需保证 O (1) 平均时间复杂度：






```java
class LFUCache {
    // 节点类：存储键、值、访问次数、链表指针
    class Node {
        int key, value, count;
        Node prev, next;
        Node(int key, int value) { this.key = key; this.value = value; this.count = 1; }
    }

    // 双向链表类：维护同频率的节点，队首最久未用，队尾最近使用
    class DoubleLinkedList {
        Node head, tail;
        int size;
        DoubleLinkedList() {
            head = new Node(-1, -1);
            tail = new Node(-1, -1);
            head.next = tail;
            tail.prev = head;
            size = 0;
        }
        // 在尾部添加节点（最近使用）
        void addLast(Node node) {
            node.prev = tail.prev;
            node.next = tail;
            tail.prev.next = node;
            tail.prev = node;
            size++;
        }
        // 删除指定节点
        void remove(Node node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            size--;
        }
        // 删除队首节点（最久未用）
        Node removeFirst() {
            if (size == 0) return null;
            Node first = head.next;
            remove(first);
            return first;
        }
    }

    int capacity, size;               // 缓存容量、当前元素数
    Map<Integer, Node> keyToNode;     // key → 节点（O(1) 查找）
    Map<Integer, DoubleLinkedList> countToMap; // 频率 → 链表（O(1) 操作同频率节点）
    TreeSet<Integer> counts;          // 有序存储所有存在的频率（快速找最小频率，O(log n)）

    public LFUCache(int capacity) {
        this.capacity = capacity;
        this.size = 0;
        keyToNode = new HashMap<>();
        countToMap = new HashMap<>();
        counts = new TreeSet<>();
    }

    // 1. get 方法：查询并更新访问频率
    public int get(int key) {
        if (!keyToNode.containsKey(key)) return -1;
        Node node = keyToNode.get(key);
        int oldCount = node.count;

        // 从旧频率的链表中移除节点
        DoubleLinkedList oldList = countToMap.get(oldCount);
        oldList.remove(node);
        if (oldList.size == 0) { // 若链表空，移除该频率
            countToMap.remove(oldCount);
            counts.remove(oldCount);
        }

        // 更新频率，加入新频率的链表
        node.count++;
        int newCount = node.count;
        countToMap.putIfAbsent(newCount, new DoubleLinkedList());
        countToMap.get(newCount).addLast(node);
        counts.add(newCount); // 维护频率的有序性

        return node.value;
    }

    // 2. put 方法：插入/更新并处理淘汰
    public void put(int key, int value) {
        if (capacity == 0) return;

        if (keyToNode.containsKey(key)) { // 键已存在：更新值 + 同 get 逻辑更新频率
            Node node = keyToNode.get(key);
            node.value = value;
            // 复用 get 中的频率更新逻辑（省略重复代码）
            int oldCount = node.count;
            DoubleLinkedList oldList = countToMap.get(oldCount);
            oldList.remove(node);
            if (oldList.size == 0) {
                countToMap.remove(oldCount);
                counts.remove(oldCount);
            }
            node.count++;
            int newCount = node.count;
            countToMap.putIfAbsent(newCount, new DoubleLinkedList());
            countToMap.get(newCount).addLast(node);
            counts.add(newCount);
            return;
        }

        // 键不存在：需插入新节点
        if (size == capacity) { // 容量已满，淘汰最小频率的最久未用节点
            int minCount = counts.first(); // 当前最小频率
            DoubleLinkedList minList = countToMap.get(minCount);
            Node removed = minList.removeFirst(); // 淘汰队首（最久未用）
            keyToNode.remove(removed.key);
            size--;
            if (minList.size == 0) { // 若链表空，移除该频率
                countToMap.remove(minCount);
                counts.remove(minCount);
            }
        }

        // 插入新节点（频率初始为1）
        Node newNode = new Node(key, value);
        keyToNode.put(key, newNode);
        countToMap.putIfAbsent(1, new DoubleLinkedList());
        countToMap.get(1).addLast(newNode);
        counts.add(1);
        size++;
    }
}
```

#### 3. 核心逻辑说明

- **频率更新**：每次 `get` 或 `put` 操作后，节点的访问次数 `count` 加 1，并从旧频率链表移动到新频率链表。
- **淘汰策略**：当缓存满时，找到当前最小频率 `minCount`，从对应链表的队首（最久未用）淘汰节点。
- **边界处理**：若某频率的链表为空，需从 `countToMap` 和 `counts` 中移除该频率，保证下次快速定位最小频率。

### 三、LRU 与 LFU 的选型建议

- **选 LRU**：若业务是 “短期突发流量 + 低内存开销”（如秒杀、网页缓存），优先用 LRU（实现简单，性能损耗低）。
- **选 LFU**：若业务是 “长期高频访问 + 抗缓存污染”（如视频平台、电商热门商品），优先用 LFU（但需接受更高的实现复杂度和内存开销）。
- **混合策略**：现代缓存库（如 Caffeine 的 TinyLFU）会结合两者优点，平衡短期突发和长期热点。