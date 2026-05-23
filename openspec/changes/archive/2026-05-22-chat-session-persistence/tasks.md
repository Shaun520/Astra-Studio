## 1. 数据库迁移与 Schema 变更

- [x] 1.1 创建 Flyway 迁移脚本 `V7__add_conversation_metadata.sql`：
  - ALTER TABLE conversations ADD COLUMN title VARCHAR(255) DEFAULT '新对话'
  - ADD COLUMN last_message_preview TEXT DEFAULT ''
  - ADD COLUMN message_count INT DEFAULT 0
  - CREATE INDEX idx_conversations_status_updated ON conversations(status, updated_at DESC)
  - CREATE INDEX idx_conversations_title_search ON conversations USING gin(to_tsvector('simple', title))
  - CREATE INDEX idx_messages_conv_seq ON conversation_messages(conversation_id, sequence_num) ✨ （优化消息分页查询）

- [x] 1.2 更新 [ConversationEntity.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/entity/ConversationEntity.java)：
  - 新增字段：`title`（String）、`lastMessagePreview`（String）、`messageCount`（Integer）
  - 添加 @Version 注解用于乐观锁
  - 新增字段：`deletedAt`（LocalDateTime，软删除时间戳）

---

## 2. 后端 API 实现 —— 会话列表管理

- [x] 2.1 创建 [ConversationQueryService.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/service/conversation/ConversationQueryService.java)：
  - 实现 `listConversations(page, size, keyword)` 方法
    - 使用 Specification 或 QueryDSL 构建动态查询
    - WHERE status != -1 过滤已删除会话
    - ORDER BY updated_at DESC 排序
    - 支持 keyword ILIKE 模糊匹配（title + lastMessage_preview）
  - 实现 `createConversation(memoryId, modelName)` 方法 ✨
    - 检查 memoryId 是否已存在（existsByMemoryId），若存在则抛 EntityExistsException（HTTP 409）
    - 创建新 ConversationEntity 记录：
      - title = "新对话"（默认值）
      - modelName = 参数值或配置默认值
      - status = 1, messageCount = 0
      - createdAt / updatedAt = NOW()
    - 返回保存后的实体
    - @Transactional 保证原子性
  - 实现 `updateTitle(memoryId, title)` 方法
    - 校验 title 长度 ≤ 255 字符
    - XSS 过滤（使用 HtmlUtils.htmlEscape 或自定义正则）
    - 更新 conversations.title + updated_at
  - 实现 `softDelete(memoryId)` 方法
    - UPDATE SET status = -1, deleted_at = NOW()
    - 幂等性处理（若已是 -1 则直接返回成功）
  - 实现 `getMessages(memoryId, page, size, role)` 方法 ✨
    - 验证会话存在且 status != -1，否则抛 NotFoundException
    - 查询 conversation_messages 表（按 sequence_num ASC 排序）
    - 支持可选的 role 过滤参数
    - 返回 Page<MessageEntity> 分页结果
  - 实现 `incrementMessageCount(memoryId)` 方法
    - 原子化递增 message_count
    - 更新 last_message_preview（取最后一条消息前 100 字符）
    - 乐观锁冲突重试机制（@Retryable 或手动循环）

- [x] 2.2 增强 [ConversationController.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/controller/ConversationController.java)：
  - 新增 `POST /api/conversation` 端点 ✨
    - RequestBody（可选）：CreateConversationRequest { memoryId?, modelName? }
    - 若不传 memoryId，自动生成 UUID v4
    - 若不传 modelName，使用配置默认值（conversation.default-model）
    - 调用 conversationQueryService.createConversation()
    - 返回 HTTP 201 Created + ConversationDTO
    - 异常处理：memoryId 已存在 → 409 Conflict
  - 新增 `GET /api/conversation` 端点
    - 参数：page（默认0）、size（默认20）、keyword（可选）
    - 返回 PageResult<ConversationDTO>（自定义 DTO，避免暴露实体）
  - 新增 `GET /api/conversation/{memoryId}/messages` 端点 ✨
    - 参数：page（默认0）、size（默认50）、role（可选：USER/ASSISTANT/SYSTEM）
    - 返回 PageResult<MessageDTO>（分页消息列表）
    - 验证会话存在且未删除（status != -1），否则返回 404
  - 新增 `PUT /api/conversation/{memoryId}/title` 端点
    - RequestBody：{ "title": "string" }
    - 返回 204 No Content / 400 Bad Request / 404 Not Found
  - 新增 `DELETE /api/conversation/{memoryId}` 端点
    - 返回 204 No Content

