### CHAR 和 VARCHAR 的核心区别

两者都是用于存储字符串的类型，但核心差异在于**存储空间分配方式**、**尾部空格处理**和**存储效率**，下面结合具体例子逐一说明：



|     特性     |                         CHAR(n)                         |                    VARCHAR(n)                     |
| :----------: | :-----------------------------------------------------: | :-----------------------------------------------: |
|   存储空间   | 固定长度：无论实际存储多少字符，都占用 `n` 个字符的空间 | 可变长度：仅占用实际字符数 + 1/2 字节（长度标识） |
| 尾部空格处理 |        存储时自动截断尾部空格，查询时也不会返回         |           保留尾部空格（符合 SQL 标准）           |
|   最大长度   |                     最多 255 个字符                     |        最多 65535 个字符（受行总长度限制）        |
|   适用场景   |           长度固定的字符串（如手机号、邮编）            |        长度不固定的字符串（如姓名、地址）         |

### 具体例子（实操验证）

为了更直观，我们创建测试表并插入数据，对比结果：

#### 步骤 1：创建测试表







```
CREATE TABLE test_char_varchar (
    id INT PRIMARY KEY AUTO_INCREMENT,
    char_col CHAR(10),       -- 固定10个字符长度
    varchar_col VARCHAR(10)  -- 可变10个字符长度
);
```

#### 例子 1：存储短于定义长度的字符串（核心区别）

插入 “张三”（2 个字符），观察存储空间和查询结果：









```
-- 插入数据
INSERT INTO test_char_varchar (char_col, varchar_col) VALUES ('张三', '张三');

-- 查询数据
SELECT 
    char_col, 
    LENGTH(char_col) AS char_len,  -- LENGTH() 返回字节数（UTF8下1个中文字符=3字节）
    varchar_col, 
    LENGTH(varchar_col) AS varchar_len
FROM test_char_varchar;
```

**查询结果**：


| char_col | char_len | varchar_col | varchar_len |
| :------: | :------: | :---------: | :---------: |
|   张三   |    6     |    张三     |      6      |

**关键解释**：

- `CHAR(10)`：定义长度为 10 个字符（UTF8 下 30 字节），即使只存 2 个字符，仍会分配 10 个字符的空间（但 MySQL 会优化显示，不展示补的空格）；
- `VARCHAR(10)`：仅分配实际字符数（2 个字符 = 6 字节）+ 1 字节长度标识（总 7 字节），空间更节省。

#### 例子 2：存储等于定义长度的字符串（无差异）

插入 “1234567890”（10 个字符）：







```
INSERT INTO test_char_varchar (char_col, varchar_col) VALUES ('1234567890', '1234567890');

SELECT 
    char_col, LENGTH(char_col), 
    varchar_col, LENGTH(varchar_col)
FROM test_char_varchar WHERE id=2;
```

**查询结果**：








|  char_col  | LENGTH(char_col) | varchar_col | LENGTH(varchar_col) |
| :--------: | :--------------: | :---------: | :-----------------: |
| 1234567890 |        10        | 1234567890  |         10          |

**关键解释**：

当存储长度等于定义长度时，两者占用的字节数相同，无差异。

#### 例子 3：尾部空格的处理（核心差异）

插入带尾部空格的字符串 “李四”（2 个字符 + 2 个空格）：







```
INSERT INTO test_char_varchar (char_col, varchar_col) VALUES ('李四  ', '李四  ');

-- 查询原始值 + 去除空格后的值，对比差异
SELECT 
    char_col, 
    TRIM(char_col) AS char_trim,  -- TRIM() 去除首尾空格
    varchar_col, 
    TRIM(varchar_col) AS varchar_trim
FROM test_char_varchar WHERE id=3;
```

**查询结果**：







| char_col | char_trim | varchar_col | varchar_trim |
| :------: | :-------: | :---------: | :----------: |
|   李四   |   李四    |    李四     |     李四     |

**关键解释**：

- `CHAR(10)`：自动截断了尾部的 2 个空格，查询返回的 “李四” 无空格；
- `VARCHAR(10)`：保留了尾部的 2 个空格，查询返回 “李四”（带空格），`TRIM()`后才变为 “李四”。

#### 例子 4：存储超长字符串（报错验证）

尝试插入超过定义长度（10 字符）的字符串 “12345678901”（11 个字符）：







```
-- 测试CHAR(10)：严格模式下直接报错
INSERT INTO test_char_varchar (char_col) VALUES ('12345678901');
-- 报错：Data too long for column 'char_col' at row 1

-- 测试VARCHAR(10)：同样报错（长度限制一致）
INSERT INTO test_char_varchar (varchar_col) VALUES ('12345678901');
-- 报错：Data too long for column 'varchar_col' at row 1
```

**关键解释**：

两者都有 “n” 的长度限制，超过限制会报错（非严格模式下会截断，但不推荐）。

#### 例子 5：实际业务场景示例







|   场景   |   推荐类型   |        示例         |               原因               |
| :------: | :----------: | :-----------------: | :------------------------------: |
|  手机号  |   CHAR(11)   |     13800138000     |  固定 11 位，CHAR 查询效率更高   |
|   邮编   |   CHAR(6)    |       100080        |      固定 6 位，无长度变化       |
|  用户名  | VARCHAR(20)  |      张三_123       |    长度 1-20 位不等，节省空间    |
| 商品描述 | VARCHAR(200) | 新款 XX 手机 全网通 | 长度不固定，避免 CHAR 的空间浪费 |

### 总结

#### CHAR 和 VARCHAR 的核心区别（关键点回顾）

1. **存储空间**：`CHAR(n)` 是固定长度（占 n 个字符空间），`VARCHAR(n)` 是可变长度（仅占实际字符数 + 长度标识）；
2. **尾部空格**：`CHAR` 自动截断尾部空格，`VARCHAR` 保留尾部空格；
3. **适用场景**：`CHAR` 适合长度固定的字符串（如手机号、邮编），`VARCHAR` 适合长度不固定的字符串（如姓名、地址）。

补充：在 MySQL 中，`CHAR` 的查询性能略高于 `VARCHAR`（因为长度固定，无需计算实际长度），但空间利用率低；`VARCHAR` 空间更节省，但查询时需解析长度标识，性能略低（现代数据库中差异可忽略）。