### 核心区别详解

我会从多个维度清晰对比这两种类型，并用简单的例子帮助你理解：





|      特性      |                    DATETIME                    |                          TIMESTAMP                           |
| :------------: | :--------------------------------------------: | :----------------------------------------------------------: |
|  **存储范围**  | '1000-01-01 00:00:00' 到 '9999-12-31 23:59:59' |    '1970-01-01 00:00:01' UTC 到 '2038-01-19 03:14:07' UTC    |
|  **存储空间**  |                     8 字节                     |                            4 字节                            |
| **时区敏感性** |       无，存储的是字面时间，不随时区变化       |     有，存储的是 UTC 时间戳，查询时会转换为当前会话时区      |
|  **自动赋值**  |               无默认自动赋值功能               | 可设置 `DEFAULT CURRENT_TIMESTAMP` 或 `ON UPDATE CURRENT_TIMESTAMP` |
|  **存储方式**  |         直接存储年、月、日、时、分、秒         |          存储从 1970-01-01 00:00:00 UTC 开始的秒数           |

### 实战示例

#### 1. 时区差异演示










```
-- 创建测试表
CREATE TABLE time_test (
    id INT PRIMARY KEY AUTO_INCREMENT,
    dt DATETIME,
    ts TIMESTAMP
);

-- 设置会话时区为东8区（北京）
SET time_zone = '+8:00';
-- 插入数据
INSERT INTO time_test (dt, ts) VALUES (NOW(), NOW());
-- 查询结果（东8区）
SELECT dt, ts FROM time_test;
-- 结果示例：dt 和 ts 都显示 2026-02-25 10:00:00

-- 切换时区为东9区（东京）
SET time_zone = '+9:00';
-- 再次查询
SELECT dt, ts FROM time_test;
-- 结果示例：dt 仍为 2026-02-25 10:00:00，ts 变为 2026-02-25 11:00:00
```

#### 2. 自动更新演示（TIMESTAMP 特有）







```
-- 创建带自动更新的表
CREATE TABLE user (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 插入数据
INSERT INTO user (username) VALUES ('test_user');
-- 查询：create_time 和 update_time 相同

-- 更新数据
UPDATE user SET username = 'new_test_user' WHERE id = 1;
-- 查询：update_time 会自动更新为当前时间，create_time 不变
```

### 总结

1. **时区处理**：`TIMESTAMP` 会随数据库会话时区自动转换，适合需要跨时区的场景；`DATETIME` 存储固定时间，不随时区变化。
2. **存储效率**：`TIMESTAMP` 仅占 4 字节，比 `DATETIME`（8 字节）更节省空间，但存储范围窄（仅到 2038 年）。
3. **自动功能**：`TIMESTAMP` 支持自动赋值和更新，适合记录创建 / 更新时间；`DATETIME` 无此特性，需手动赋值。

选择建议：如果需要记录跨时区的时间、且时间范围在 1970-2038 年之间，优先用 `TIMESTAMP`；如果需要存储更早 / 更晚的时间，或不需要时区转换，用 `DATETIME`。