- [x] 2.3 创建 [ConversationDTO.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/dto/response/ConversationDTO.java)：
  - 字段：memoryId, title, lastMessagePreview, modelName, messageCount, updatedAt, status
  - 添加 `fromEntity(ConversationEntity)` 静态转换方法
  - 格式化 updatedAt 为相对时间字符串（可选，或前端处理）

- [x] 2.3.5 创建 [MessageDTO.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/dto/response/MessageDTO.java) ✨：
  - 字段：id (Long), role (String: USER/ASSISTANT/SYSTEM), content (String), thinkingContent (String, nullable), attachments (List<String>, nullable), sequenceNum (Integer), timestamp (LocalDateTime)
  - 添加 `fromEntity(MessageEntity)` 静态转换方法
  - 注意：attachments 字段需从 JSON 字符串反序列化为 List

- [x] 2.3.6 创建 [CreateConversationRequest.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/dto/request/CreateConversationRequest.java) ✨：
  - 字段：memoryId (String, 可选，前端自定义 session ID)
  - 字段：modelName (String, 可选，指定初始模型，默认使用系统配置)
  - 添加 @NotBlank 校验（若传了 memoryId 则不能为空）
  - 添加 @Size(max = 64) 限制 memoryId 长度
  - getters & setters（或使用 Lombok @Data）

- [x] 2.4 创建 [PageResult.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/dto/response/PageResult.java) 通用分页包装类：
  - 字段：content (List<T>), totalElements, totalPages, currentPage, size

---

## 3. 后端服务增强 —— 会话创建与元数据同步

- [x] 3.1 增强 [ChatService.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/service/chat/ChatService.java) 的 streamChat() 方法：
  - 在方法入口处检查 memoryId 是否存在于 conversations 表
  - 若不存在，调用 conversationQueryService.createConversation(memoryId, modelName)
  - 记录 isFirstReply 标志（用于后续标题生成触发）

- [x] 3.2 增强 [ConversationPersistenceService.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/service/conversation/ConversationPersistenceService.java) 的 saveContext() 方法：
  - 在 Redis 写入成功后，调用 conversationQueryService.incrementMessageCount(memoryId)
  - 传入 ctx.messages 列表用于提取 lastMessagePreview
  - 在同一事务中更新元数据（确保原子性）

- [x] 3.3 在 ConversationRepository 中新增查询方法：
  - `findByStatusNotOrderByUpdatedAtDesc(Pageable pageable)`
  - `findByStatusNotAndTitleContainingIgnoreCase(String keyword, Pageable pageable)`
  - `existsByMemoryId(String memoryId)`

- [x] 3.4 在 MessageRepository 中新增消息查询方法 ✨：
  - `findByConversationOrderBySequenceNumAsc(ConversationEntity conversation, Pageable pageable)` — 基础分页查询
  - `findByConversationAndRoleOrderBySequenceNumAsc(ConversationEntity conversation, String role, Pageable pageable)` — 按角色过滤
  - 确保 conversation_messages 表有索引：`CREATE INDEX idx_messages_conv_seq ON conversation_messages(conversation_id, sequence_num)`

---

## 4. 标题自动生成服务

- [x] 4.1 创建 [TitleGeneratorService.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/service/title/TitleGeneratorService.java)：
  - 实现 `generate(userMessage, assistantReply): String` 方法
  - 构造 LLM Prompt（参考 Design Decision 2 的模板）
  - 调用 DashScope text-generation-v3 API（复用现有的 OpenAiClient 或新建专用客户端）
  - 配置：temperature=0.3, max_tokens=20, timeout=2s
  - 结果后处理：去除首尾空白、截断至 30 字符

- [x] 4.2 实现规则提取降级方案（同文件中）：
  - 新增 `generateFallback(userMessage): String` 方法
  - 提取前 20 中文字符 / 40 英文字符
  - 正则清洗特殊字符：`[^\u4e00-\u9fa5a-zA-Z0-9\s，。！？、：；""''（）]`
  - 默认值兜底："新对话"

