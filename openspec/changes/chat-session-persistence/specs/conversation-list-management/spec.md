## ADDED Requirements

### Requirement: 新建会话接口
系统 SHALL 提供 `POST /api/conversation` 接口，允许用户显式创建新的空会话记录。

#### Scenario: 成功创建新会话
- **WHEN** 前端调用 `POST /api/conversation` 并传入 `{ "modelName": "deepseek-v4-flash" }`
- **THEN** 系统创建新的 ConversationEntity 记录：
  - memoryId = 自动生成 UUID v4 格式（如 `conv_550e8400-e29b-41d4-a716-446655440000`）
  - title = "新对话"（默认值）
  - modelName = 请求体中指定的模型名称
  - status = 1（正常）
  - messageCount = 0
  - createdAt / updatedAt = NOW()
- **AND** 返回 HTTP 201 Created，响应体包含完整的 ConversationDTO

#### Scenario: 不传 modelName 使用默认值
- **WHEN** 前端调用 `POST /api/conversation` 但不传 modelName 字段
- **THEN** 系统使用配置文件中的默认模型（`conversation.default-model: glm-5`）作为 modelName
- **AND** 返回 201 Created

#### Scenario: 前端自定义 session ID
- **WHEN** 前端希望使用自己生成的 session ID（如 localStorage 中已存在的 ID）
- **THEN** 可选传入 `{ "memoryId": "custom-session-id", "modelName": "qwen3.6-flash" }`
- **AND** 系统使用前端传入的 memoryId（需校验格式：UUID 或自定义字符串，长度 ≤ 64）
- **AND** 若该 memoryId 已存在，返回 HTTP 409 Conflict（提示会话已存在）

#### Scenario: 创建后立即可见
- **WHEN** 新会话创建成功
- **THEN** 立即调用 `GET /api/conversation` 查询列表时，该会话出现在第一条（因 updated_at 最新）
- **AND** 前端可跳转到该会话开始聊天

---

### Requirement: 会话列表查询接口
系统 SHALL 提供 `GET /api/conversation` 接口，支持分页查询和关键词过滤，返回当前用户的会话列表。

#### Scenario: 成功获取会话列表（默认参数）
- **WHEN** 前端调用 `GET /api/conversation`（不传 page/size 参数）
- **THEN** 系统返回 HTTP 200，响应体包含：
  ```json
  {
    "content": [
      {
        "memoryId": "conv_abc123",
        "title": "如何实现快速排序",
        "lastMessagePreview": "快速排序的平均时间复杂度为 O(nlogn)...",
        "modelName": "deepseek-v4-flash",
        "messageCount": 12,
        "updatedAt": "2026-05-20T15:30:00",
        "status": 1
      }
    ],
    "totalElements": 28,
    "totalPages": 2,
    "currentPage": 0,
    "size": 20
  }
  ```
- **AND** 结果按 `updated_at DESC` 排序（最近更新的会话在前）
- **AND** 自动过滤 `status = -1` 的已删除会话

#### Scenario: 分页查询
- **WHEN** 前端调用 `GET /api/conversation?page=1&size=10`
- **THEN** 返回第 2 页数据（索引从 0 开始），每页最多 10 条记录

#### Scenario: 关键词搜索
- **WHEN** 前端调用 `GET /api/conversation?keyword=排序`
- **THEN** 系统对 title 和 last_message_preview 字段进行模糊匹配（ILIKE '%排序%'）
- **AND** 仅返回匹配的会话记录

#### Scenario: 空结果处理
- **WHEN** 数据库中无任何会话记录（新用户首次使用）
- **THEN** 返回空数组，`totalElements=0, totalPages=0`

---

### Requirement: 会话标题更新接口
系统 SHALL 提供 `PUT /api/conversation/{memoryId}/title` 接口，允许用户手动修改会话标题。

#### Scenario: 成功更新标题
- **WHEN** 前端调用 `PUT /api/conversation/{memoryId}/title` 并传入 `{ "title": "算法学习笔记" }`
- **THEN** 系统更新 conversations 表的 title 字段为"算法学习笔记"
- **AND** 更新 updated_at 为当前时间戳
- **AND** 返回 HTTP 204 No Content

#### Scenario: 标题长度限制
- **WHEN** 用户提交的 title 长度 > 255 字符
- **THEN** 系统返回 HTTP 400 Bad Request，错误信息："Title length must not exceed 255 characters"

