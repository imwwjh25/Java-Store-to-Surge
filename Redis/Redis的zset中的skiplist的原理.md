Redis 的 ZSet（有序集合）核心实现之一是 **跳表（Skip List）**（配合哈希表实现 O (1) 查找元素分数）。跳表是一种 “多层链表” 结构，通过 “空间换时间” 将链表的增删改查复杂度从 O (n) 优化到 O (log n)，兼具链表的灵活性和数组的高效查找能力。

下面从 **跳表的结构设计**、**核心操作（增 / 删 / 查）的具体实现** 展开，结合 Redis 源码的核心逻辑（简化后），让你彻底看懂。

## 一、跳表的核心结构设计（Redis 源码简化）

Redis 的跳表由 **跳表节点（zskiplistNode）** 和 **跳表本身（zskiplist）** 两部分组成，先看数据结构定义（基于 Redis 6.x 源码简化）：

### 1. 跳表节点（zskiplistNode）

每个节点存储一个 ZSet 元素（value+score），以及多层前进指针（用于快速跳转）、后退指针（用于反向遍历）、跨度（记录指针跨越的节点数，用于计算排名）。








```c
typedef struct zskiplistNode {
    // ZSet 元素的值（唯一，通过哈希表保证）
    sds ele;
    // 元素的分数（排序依据，可重复）
    double score;
    // 后退指针：指向当前节点的前一个节点（仅最底层链表有，用于反向遍历）
    struct zskiplistNode *backward;
    // 层：每个节点有多层，每层包含一个前进指针和跨度
    struct zskiplistLevel {
        // 前进指针：指向同一层的下一个节点
        struct zskiplistNode *forward;
        // 跨度：当前节点到 forward 节点之间跨越的节点数（用于计算排名）
        unsigned long span;
    } level[]; // 柔性数组，每层的信息
} zskiplistNode;
```

### 2. 跳表本身（zskiplist）

管理跳表的全局信息，包括表头、表尾、最大层数、节点总数。








```c
typedef struct zskiplist {
    // 表头节点（不存储实际元素，仅用于跳转）
    struct zskiplistNode *header;
    // 表尾节点（不存储实际元素，仅用于快速定位尾部）
    struct zskiplistNode *tail;
    // 跳表中当前的节点总数（不含表头/表尾）
    unsigned long length;
    // 跳表当前的最大层数（表头的层数为最大层数）
    int level;
} zskiplist;
```

### 3. 跳表结构示意图（直观理解）






```plaintext
level 3: 表头 → [节点A] → [节点C] → 表尾（跨度：1, 2）
level 2: 表头 → [节点A] → [节点B] → [节点C] → 表尾（跨度：1, 1, 1）
level 1: 表头 → [节点A] → [节点B] → [节点C] → [节点D] → 表尾（跨度：1, 1, 1, 1）
          （backward指针：A←B←C←D）
```

- 最底层（level 1）是完整的双向链表，包含所有节点（保证遍历的完整性）；
- 上层是 “稀疏链表”，层数越高，节点越少（用于快速跳转，减少查找次数）；
- 表头节点的层数等于跳表的最大层数，所有层的前进指针都指向对应层的第一个节点。

### 4. 关键设计细节

- **层数随机生成**：新节点的层数不是固定的，而是通过 “随机算法” 生成（Redis 中最大层数为 64），保证上层链表的稀疏性（层数越高，概率越低）；
- **分数可重复**：多个节点可以有相同的 score，但 ele（元素值）必须唯一（通过 ZSet 配套的哈希表 `dict` 保证）；
- **跨度（span）**：用于快速计算节点的排名（比如要查节点 C 的排名，从表头出发，累加各层的跨度即可）。

## 二、跳表的核心操作实现（Redis 源码逻辑）

### 1. 查找操作（最基础，增删都依赖查找）

查找的目标是：根据 `ele`（元素值）或 `score`（分数），找到对应的节点，或找到插入 / 删除的位置。

#### 查找流程（以 “按 score 查找节点” 为例）

1. **从表头的最高层开始**：初始化当前节点为表头，当前层数为跳表的最大层数；

2. 逐层向下查找 ：

    - 若当前层的前进指针指向的节点 score ≤ 目标 score，且节点不是表尾，则跳到该节点，累加跨度（用于排名计算）；
    - 若不满足，则向下一层（level--）；

3. **最终定位到最底层**：当 level 减到 1 时，当前节点的下一个节点就是目标节点（或插入位置）；

4. **验证元素值**：如果是按 ele 查找，还需要检查目标节点的 ele 是否与传入值一致（因为 score 可重复）。

#### 查找源码简化（核心逻辑）