- [x] 4.3 集成到 ChatService.subscribeTokenStream() 回调中：
  - 在 onCompleteResponse() 中判断 isFirstReply && autoGenerateTitle
  - 调用 titleGenerator.generate()
  - catch 异常时降级到 generateFallback()
  - 调用 conversationQueryService.updateTitle(memoryId, generatedTitle)

- [ ] 4.4 配置项支持（application.yaml）：
  ```yaml
  conversation:
    title:
      auto-generate: true
      async: false  # 同步模式（默认）
      llm-model: text-generation-v3
      max-length: 30
  ```

- [ ] 4.5 （可选）异步线程池配置（AsyncConfig.java）：
  - 若启用异步模式，新增 `@Bean("titleGeneratorExecutor")`
  - corePoolSize=2, maxPoolSize=5, queueCapacity=50

---

## 5. 定时清理任务（软删除数据物理删除）

- [x] 5.1 创建 [ConversationCleanupTask.java](../Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/scheduled/ConversationCleanupTask.java)：
  - @Scheduled(cron = "0 0 3 * * ?") 每天 3AM 执行
  - 查询 deleted_at < NOW() - 30天的记录
  - 批量物理删除（conversations + snapshots + messages 三表级联）
  - 记录删除数量到日志

- [x] 5.2 在 ConversationRepository 中新增：
  - `hardDeleteOlderThan(LocalDateTime threshold)` 自定义 SQL
  - 使用 @Modifying + @Query 注解

---

## 6. 前端 API 层扩展

- [x] 6.1 更新 [services/api.ts](../Astra-Studio/Astra-Studio/src/services/api.ts)：
  - 新增 `createConversation(memoryId?: string, modelName?: string): Promise<ConversationItem>` ✨
    - POST /api/conversation
    - Body（可选）：{ memoryId, modelName }
    - 若不传参数，后端自动生成 UUID 和使用默认模型
    - 返回新建的 ConversationItem（含服务端生成的 memoryId）
  - 新增 `getConversations(page?: number, size?: number, keyword?: string): Promise<PageResult<ConversationItem>>`
    - GET /api/conversation?page={}&size={}&keyword={}
    - 定义 ConversationItem 接口（memoryId, title, lastMessagePreview, modelName, messageCount, updatedAt）
  - 新增 `getConversationMessages(memoryId: string, page?: number, size?: number, role?: string): Promise<PageResult<MessageItem>>` ✨
    - GET /api/conversation/{memoryId}/messages?page={}&size={}&role={}
    - 定义 MessageItem 接口（id, role, content, thinkingContent, attachments[], sequenceNum, timestamp）
  - 新增 `updateConversationTitle(memoryId: string, title: string): Promise<void>`
    - PUT /api/conversation/{memoryId}/title
    - Body: { title }
  - 新增 `deleteConversation(memoryId: string): Promise<void>`
    - DELETE /api/conversation/{memoryId}

- [x] 6.2 新增 TypeScript 类型定义 [types/conversation.ts](../Astra-Studio/Astra-Studio/src/types/conversation.ts)：
  ```typescript
  export interface ConversationItem {
    memoryId: string
    title: string
    lastMessagePreview: string
    modelName: string
    messageCount: number
    updatedAt: string
    status: number
  }

  export interface MessageItem {  ✨
    id: number
    role: 'USER' | 'ASSISTANT' | 'SYSTEM'
    content: string
    thinkingContent?: string | null
    attachments?: string[] | null
    sequenceNum: number
    timestamp: string
  }

  export interface PageResult<T> {
    content: T[]
    totalElements: number
    totalPages: number
    currentPage: number
    size: number
  }
  ```

---

## 7. 前端 UI 组件实现

- [x] 7.1 重构 [AppSidebar.vue](../Astra-Studio/Astra-Studio/src/components/AppSidebar.vue) 的"最近"Tab：
  - 替换硬编码的 libraryItems[0]（count: 28）为真实会话列表
  - inject('conversationList') 和 inject('refreshConversations')
  - onMounted 时调用 refreshConversations()
  - 渲染 v-for 循环展示 ConversationItem 列表
  - 每项样式：模型图标 + 标题（单行截断）+ 预览文本（灰色双行）+ 相对时间
  - 当前活跃会话高亮（v-bind:class="{ 'active': item.memoryId === currentSessionId }"）
  - 点击事件：emit('restore', item.memoryId)

