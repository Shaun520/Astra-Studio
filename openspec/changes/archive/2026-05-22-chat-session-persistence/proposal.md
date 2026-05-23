## Why

当前 Astra Studio 项目已实现对话上下文的后端持久化（Redis + PostgreSQL 三级缓存），但**缺少完整的会话生命周期管理能力**：

1. **前端无会话列表**：用户无法查看历史对话记录，页面刷新后对话丢失
2. **无会话元数据管理**：缺少会话标题自动生成、编辑、删除等功能
3. **前后端断层**：后端 `ConversationController` 仅提供 restore 接口，前端未对接
4. **用户体验不完整**：用户期望像 ChatGPT/Claude 那样拥有"最近对话"侧边栏

**目标**：实现端到端的聊天会话记录系统，让用户能够查看、切换、管理历史对话。

---

## What Changes

### 后端增强

- 新增 `GET /api/conversation` 接口：返回当前用户的会话列表（分页、排序）
- 新增 `PUT /api/conversation/{memoryId}/title` 接口：更新会话标题（支持 AI 自动生成或手动编辑）
- 新增 `DELETE /api/conversation/{memoryId}` 接口：软删除会话（标记 status=-1，保留数据 30 天）
- 增强 `ConversationQueryService`：添加会话列表查询逻辑（按 updated_at 降序，过滤已删除）
- 增强 `ConversationEntity`：新增 title 字段（首条消息摘要 / AI 生成）

### 前端实现

- **AppSidebar.vue 改造**：
  - "最近" Tab 展示真实会话列表（替换硬编码的 count: 28）
  - 点击会话项触发 `restoreConversation()` 并切换到该对话
  - 右键菜单：重命名 / 删除 / 固定到收藏

- **新增 ConversationList 组件**（可选）：
  - 会话卡片：显示标题、预览文本、时间戳、模型图标
  - 搜索框：按关键词过滤会话
  - 空状态引导："开始你的第一个对话"

- **api.ts 扩展**：
  - `getConversations(page, size)` 获取会话列表
  - `updateConversationTitle(memoryId, title)` 更新标题
  - `deleteConversation(memoryId)` 删除会话

### 数据流优化

- **会话创建时机调整**：在 `handleSend()` 首次发送消息时调用 `POST /api/conversation` 创建记录（而非仅在前端生成 session ID）
- **自动标题生成**：在第一条 assistant 回复完成后，提取前 20 字作为默认标题（或调用 LLM 生成简洁摘要）
- **实时更新机制**：每次消息保存成功后，更新会话的 `updated_at` 和 `message_count`

---

## Capabilities

### New Capabilities

- **conversation-list-management**: 会话列表的 CRUD 操作（查询、重命名、删除、恢复），包括后端 API 和前端 UI 交互
- **session-title-auto-generation**: 基于 LLM 或规则引擎自动生成会话标题（首条消息摘要 / 关键词提取）
- **conversation-history-ui**: 前端会话历史界面（侧边栏列表、搜索过滤、空状态处理）

### Modified Capabilities

- **conversation-persistence**:
  - **变更内容**：扩展 REQUIREMENTS，新增会话元数据（title、message_count、last_message_preview）的持久化策略
  - **影响范围**：ConversationEntity 表结构变更、saveContext() 逻辑增强

---

## Impact

### 受影响的代码模块

| 模块 | 文件 | 变更类型 |
|------|------|---------|
| **后端 Controller** | `ConversationController.java` | 新增 4 个接口 (GET list / GET messages / PUT title / DELETE) |
| **后端 Service** | `ConversationQueryService.java` | 新建，封装列表查询 + 消息查询逻辑 |
| **后端 Service** | `ConversationPersistenceService.java` | 增强 saveContext()，同步更新 message_count |
| **后端 Entity** | `ConversationEntity.java` | 新增 title、lastMessagePreview 字段 |
| **数据库迁移** | `V7__add_conversation_metadata.sql` | ALTER TABLE conversations ADD COLUMN |
| **前端 API** | `services/api.ts` | 新增 4 个函数 |
| **前端组件** | `components/AppSidebar.vue` | 重构"最近"Tab，接入真实数据 |
| **前端组件** | `components/ConversationCard.vue` | 新建，会话卡片组件 |
| **前端 Store** | `App.vue` (provide/inject) | 新增 currentSessionTitle 状态 |

### API 变更清单

```
POST   /api/conversation                        # 新建会话（新增）✨
GET    /api/conversation?page=0&size=20          # 会话列表（新增）
GET    /api/conversation/{id}/messages?page=0&size=50&role=  # 获取某会话的消息记录（新增）✨
PUT    /api/conversation/{id}/title              # 更新标题（新增）
DELETE /api/conversation/{id}                    # 软删除（新增）
GET    /api/conversation/{id}/restore            # 已有，保持不变（用于恢复上下文）
POST   /api/chat                                # 已有，需在首次调用时关联 conversation_id
```

### 依赖与配置

- **新增依赖**：无（复用已有的 Spring Data JPA + Redis）
- **配置变更**：`application.yaml` 新增 `conversation.title.auto-generate: true`
- **数据库兼容性**：需 Flyway 迁移脚本（向后兼容，新字段 nullable）

### 性能影响评估

- **会话列表查询**：PostgreSQL INDEX on (status, updated_at)，< 50ms
- **标题自动生成**：可选异步任务（@Async），不影响主流程
- **Redis 缓存**：可考虑缓存热门会话列表（TTL=5min）

---

## 成功标准

1. 用户能在侧边栏看到最近 20 条历史对话（含标题和预览）
2. 点击"新建对话"按钮能**立即创建空会话并显示在侧边栏**（显式创建，非隐式）✨
3. 点击任意会话能**查看该会话的完整聊天消息记录**（分页加载、角色过滤）✨
4. 点击任意会话能恢复完整对话上下文（用于继续聊天）
5. 能手动修改会话标题（即时生效）
6. 能删除会话（30 天内可恢复）
7. 新对话的首条回复后自动生成标题（延迟 < 2s）
8. 页面刷新后不丢失当前对话状态
9. 长会话支持**滚动加载历史消息**（默认 50 条/页，避免一次性加载过多）
10. **完整的 CRUD 操作**：Create(POST) / Read(GET list+messages) / Update(PUT title) / Delete(DELETE) ✨
