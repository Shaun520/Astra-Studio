## MODIFIED Requirements

### Requirement: ConversationContext 数据结构设计
系统 SHALL 使用 ConversationContext 作为对话上下文的统一传输对象，包含版本号、校验和以及完整的消息列表。

#### Scenario: ConversationContext 结构完整性
- **WHEN** 系统创建或更新 ConversationContext
- **THEN** 该对象 MUST 包含以下字段：`memoryId`（会话唯一标识）、`messages`（MessageEntry 列表）、`modelName`、`version`（单调递增）、`checksum`（SHA-256）、`createdAt`、`updatedAt`、**`title`（可选，用于前端展示）**

#### Scenario: MessageEntry 结构
- **WHEN** 构建单条消息记录
- **THEN** MessageEntry MUST 包含：`role`（USER/ASSISTANT/SYSTEM）、`content`、`thinkingContent`（可选）、`attachments`（JSON 序列化的附件列表）、`sequenceNum`（会话内序号）、`timestamp`

---

## ADDED Requirements

### Requirement: 会话创建时机规范化
系统 SHALL 在用户首次发送消息时自动创建会话记录（而非仅依赖前端 session ID）。

#### Scenario: 首次发送消息触发创建
- **WHEN** ChatService.streamChat() 接收到某 memoryId 的第一条消息
- **AND** conversations 表中不存在该 memoryId 的记录
- **THEN** 系统自动插入新的 ConversationEntity：
  - memoryId = 前端传来的 session ID
  - title = "新对话"（后续由标题生成服务覆盖）
  - modelName = 当前使用的模型名称
  - status = 1（正常）
  - createdAt / updatedAt = NOW()
- **AND** 后续消息保存时关联到该会话

#### Scenario: 已有会话复用
- **WHEN** memoryId 已存在于 conversations 表
- **THEN** 不重复创建，直接复用现有记录
- **AND** 仅更新 updatedAt 时间戳

---

### Requirement: saveContext() 增强 —— 元数据同步
系统 SHALL 在 ConversationPersistenceService.saveContext() 执行时，同步更新会话元数据。

#### Scenario: 保存上下文后更新计数器和预览
- **WHEN** saveContext(memoryId, ctx) 成功将上下文写入 Redis 和 PostgreSQL
- **THEN** 系统在同一事务中执行：
  1. UPDATE conversations SET message_count = {ctx.messages.size()} WHERE memory_id = ?
  2. UPDATE conversations SET last_message_preview = {最后一条消息 content 前100字} WHERE memory_id = ?
  3. UPDATE conversations SET updated_at = NOW() WHERE memory_id = ?

#### Scenario: Redis 缓存失效策略
- **WHEN** 会话元数据更新完成
- **THEN** 主动删除该 memoryId 对应的 Redis 缓存 key（astra:conv:{memoryId}）
- **AND** 下次 loadContext() 时强制从 PostgreSQL 读取最新数据（Cache-Aside 模式）