- [x] 7.2 创建 [components/ConversationCard.vue](../Astra-Studio/Astra-Studio/src/components/ConversationCard.vue) 单个会话卡片组件：
  - Props: conversation (ConversationItem), isActive (boolean)
  - 插槽：左侧图标区域（根据 modelName 动态渲染 DeepSeek/Qwen/GLM 图标）
  - 右键菜单：@contextmenu.prevent 显示浮动菜单
  - 菜单项：✏️ 重命名、📌 固定收藏（disabled）、🗑️ 删除

- [x] 7.3 创建 [components/ConversationSearch.vue](../Astra-Studio/Astra-Studio/src/components/ConversationSearch.vue) 搜索框组件：
  - 输入框 + 搜索图标
  - watch input 使用 debounce（300ms）防抖
  - emit('search', keyword) 给父组件
  - 清空按钮（keyword 非空时显示）

- [x] 7.4 创建 [components/EmptyState.vue](../Astra-Studio/Astra-Studio/src/components/EmptyState.vue) 空状态组件：
  - MessageSquare 图标 + 渐变透明度动画
  - 提示文案："还没有对话记录"
  - CTA 按钮："开始第一个对话"

- [x] 7.5 创建 [components/SkeletonLoader.vue](../Astra-Studio/Astra-Studio/src/components/SkeletonLoader.vue) 加载骨架屏：
  - 3 行 placeholder shimmer 效果（CSS animation）
  - 用于 API 请求期间的 loading 状态

- [x] 7.6 创建 [components/ContextMenu.vue](../Astra-Studio/Astra-Studio/src/components/ContextMenu.vue) 浮动右键菜单：
  - Props: visible, x, y 坐标, items (MenuItem[])
  - 点击外部自动关闭（@click.stop + document 监听器）
  - 菜单项：icon + label + disabled + divider 分隔线

- [x] 7.7 创建 [components/DeleteConfirmDialog.vue](../Astra-Studio/Astra-Studio/src/components/DeleteConfirmDialog.vue) 删除确认对话框：
  - Modal 样式（fixed 居中遮罩层）
  - 标题 + 内容文案 + 取消/确认按钮
  - emit('confirm') / emit('cancel')

- [x] 7.8 创建 [components/InlineTitleEditor.vue](../Astra-Studio/Astra-Studio/src/components/InlineTitleEditor.vue) 内联标题编辑器：
  - Props: initialTitle
  - 渲染为 <input autofocus> 或 <span>（根据 isEditing 状态切换）
  - Enter 确认，Escape 取消
  - emit('save', newTitle) / emit('cancel')

- [x] 7.9 创建 [components/MessageHistoryView.vue](../Astra-Studio/Astra-Studio/src/components/MessageHistoryView.vue) 消息历史浏览组件 ✨：
  - Props: memoryId (string)
  - 功能：展示某会话的完整聊天记录（只读模式，不可继续聊天）
  - 分页加载逻辑：
    - 初始加载最近 50 条消息（page=0, size=50, 按 sequenceNum DESC）
    - 向上滚动到顶部时触发 `loadMore()` (page+1)
    - 合并新数据到列表头部（unshift，保持时间正序）
  - 消息渲染：复用现有 ChatMsg 组件的 Markdown 渲染能力
    - USER 消息：右侧对齐，蓝色气泡
    - ASSISTANT 消息：左侧对齐，支持思维链折叠
    - thinkingContent: 默认折叠，点击展开
  - 加载状态：顶部显示 SkeletonLoader 或 spinner
  - 空状态："该会话暂无消息"
  - 角色过滤栏（可选）：Tab 切换 "全部 / 用户 / AI"
    - 调用 getConversationMessages(memoryId, page, size, role)

---

## 8. 前端状态管理与交互逻辑

- [x] 8.1 增强 [App.vue](../Astra-Studio/Astra-Studio/App.vue) 的 provide/inject 状态：
  - 新增 `currentSessionId` ref（string）
  - 新增 `conversationList` ref（ConversationItem[]）
  - 新增 `refreshConversations()` 异步函数
  - 新增 `handleRestoreConversation(memoryId)` 函数
    - 调用 restoreConversation API（用于继续聊天）
    - 清空 messages 数组
    - push 恢复的消息到 ChatMsg 区域
    - 更新 currentSessionId
  - 新增 `handleViewHistory(memoryId)` 函数 ✨
    - 调用 getConversationMessages API 获取消息列表（用于只读浏览）
    - 切换视图模式：从 ChatMode 切换到 HistoryMode
    - 显示 MessageHistoryView 组件，传入 memoryId
  - 新增 `viewMode` ref：'chat' | 'history' （控制显示 Composer 还是 MessageHistoryView）
  - provide 所有状态给子组件