```c
// 查找目标 score 和 ele 对应的节点，返回节点指针（未找到返回 NULL）
zskiplistNode *zslFind(zskiplist *zsl, double score, sds ele) {
    zskiplistNode *x = zsl->header; // 从表头开始
    // 从最高层向下遍历
    for (int i = zsl->level - 1; i >= 0; i--) {
        // 前进指针不为空，且 score 小于等于目标 score，继续前进
        while (x->level[i].forward != NULL && 
               (x->level[i].forward->score < score ||
                (x->level[i].forward->score == score && 
                 sdscmp(x->level[i].forward->ele, ele) < 0))) {
            x = x->level[i].forward;
        }
    }
    // 此时 x 是目标节点的前一个节点，跳转到下一个节点
    x = x->level[i+1].forward;
    // 验证是否是目标节点（score 和 ele 都匹配）
    if (x != NULL && x->score == score && sdscmp(x->ele, ele) == 0) {
        return x;
    } else {
        return NULL;
    }
}
```

#### 查找复杂度：O (log n)

- 每一层的查找步数是常数（因为上层链表稀疏），总层数是 O (log n)（随机层数的设计保证），因此整体复杂度是 O (log n)。

### 2. 插入操作（核心：找到插入位置 → 生成随机层数 → 调整指针）

插入的核心是 “不破坏跳表的有序性”，步骤分为 4 步：

#### 插入流程

1. 查找插入位置 ：

    - 遍历跳表，找到 “每一层中，位于目标节点之前的节点”，存入 `update` 数组（`update[i]` 表示第 i 层中，目标节点的前驱节点）；
    - 同时记录各层的跨度，用于后续调整排名。

2. 生成新节点的随机层数 ：

    - 新节点的层数通过 “随机算法” 生成（Redis 中：默认层数 1，有 50% 概率每层加 1，最大层数 64）；
    - 若新节点的层数超过跳表当前的最大层数，则更新跳表的最大层数，并将 `update` 数组中超出原层数的部分指向表头。

3. **创建新节点**：初始化新节点的 ele、score、各层的 forward 指针和 span。

4. 调整各层指针 ：

    - 对每一层 i（从 0 到新节点的层数 - 1）：
        - 新节点的 forward 指针 = `update[i]` 的 forward 指针；
        - 新节点的 span = `update[i]` 的 span - （当前位置到 `update[i]` 的跨度）；
        - `update[i]` 的 forward 指针 = 新节点；
        - `update[i]` 的 span = （当前位置到新节点的跨度） + 新节点的 span；

5. 设置后退指针 ：新节点的 backward 指针 =```update[0]```（最底层的前驱节点）；

- 若新节点的 forward 指针不为空，则 forward 节点的 backward 指针 = 新节点；

6. **更新跳表信息**：节点总数 +1。

#### 插入源码简化（核心逻辑）



```c
// 插入元素 ele 和分数 score，返回新节点
zskiplistNode *zslInsert(zskiplist *zsl, double score, sds ele) {
    zskiplistNode *update[ZSKIPLIST_MAXLEVEL]; // 存储各层的前驱节点
    unsigned long rank[ZSKIPLIST_MAXLEVEL] = {0}; // 存储各层的排名
    zskiplistNode *x = zsl->header;

    // 步骤1：查找插入位置，填充 update 和 rank 数组
    for (int i = zsl->level - 1; i >= 0; i--) {
        rank[i] = (i == zsl->level - 1) ? 0 : rank[i+1];
        while (x->level[i].forward != NULL && 
               (x->level[i].forward->score < score ||
                (x->level[i].forward->score == score && 
                 sdscmp(x->level[i].forward->ele, ele) < 0))) {
            rank[i] += x->level[i].span; // 累加跨度，计算排名
            x = x->level[i].forward;
        }
        update[i] = x; // 记录第 i 层的前驱节点
    }

    // 步骤2：生成新节点的随机层数
    int level = zslRandomLevel();
    // 若新节点层数超过跳表最大层数，更新 update 数组和跳表最大层数
    if (level > zsl->level) {
        for (int i = zsl->level; i < level; i++) {
            update[i] = zsl->header;
            update[i]->level[i].span = zsl->length;
        }
        zsl->level = level;
    }

    // 步骤3：创建新节点
    x = zslCreateNode(level, score, ele);

    // 步骤4：调整各层指针和跨度
    for (int i = 0; i < level; i++) {
        x->level[i].forward = update[i]->level[i].forward;
        x->level[i].span = update[i]->level[i].span - (rank[0] - rank[i]);
        update[i]->level[i].forward = x;
        update[i]->level[i].span = (rank[0] - rank[i]) + 1;
    }

    // 步骤5：调整高于新节点层数的前驱节点跨度
    for (int i = level; i < zsl->level; i++) {
        update[i]->level[i].span++;
    }

    // 步骤6：设置后退指针
    x->backward = (update[0] == zsl->header) ? NULL : update[0];
    if (x->level[0].forward != NULL) {
        x->level[0].forward->backward = x;
    } else {
        zsl->tail = x; // 新节点是表尾
    }

    // 步骤7：更新节点总数
    zsl->length++;
    return x;
}
```

#### 插入复杂度：O (log n)

- 查找插入位置：O (log n)；
- 调整各层指针：O (level)（level 最大 64，可视为常数）；
- 整体复杂度：O (log n)。

### 3. 删除操作（核心：找到节点 → 调整指针 → 释放节点）

删除的核心是 “移除节点后，保证跳表的有序性和指针连贯性”，步骤分为 4 步：

#### 删除流程