#### Scenario: 会话不存在
- **WHEN** memoryId 对应的会话不存在或已删除
- **THEN** 系统返回 HTTP 404 Not Found

#### Scenario: 标题去特殊字符
- **WHEN** 标题包含 HTML 标签或脚本注入代码（如 `<script>alert(1)</script>`）
- **THEN** 系统进行 XSS 过滤（转义 <, >, ", '），存储安全文本

---

### Requirement: 会话软删除接口
系统 SHALL 提供 `DELETE /api/conversation/{memoryId}` 接口，执行软删除操作并保留数据 30 天。

#### Scenario: 成功删除会话
- **WHEN** 前端调用 `DELETE /api/conversation/{memoryId}`
- **THEN** 系统更新该会话的 status 为 -1，deleted_at 为当前时间戳
- **AND** 返回 HTTP 204 No Content
- **AND** 物理数据仍保留在数据库中（可恢复）

#### Scenario: 删除已删除的会话（幂等性）
- **WHEN** 前端重复调用 DELETE 接口删除同一会话
- **THEN** 系统返回 HTTP 204（幂等操作，不报错）

#### Scenario: 删除后查询不可见
- **WHEN** 会话被软删除后，调用 `GET /api/conversation` 查询列表
- **THEN** 该会话不出现在结果集中（WHERE status != -1 过滤）

---

### Requirement: 会话消息记录查询接口
系统 SHALL 提供 `GET /api/conversation/{memoryId}/messages` 接口，支持分页查询某个会话的所有聊天消息记录（用于历史浏览，区别于 restore 的上下文恢复）。

#### Scenario: 成功获取会话消息列表（默认参数）
- **WHEN** 前端调用 `GET /api/conversation/{memoryId}/messages`
- **THEN** 系统返回 HTTP 200，响应体包含：
  ```json
  {
    "content": [
      {
        "id": 101,
        "role": "USER",
        "content": "帮我写一个快速排序算法",
        "thinkingContent": null,
        "attachments": [],
        "sequenceNum": 1,
        "timestamp": "2026-05-20T15:28:00"
      },
      {
        "id": 102,
        "role": "ASSISTANT",
        "content": "好的，这是一个快速排序的实现...",
        "thinkingContent": "首先理解快速排序的核心思想...",
        "attachments": null,
        "sequenceNum": 2,
        "timestamp": "2026-05-20T15:28:05"
      }
    ],
    "totalElements": 12,
    "totalPages": 1,
    "currentPage": 0,
    "size": 50
  }
  ```
- **AND** 结果按 `sequence_num ASC` 排序（时间正序）
- **AND** 默认返回最近 50 条消息（size=50）

#### Scenario: 分页加载历史消息
- **WHEN** 前端调用 `GET /api/conversation/{memoryId}/messages?page=0&size=20`
- **THEN** 返回该会话最早的 20 条消息（用于滚动加载更多历史）

#### Scenario: 按角色过滤消息
- **WHEN** 前端调用 `GET /api/conversation/{memoryId}/messages?role=ASSISTANT`
- **THEN** 仅返回 AI 助手的回复消息（排除 USER 和 SYSTEM 角色）

#### Scenario: 会话不存在或已删除
- **WHEN** memoryId 对应的会话不存在或 status = -1
- **THEN** 系统返回 HTTP 404 Not Found

#### Scenario: 空会话（无消息记录）
- **WHEN** 该会话刚创建但尚未发送任何消息
- **THEN** 返回空数组，`totalElements=0, totalPages=0`

---

### Requirement: 会话元数据自动更新机制
系统 SHALL 在每次消息保存时自动同步更新会话的 message_count、last_message_preview、updated_at 元数据。

#### Scenario: 发送用户消息后更新计数器
- **WHEN** 用户发送一条消息并通过 ConversationPersistenceService.saveContext() 保存
- **THEN** 系统原子化递增 message_count（+1）
- **AND** 更新 last_message_preview 为该条消息内容的前 100 字符
- **AND** 更新 updated_at 为当前时间戳

#### Scenario: 接收 AI 回复后更新预览
- **WHEN** AI 助手返回完整回复并保存到 conversation_messages 表
- **THEN** 同样触发上述元数据更新逻辑

#### Scenario: 并发写入冲突处理
- **WHEN** 多个请求同时尝试更新同一会话的 message_count
- **THEN** 使用乐观锁（@Version 注解）防止丢失更新
- **AND** 若版本号冲突，抛出 OptimisticLockingFailureException 并重试 3 次