- [x] 8.2 实现新建对话逻辑（Composer.vue 或 App.vue）：
  - 点击"新建对话"按钮时：
    - **调用 `createConversation()` API** ✨（显式创建，而非仅前端生成 UUID）
      - 可选传入当前选择的 modelName
      - 后端返回完整的 ConversationItem（含服务端生成的 memoryId）
    - 清空当前消息列表
    - **立即刷新侧边栏**：`refreshConversations()` 或乐观插入到列表头部
    - 更新 currentSessionId 为新会话的 memoryId
    - localStorage.setItem('lastSessionId', newMemoryId)
    - 聚焦输入框，准备接收用户输入
  - **错误处理**：若 API 调用失败（网络异常/409 冲突），降级为前端本地 UUID（兼容离线场景）

- [x] 8.3 实现页面刷新恢复逻辑（App.vue onMounted）：
  - const lastSessionId = localStorage.getItem('lastSessionId')
  - if (lastSessionId) 调用 handleRestoreConversation(lastSessionId)

- [x] 8.4 实现标题生成完成后的 UI 刷新：
  - 通过 provide/inject 的回调函数通知侧边栏
  - 或者使用 mitt 事件总线（若项目已引入）
  - 将"新对话..."替换为真实标题

---

## 9. 测试与验证

- [ ] 9.1 后端单元测试：
  - ConversationQueryServiceTest: 测试分页、搜索、边界条件
  - TitleGeneratorServiceTest: 测试 LLM 生成、降级逻辑、特殊字符清洗
  - ConversationControllerTest: 测试 API 端点（MockMvc）

- [ ] 9.2 后端集成测试：
  - 测试完整的 saveContext() → 元数据更新流程
  - 测试并发场景下的乐观锁冲突与重试
  - 测试软删除后的查询过滤

- [ ] 9.3 前端组件测试（Vitest）：
  - ConversationCard: 测试点击、右键菜单、高亮状态
  - ConversationSearch: 测试防抖、清空、空结果提示
  - DeleteConfirmDialog: 测试确认/取消事件

- [ ] 9.4 E2E 测试（Playwright/Cypress 可选）：
  - 用户打开应用 → 侧边栏显示历史会话列表
  - 点击某会话 → 恢复完整聊天记录
  - 发送新消息 → 会话列表实时更新
  - 右键删除 → 确认对话框 → 列表移除

---

## 10. 性能优化与监控

- [ ] 10.1 数据库性能优化：
  - 验证 EXPLAIN ANALYZE 查询计划（确认索引命中）
  - 监控慢查询日志（阈值 > 100ms）

- [ ] 10.2 Redis 缓存策略（可选）：
  - 实现热门会话列表缓存（key: astra:conv:list:page:{page}）
  - TTL = 5 分钟
  - 写操作时主动失效（Cache-Aside）

- [ ] 10.3 前端性能优化：
  - 虚拟滚动（vue-virtual-scroller）：当会话数 > 100 时启用
  - 图片懒加载（若有头像/缩略图）
  - 防抖/节流统一封装到 utils/debounce.ts

- [ ] 10.4 监控指标接入：
  - Prometheus Counter: conversation_list_queries_total
  - Prometheus Histogram: conversation_query_duration_seconds
  - 日志：记录标题生成成功率/失败率

---

## 11. 文档与部署准备

- [ ] 11.1 更新 README.md（如需要）：
  - 新增"会话管理"功能说明
  - API 文档链接（Swagger/OpenAPI 3.0）

- [ ] 11.2 数据库迁移脚本验证：
  - 本地开发环境执行 V7 脚本
  - 验证向后兼容性（旧数据自动填充默认值）

- [ ] 11.3 部署清单：
  - 备份数据库（pg_dump）
  - 执行 Flyway 迁移
  - 部署新版本 JAR 包
  - 验证 API 健康检查
  - 监控错误日志 24h
