---
name: database-design
description: 数据库设计规范与优化指南
version: 1.0.0
category: development
triggers:
  - database design
  - 数据库设计
  - SQL 优化
  - 表设计
  - 数据库规范
scriptPath: check-db.sh
scriptType: bash
autoExecute: true
scriptTimeout: 10
---

# 数据库设计规范技能包

设计高效、可维护的数据库结构。

## 表设计原则

### 1. 命名规范

**表名**：
- 小写字母 + 下划线
- 使用复数形式
- 示例：`users`, `order_items`, `user_profiles`

**字段名**：
- 小写字母 + 下划线
- 见名知意
- 示例：`created_at`, `user_id`, `email_address`

**索引名**：
```sql
-- 主键：pk_表名
PRIMARY KEY pk_users

-- 唯一索引：uk_表名_字段名
UNIQUE INDEX uk_users_email

-- 普通索引：idx_表名_字段名
INDEX idx_users_created_at

-- 外键：fk_表名_关联表名
FOREIGN KEY fk_orders_users
```

### 2. 字段类型选择

| 数据类型 | 使用场景 | 示例 |
|---------|---------|------|
| **INT/BIGINT** | ID、数量、年龄 | `user_id BIGINT` |
| **VARCHAR** | 变长字符串 | `name VARCHAR(100)` |
| **CHAR** | 定长字符串 | `country_code CHAR(2)` |
| **TEXT** | 长文本 | `description TEXT` |
| **DECIMAL** | 金额、精确数值 | `price DECIMAL(10,2)` |
| **DATETIME** | 日期时间 | `created_at DATETIME` |
| **ENUM** | 固定选项 | `status ENUM('active','inactive')` |
| **BOOLEAN** | 布尔值 | `is_deleted BOOLEAN` |

**注意**：
- 避免使用 FLOAT/DOUBLE 存储金额
- VARCHAR 长度设置合理（避免过大）
- 时间字段统一使用 DATETIME 或 TIMESTAMP

### 3. 主键设计

**推荐**：
```sql
-- 自增ID（适合单机）
id BIGINT AUTO_INCREMENT PRIMARY KEY

-- 雪花ID（适合分布式）
id BIGINT PRIMARY KEY COMMENT '雪花ID'

-- UUID（分布式场景）
id CHAR(36) PRIMARY KEY COMMENT 'UUID'
```

**避免**：
- 业务字段作为主键（如手机号、邮箱）
- 联合主键（复杂度高）

### 4. 外键约束

**使用外键的优势**：
- 数据完整性保证
- 级联操作

**不使用外键的场景**：
- 高并发系统（性能考虑）
- 分库分表场景
- 微服务架构

```sql
-- 外键示例
FOREIGN KEY (user_id) 
  REFERENCES users(id) 
  ON DELETE CASCADE 
  ON UPDATE CASCADE
```

## 索引设计

### 1. 索引类型

**主键索引**：
```sql
PRIMARY KEY (id)
```

**唯一索引**：
```sql
UNIQUE INDEX uk_users_email (email)
```

**普通索引**：
```sql
INDEX idx_users_created_at (created_at)
```

**联合索引**（遵循最左前缀原则）：
```sql
INDEX idx_users_name_age (name, age)
-- 可以走索引: WHERE name = 'xxx'
-- 可以走索引: WHERE name = 'xxx' AND age = 25
-- 不走索引: WHERE age = 25
```

**全文索引**：
```sql
FULLTEXT INDEX ft_articles_content (content)
```

### 2. 索引优化原则

**何时创建索引**：
- ✓ WHERE 子句常用字段
- ✓ ORDER BY 字段
- ✓ JOIN 关联字段
- ✓ 查询频率高的字段

**何时避免索引**：
- ✗ 数据重复度高的字段（如性别）
- ✗ 频繁更新的字段
- ✗ 小表（全表扫描更快）

**索引失效场景**：
```sql
-- ❌ 在索引列上使用函数
WHERE YEAR(created_at) = 2025

-- ✓ 改为
WHERE created_at >= '2025-01-01' AND created_at < '2026-01-01'

-- ❌ 模糊查询前导模糊
WHERE name LIKE '%张%'

-- ✓ 改为
WHERE name LIKE '张%'

-- ❌ 类型转换
WHERE user_id = '123'  -- user_id 是 INT

-- ✓ 改为
WHERE user_id = 123
```

## SQL 优化

### 1. 查询优化

**避免 SELECT ***：
```sql
-- ❌ 不推荐
SELECT * FROM users;

-- ✓ 推荐
SELECT id, name, email FROM users;
```

**使用 LIMIT**：
```sql
SELECT id, name FROM users LIMIT 100;
```

**避免 N+1 查询**：
```sql
-- ❌ N+1 问题
SELECT * FROM orders;  -- 查询 N 个订单
-- 然后循环查询每个订单的用户
SELECT * FROM users WHERE id = ?;

-- ✓ 使用 JOIN
SELECT o.*, u.name 
FROM orders o 
JOIN users u ON o.user_id = u.id;
```

### 2. 分页优化

**传统分页**（大偏移量性能差）：
```sql
SELECT * FROM users LIMIT 100000, 20;
```

**优化方案（使用 ID 范围）**：
```sql
SELECT * FROM users 
WHERE id > 100000 
ORDER BY id 
LIMIT 20;
```

**使用覆盖索引**：
```sql
SELECT id, name FROM users 
WHERE status = 'active' 
ORDER BY created_at 
LIMIT 20;
```

### 3. COUNT 优化

**避免 COUNT(*)**：
```sql
-- ❌ 慢
SELECT COUNT(*) FROM users WHERE status = 'active';

-- ✓ 使用近似值（如缓存）
-- 或者定期统计存到另一个表
```

## 范式设计

### 第一范式（1NF）
每个字段都是不可分割的原子值

### 第二范式（2NF）
消除部分依赖（非主键字段完全依赖主键）

### 第三范式（3NF）
消除传递依赖（非主键字段不依赖其他非主键字段）

### 反范式化
为了性能适当冗余数据：
```sql
-- 订单表冗余用户名（避免频繁JOIN）
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    user_name VARCHAR(100),  -- 冗余字段
    total_amount DECIMAL(10,2)
);
```

## 常用字段

### 标准字段
```sql
id BIGINT AUTO_INCREMENT PRIMARY KEY,
created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
is_deleted BOOLEAN DEFAULT FALSE,
created_by BIGINT,
updated_by BIGINT
```

### 乐观锁
```sql
version INT NOT NULL DEFAULT 0
```

### 软删除
```sql
deleted_at DATETIME NULL,
is_deleted BOOLEAN DEFAULT FALSE
```

## 分库分表

### 垂直拆分
按业务模块拆分表

### 水平拆分
```sql
-- 按 user_id 取模
user_0, user_1, user_2, ...

-- 按时间分表
order_202501, order_202502, ...
```

## 最佳实践

1. **必须字段**：id, created_at, updated_at
2. **统一字符集**：utf8mb4
3. **统一时区**：UTC
4. **避免 NULL**：尽量使用 NOT NULL + DEFAULT
5. **合理使用注释**：COMMENT '字段说明'
6. **定期备份**：自动化备份机制
7. **监控慢查询**：开启 slow_query_log