1. 查找目标节点和前驱节点 ：

    - 遍历跳表，找到目标节点（ele 和 score 匹配），同时记录各层的前驱节点（存入 `update` 数组）；
    - 若未找到目标节点，直接返回失败。

2. 调整各层指针和跨度 ：

    - 对每一层 i（从 0 到跳表最大层数 - 1）：- 若```update[i]```的 forward 指针指向目标节点，则：

       - `update[i]` 的 forward 指针 = 目标节点的 forward 指针；
       - `update[i]` 的 span += 目标节点的 span - 1；

     - 否则，跳出循环（更高层的指针不会指向目标节点）。

3. 调整后退指针 ：

    - 若目标节点的 forward 指针不为空，则 forward 节点的 backward 指针 = 目标节点的 backward 指针；
    - 若目标节点是表尾，则跳表的 tail 指针 = 目标节点的 backward 指针。

4. 更新跳表信息 ：

    - 节点总数 -1；
    - 若目标节点的层数等于跳表的最大层数，且表头的 forward 指针为空，则降低跳表的最大层数。

5. **释放节点**：释放目标节点的 ele（sds 字符串）和节点本身的内存。

#### 删除源码简化（核心逻辑）







```c
// 删除 score 和 ele 对应的节点，返回是否成功
int zslDelete(zskiplist *zsl, double score, sds ele) {
    zskiplistNode *update[ZSKIPLIST_MAXLEVEL];
    zskiplistNode *x = zsl->header;

    // 步骤1：查找目标节点和前驱节点
    for (int i = zsl->level - 1; i >= 0; i--) {
        while (x->level[i].forward != NULL && 
               (x->level[i].forward->score < score ||
                (x->level[i].forward->score == score && 
                 sdscmp(x->level[i].forward->ele, ele) < 0))) {
            x = x->level[i].forward;
        }
        update[i] = x;
    }

    // 步骤2：验证目标节点是否存在
    x = x->level[0].forward;
    if (x == NULL || x->score != score || sdscmp(x->ele, ele) != 0) {
        return 0; // 未找到，删除失败
    }

    // 步骤3：调整各层指针和跨度
    for (int i = 0; i < zsl->level; i++) {
        if (update[i]->level[i].forward == x) {
            update[i]->level[i].forward = x->level[i].forward;
            update[i]->level[i].span += x->level[i].span - 1;
        } else {
            break; // 更高层无该节点，无需调整
        }
    }

    // 步骤4：调整后退指针
    if (x->level[0].forward != NULL) {
        x->level[0].forward->backward = x->backward;
    } else {
        zsl->tail = x->backward; // 目标节点是表尾，更新 tail
    }

    // 步骤5：降低跳表的最大层数（如果需要）
    while (zsl->level > 1 && zsl->header->level[zsl->level - 1].forward == NULL) {
        zsl->level--;
    }

    // 步骤6：更新节点总数并释放节点
    zsl->length--;
    zslFreeNode(x); // 释放节点内存（包括 sds ele）
    return 1; // 删除成功
}
```

#### 删除复杂度：O (log n)

- 查找目标节点和前驱节点：O (log n)；
- 调整各层指针：O (level)（常数级）；
- 整体复杂度：O (log n)。

## 三、Redis 跳表的关键优化（源码细节补充）

1. 随机层数算法 ：

    - Redis 中，新节点的层数生成规则：默认层数 1，每次有 50% 的概率加 1，最大层数 64（`ZSKIPLIST_MAXLEVEL=64`）；
    - 该规则保证了跳表的层数是 O (log n)，避免出现 “所有节点都是 64 层” 的极端情况（浪费空间）。

2. 跨度（span）的设计 ：

    - 跨度用于快速计算节点的排名（比如 `ZRANK` 命令），无需遍历整个底层链表；
    - 插入 / 删除节点时，同步调整跨度，保证排名计算的准确性。

3. 与哈希表的配合 ：

    - ZSet 同时维护跳表和哈希表：跳表负责按 score 排序和范围查询（如 `ZRANGE`），哈希表负责按 ele 快速查找 score（O (1) 复杂度）；
    - 哈希表的 key 是 ele，value 是跳表节点，保证 ele 的唯一性。

4. 反向遍历支持 ：

    - 最底层链表的 backward 指针，支持反向遍历（如 `ZREVRANGE` 命令），无需从表头重新查找。

## 四、总结：跳表的核心逻辑

1. **结构本质**：多层稀疏链表，底层是完整双向链表（保证完整性），上层是跳转层（提升查找效率）；

2. 核心操作 ：

    - 查找：从最高层向下跳，O (log n)；
    - 插入：找位置 → 随机层数 → 调指针，O (log n)；
    - 删除：找节点 → 调指针 → 释内存，O (log n)；

3. **Redis 优化**：随机层数、跨度排名、哈希表配合、反向遍历，让跳表在排序、查找、范围查询场景下兼具高效性和灵活性。

Redis 选择跳表而非红黑树，是因为跳表的实现更简单（红黑树的旋转操作复杂），且范围查询效率更高（跳表可直接通过多层指针跳转，红黑树需要中序遍历）。