## ADDED Requirements

### Requirement: Kryo 高性能序列化引擎
系统 SHALL 基于 Kryo 5.x + CompatibleFieldSerializer 实现 ConversationContext 的高性能序列化/反序列化，并支持版本向前兼容与 JSON 降级。

#### Scenario: 正常序列化与反序列化
- **WHEN** 系统需要将 ConversationContext 持久化到 Redis 或 PostgreSQL
- **THEN** 调用 `KryoSerializer.toBytes()` 将对象序列化为字节数组（耗时 < 1ms），调用 `KryoSerializer.fromBytes()` 可完整还原所有字段

#### Scenario: 版本向前兼容
- **WHEN** KryoSerializer 升级后（如 ConversationContext 新增 `userId` 字段）需要反序列化旧版本字节数组
- **THEN** 使用 `CompatibleFieldSerializer` 按字段 offset 匹配，新增字段自动设为 `null`，不抛异常

#### Scenario: JSON 降级恢复
- **WHEN** Kryo 反序列化失败（如 Kryo 版本不兼容或数据结构发生重大变更）
- **THEN** 系统自动降级为 Jackson JSON 反序列化尝试恢复数据；若 JSON 也失败，返回空的 ConversationContext 且不影响主流程

#### Scenario: 数据完整性校验
- **WHEN** 从 PostgreSQL 读取 snapshot 后反序列化
- **THEN** 系统对原始字节数组计算 SHA-256 校验和，与数据库中存储的 `checksum` 字段对比，不一致时记录 ERROR 日志并触发 messages 重建路径

---

### Requirement: Redis 热缓存 + PostgreSQL 冷存储双写架构
系统 SHALL 实现 Redis 作为第一级热缓存、PostgreSQL 作为第二级冷存储的双层持久化架构，写操作先写 Redis 同步、后异步刷 PostgreSQL。

#### Scenario: 写路径 —— 同步写 Redis
- **WHEN** ConversationPersistenceService.saveContext() 被调用
- **THEN** 系统立即执行 Kryo 序列化 → Redis SET + EXPIRE（TTL=24h），整个过程 < 3ms 且不阻塞请求线程

#### Scenario: 写路径 —— 异步刷 PostgreSQL
- **WHEN** Redis 写入成功
- **THEN** 系统通过 `@Async` 线程池异步执行：插入/更新 `conversations` 表 → 写入/更新 `conversation_snapshots` 表（含 checksum）→ 增量写入 `conversation_messages` 表

#### Scenario: 读路径 —— Redis 命中
- **WHEN** ConversationPersistenceService.loadContext(memoryId) 被调用且 Redis 中存在对应 key
- **THEN** 直接从 Redis GET 字节数组 → Kryo 反序列化 → 返回 ConversationContext（延迟 < 1ms）

#### Scenario: 读路径 —— Redis 未命中，PostgreSQL 命中
- **WHEN** Redis 中不存在对应 key（如 TTL 过期或缓存淘汰）
- **THEN** 系统查询 `conversation_snapshots` 表 → 校验 checksum → Kryo 反序列化 → 回写 Redis → 返回 Context

#### Scenario: 读路径 —— 全链路穿透，messages 重建
- **WHEN** Redis 和 snapshots 表中均不存在数据，但 `conversation_messages` 表中有该会话的消息记录
- **THEN** 系统逐条读取 messages（按 sequence_num 排序）→ 重建 ConversationContext → 生成新 snapshot 写入 DB → 返回重建后的 Context

#### Scenario: 读路径 —— 全新会话
- **WHEN** memoryId 在 Redis、snapshots 和 messages 三处均不存在
- **THEN** 系统返回空的 ConversationContext（messages 为空列表，version=1），不抛异常

---

### Requirement: ConversationContext 数据结构设计
系统 SHALL 使用 ConversationContext 作为对话上下文的统一传输对象，包含版本号、校验和以及完整的消息列表。

#### Scenario: ConversationContext 结构完整性
- **WHEN** 系统创建或更新 ConversationContext
- **THEN** 该对象 MUST 包含以下字段：`memoryId`（会话唯一标识）、`messages`（MessageEntry 列表）、`modelName`、`version`（单调递增）、`checksum`（SHA-256）、`createdAt`、`updatedAt`、**`title`（可选，用于前端展示）**

#### Scenario: MessageEntry 结构
- **WHEN** 构建单条消息记录
- **THEN** MessageEntry MUST 包含：`role`（USER/ASSISTANT/SYSTEM）、`content`、`thinkingContent`（可选）、`attachments`（JSON 序列化的附件列表）、`sequenceNum`（会话内序号）、`timestamp`

---

### Requirement: 对话上下文恢复接口
系统 SHALL 提供 `GET /api/ai/conversations/{memoryId}/restore` 接口，供前端在页面刷新或切换会话时恢复历史对话。

#### Scenario: 成功恢复对话上下文
- **WHEN** 前端调用 `GET /api/ai/conversations/{memoryId}/restore` 且该会话存在历史记录
- **THEN** 返回 JSON 响应 `{ memoryId, messages: [...], version, recovered: true }`，HTTP 200

#### Scenario: 会话不存在时恢复空上下文
- **WHEN** 前端调用 restore 接口但 memoryId 无历史记录
- **THEN** 返回 `{ memoryId, messages: [], version: 1, recovered: false }`，HTTP 200

#### Scenario: 已删除会话的恢复拒绝
- **WHEN** 前端调用 restore 接口但对应 conversations.status = -1（已删除）
- **THEN** 返回 HTTP 404 或 HTTP 200 但 `recovered: false, reason: "deleted"`

---

### Requirement: 服务重启后自动恢复
系统 SHALL 在服务重启时不需要预热操作，首次用户请求通过惰性加载机制自动恢复上下文。

#### Scenario: 服务重启后首次请求的透明恢复
- **WHEN** 服务重启后用户发送第一条消息（相同的 memoryId）
- **THEN** `loadContext()` 从 PostgreSQL 恢复上下文，用户感知的延迟增加 < 15ms（首次 DB 查询），后续请求由 Redis 缓存加速

#### Scenario: 异步刷写异常不影响用户请求
- **WHEN** PostgreSQL 异步写入失败（如数据库暂时不可达）
- **THEN** 主请求流程不中断（Redis 已写入成功），系统记录 ERROR 日志并触发告警，通过定时重试机制最终一致

---

### Requirement: 缓存 Key 命名空间规范
系统 SHALL 遵循 `astra:conv:{memoryId}` 的 Redis Key 命名空间规范，并为版本号独立维护辅助 Key。

#### Scenario: Key 命名结构
- **WHEN** 系统操作 Redis 缓存
- **THEN** 主 Key 格式为 `astra:conv:{memoryId}`，版本 Key 格式为 `astra:conv:{memoryId}:version`，消息计数 Key 格式为 `astra:conv:{memoryId}:msgcount`

#### Scenario: Key 过期策略
- **WHEN** 缓存写入
- **THEN** 所有相关 Key 设置统一的 EXPIRE 时间（可配置，默认 24 小时），使用 Redis Pipeline 批量执行以减少 RTT

---

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
