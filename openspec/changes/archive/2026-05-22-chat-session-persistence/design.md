## Context

### 当前状态

Astra Studio 项目已具备**对话上下文持久化能力**（2026-05-19 已实现）：
- **后端**：`ConversationPersistenceService` 实现 Redis (L1) + PostgreSQL Snapshot (L2) + Messages Rebuild (L3) 三级缓存
- **数据库**：`conversations`、`conversation_snapshots`、`conversation_messages` 三张表
- **API**：仅提供 `GET /api/conversation/{memoryId}/restore` 单点恢复接口
- **前端**：AppSidebar 有"最近/收藏/项目"占位 UI，但未接入真实数据

### 痛点分析

1. **用户无法查看历史对话**：页面刷新后 session ID 丢失，无法恢复之前的聊天记录
2. **无会话元数据管理**：缺少标题、时间戳、消息数量等展示信息
3. **前后端功能断层**：后端有数据但无查询接口，前端有 UI 但无数据源
4. **对标产品差距**：ChatGPT/Claude 均提供完整的会话历史侧边栏

### 约束条件

- **向后兼容**：不能破坏现有的 `restoreConversation()` 接口和 Redis 缓存机制
- **性能要求**：会话列表查询 < 50ms，不影响主聊天流程的延迟
- **技术栈限制**：复用 Spring Data JPA + Redis，不引入新框架
- **前端兼容**：Vue3 Composition API + TailwindCSS，保持现有设计风格

---

## Goals / Non-Goals

**Goals:**

1. ✅ 实现完整的会话生命周期管理（创建 → 列表 → 恢复 → 删除）
2. ✅ 提供用户友好的会话列表 UI（侧边栏集成、搜索过滤、空状态引导）
3. ✅ 支持会话标题自动生成（首条消息摘要 / LLM 智能生成）
4. ✅ 确保数据一致性（消息保存与元数据更新原子化操作）
5. ✅ 保持现有代码架构风格（工厂模式、可选依赖注入）

**Non-Goals:**

- ❌ 会话分享/导出功能（后续迭代）
- ❌ 多租户隔离（当前单用户场景）
- ❌ 会话标签/分类系统（复杂度过高）
- ❌ 实时协作编辑（非核心需求）

---

## Decisions

### Decision 1: 后端 API 设计 - RESTful 风格 + 分页支持

**选择方案**: 标准的 RESTful API 设计，遵循 Spring MVC 规范

```java
// ConversationController.java 新增接口
@GetMapping
public ResponseEntity<PageResult<ConversationDTO>> listConversations(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(required = false) String keyword);

@PutMapping("/{id}/title")
public ResponseEntity<Void> updateTitle(@PathVariable String id, @RequestBody TitleRequest request);

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteConversation(@PathVariable String id);
```

**替代方案考虑**:
- GraphQL: 过度工程，当前查询简单无需灵活查询语言
- gRPC: 前端不支持，增加协议转换层

**决策理由**:
- 与现有 `ChatController` 风格一致（REST + SSE 混合）
- 分页参数标准化（page/size），便于前端分页加载
- keyword 参数支持模糊搜索（按 title 或 last_message_preview 匹配）

---

### Decision 1.2: 新建会话接口设计 - 显式创建 vs 隐式创建

**选择方案**: 提供 `POST /api/conversation` 显式创建接口，同时保留隐式创建作为降级方案

```java
// ConversationController.java 新增接口
@PostMapping
public ResponseEntity<ConversationDTO> createConversation(
    @RequestBody(required = false) CreateConversationRequest request) {
    
    String memoryId = (request != null && request.getMemoryId() != null)
        ? request.getMemoryId()
        : UUID.randomUUID().toString();
    
    String modelName = (request != null && request.getModelName() != null)
        ? request.getModelName()
        : defaultModelName;  // 从配置文件读取
    
    ConversationEntity conv = conversationQueryService.createConversation(memoryId, modelName);
    return ResponseEntity.status(201).body(ConversationDTO.fromEntity(conv));
}
```

**两种创建模式对比**:

| 模式 | 触发时机 | 适用场景 | 优点 | 缺点 |
|------|---------|---------|------|------|
| **显式创建（推荐）** | 用户点击"新建对话"按钮时立即调用 POST API | 前端主动管理会话生命周期 | 侧边栏立即可见、支持提前设置模型、符合 RESTful 规范 | 需要额外一次 API 调用 |
| **隐式创建（降级）** | 首次发送消息时自动检测并创建 | 兼容旧逻辑、容错兜底 | 无需前端改动、用户体验无缝 | 侧边栏延迟显示、无法预设参数 |

**实现细节**:
```java
// ConversationQueryService.java
@Transactional
public ConversationEntity createConversation(String memoryId, String modelName) {
    // 1. 检查是否已存在（幂等性校验）
    if (conversationRepo.existsByMemoryId(memoryId)) {
        throw new EntityExistsException("Conversation already exists: " + memoryId);
    }
    
    // 2. 创建新记录
    ConversationEntity conv = new ConversationEntity();
    conv.setMemoryId(memoryId);
    conv.setTitle("新对话");  // 默认标题，后续由 TitleGeneratorService 覆盖
    conv.setModelName(modelName);
    conv.setStatus(1);  // 正常状态
    conv.setMessageCount(0);
    conv.setCreatedAt(LocalDateTime.now());
    conv.setUpdatedAt(LocalDateTime.now());
    
    return conversationRepo.save(conv);
}
```

**请求体 DTO 设计**:
```java
public class CreateConversationRequest {
    private String memoryId;   // 可选：前端自定义 session ID
    private String modelName;  // 可选：指定初始模型，默认使用系统配置
    
    // getters & setters
}
```

**替代方案考虑**:
- 仅依赖隐式创建: ❌ 无法满足"提前创建空会话"需求，且不符合 RESTful CRUD 完整性
- 仅依赖显式创建: ❌ 破坏向后兼容性（旧版前端不调用该接口会导致聊天功能失效）

**决策理由**:
- **双模式并存**: 显式创建为主流程，隐式创建作为容错兜底（ChatService.streamChat() 中检测）
- **灵活性**: 前端可选择何时创建（点击按钮 vs 发消息前），适应不同产品交互设计
- **幂等性保证**: 409 Conflict 错误码处理重复创建场景

---

### Decision 1.5: 会话消息记录查询接口设计 - 区分浏览与恢复

**选择方案**: 独立的 `GET /api/conversation/{id}/messages` 接口，专门用于历史消息浏览

```java
// ConversationController.java 新增接口
@GetMapping("/{id}/messages")
public ResponseEntity<PageResult<MessageDTO>> getConversationMessages(
    @PathVariable String id,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size,
    @RequestParam(required = false) String role);  // 可选：USER/ASSISTANT/SYSTEM 过滤
```

**与 restore 接口的区别**:

| 维度 | `GET /{id}/restore` | `GET /{id}/messages` |
|------|---------------------|---------------------|
| **用途** | 恢复对话上下文（继续聊天） | 浏览历史消息（只读查看） |
| **返回格式** | ConversationContext (Kryo 序列化对象) | PageResult<MessageDTO> (分页列表) |
| **数据来源** | Redis L1 → PostgreSQL Snapshot L2 → Messages Rebuild L3 | 直接查 conversation_messages 表 |
| **性能优化** | 三级缓存加速（~1ms） | 数据库分页查询（~20ms） |
| **适用场景** | 页面刷新后恢复当前会话 | 点击历史会话查看聊天记录 |

**实现细节**:
```java
// ConversationQueryService.java
public Page<MessageEntity> getMessages(String memoryId, Pageable pageable, String role) {
    // 1. 验证会话存在且未删除
    ConversationEntity conv = conversationRepo.findByMemoryId(memoryId)
        .orElseThrow(() -> new NotFoundException("Conversation not found"));
    if (conv.getStatus() == -1) {
        throw new NotFoundException("Conversation has been deleted");
    }

    // 2. 构建动态查询
    if (role != null && !role.isEmpty()) {
        return messageRepo.findByConversationAndRoleOrderBySequenceNumAsc(conv, role, pageable);
    } else {
        return messageRepo.findByConversationOrderBySequenceNumAsc(conv, pageable);
    }
}
```

**替代方案考虑**:
- 复用 restore 接口返回所有消息: ❌ 不支持分页，长会话（100+条）会导致性能问题和内存溢出
- 在 listConversations 接口中内嵌 messages: ❌ 违反 RESTful 单一职责原则，且会增加列表接口的复杂度
- WebSocket 实时推送: ❌ 当前场景是静态历史查询，非实时场景，SSE 已足够

**决策理由**:
- **关注点分离**: 浏览 vs 恢复是两种不同的语义和性能需求
- **可扩展性**: 未来可基于此接口添加消息搜索、导出等功能（如 ?keyword=排序）
- **用户体验**: 支持滚动加载（infinite scroll），避免一次性加载大量数据导致前端卡顿

---

### Decision 2: 会话标题自动生成策略 - 双模式降级

**选择方案**: LLM 优先 + 规则兜底的双模式策略

```
触发时机: 第一条 assistant 回复完成后
    ↓
[模式 A] LLM 生成（如果 conversation.title.auto-generate=true 且 API 可用）
    ↓ 调用 text-generation-v3 生成 ≤15 字标题
    ↓ 成功则保存到 conversations.title
    ↓ 失败或超时 (>2s)
    ↓
[模式 B] 规则提取（降级方案）
    ↓ 提取 user 首条消息前 20 字作为默认标题
    ↓ 截断至 30 字符，去除特殊字符
    ↓ 保存到 conversations.title
```

**实现位置**:
- **同步路径**（推荐）：在 `ChatService.subscribeTokenStream()` 的 `onCompleteResponse()` 回调中调用
- **异步路径**（备选）：通过 `@Async("titleGeneratorExecutor")` 异步任务执行

**代码示例**:
```java
// ChatService.java 增强
.onCompleteResponse(response -> {
    // ... 现有的 complete 逻辑 ...

    if (isFirstReply && autoGenerateTitle) {
        try {
            String generatedTitle = titleGenerator.generate(userMessageText, response.text());
            conversationQueryService.updateConversationTitle(memoryId, generatedTitle);
        } catch (Exception e) {
            String fallbackTitle = extractFallbackTitle(userMessageText);
            conversationQueryService.updateConversationTitle(memoryId, fallbackTitle);
        }
    }
})
```

**替代方案考虑**:
- 仅使用规则提取: 缺乏语义理解（如"帮我写个排序算法"→"排序算法实现"更友好）
- 仅使用 LLM: 增加 API 调用成本和延迟，且依赖外部服务可用性

**决策理由**:
- 平衡用户体验（智能标题）与系统可靠性（规则兜底）
- 符合"优雅降级"的设计原则（参考现有 RAG Fallback 机制）
- 可配置开关（`conversation.title.auto-generate`），方便灰度发布

---

### Decision 3: 数据库 Schema 变更 - 向后兼容的增量迁移

**选择方案**: Flyway 迁移脚本 + nullable 字段

```sql
-- V7__add_conversation_metadata.sql
ALTER TABLE conversations
ADD COLUMN title VARCHAR(255) DEFAULT '新对话',
ADD COLUMN last_message_preview TEXT DEFAULT '',
ADD COLUMN message_count INT DEFAULT 0;

CREATE INDEX idx_conversations_status_updated
ON conversations(status, updated_at DESC);

CREATE INDEX idx_conversations_title_search
ON conversations USING gin(to_tsvector('simple', title));
```

**字段说明**:

| 字段 | 类型 | 默认值 | 用途 |
|------|------|--------|------|
| title | VARCHAR(255) | '新对话' | 会话标题（自动/手动设置） |
| last_message_preview | TEXT | '' | 最后一条消息预览（用于列表展示） |
| message_count | INT | 0 | 消息计数（避免 COUNT 查询） |

**替代方案考虑**:
- 新建 conversation_metadata 表: 增加 JOIN 复杂度，违反范式但提升性能
- JSONB 字段存储元数据: 查询灵活性降低，索引效率差

**决策理由**:
- nullable + default 值保证向后兼容（旧数据自动填充默认值）
- GIN 索引加速全文搜索（title 模糊匹配）
- 复合索引优化常见查询（按 status 过滤 + updated_at 排序）

---

### Decision 4: 前端状态管理 - provide/inject 轻量级方案

**选择方案**: Vue3 Composition API 的 provide/inject 模式（保持现有架构）

```typescript
// App.vue (Provider)
const currentSessionId = ref<string>('')
const conversationList = ref<ConversationItem[]>([])

provide('currentSessionId', currentSessionId)
provide('conversationList', conversationList)
provide('refreshConversations', async () => {
  conversationList.value = await getConversations()
})

// AppSidebar.vue (Consumer)
const conversationList = inject('Ref<ConversationItem[]>')!
const refreshConversations = inject<() => Promise<void>>()!

onMounted(() => refreshConversations())
```

**替代方案考虑**:
- Pinia/Vuex: 引入额外依赖，当前状态简单无需全局 Store
- Event Bus: 组件通信混乱，难以追踪数据流

**决策理由**:
- 与现有 `selectedModel`、`startNewSession` 状态管理方式一致
- 无需安装新包，保持构建体积最小化
- 类型安全（TypeScript 泛型 inject）

---

### Decision 5: 删除策略 - 软删除 + 延迟清理

**选择方案**: 软删除（status=-1）+ 定时任务清理（30 天后物理删除）

```java
// ConversationController.java
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteConversation(@PathVariable String id) {
    conversationQueryService.softDelete(id);  // UPDATE SET status=-1, deleted_at=NOW()
    return ResponseEntity.noContent().build();
}

// ScheduledTask.java (新增)
@Scheduled(cron = "0 0 3 * * ?")  // 每天 3AM 执行
public void cleanupDeletedConversations() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(30);
    conversationRepo.hardDeleteOlderThan(threshold);
}
```

**替代方案考虑**:
- 物理删除立即执行: 用户误删无法恢复，数据丢失风险高
- 归档到独立表: 增加存储成本，恢复流程复杂

**决策理由**:
- 符合 GDPR"被遗忘权"要求（30 天后彻底删除）
- 用户可在 30 天内撤销删除操作（未来可扩展"回收站"功能）
- 不影响活跃数据的查询性能（WHERE status != -1 过滤）

---

## Risks / Trade-offs

### Risk 1: 会话列表查询性能退化

**风险描述**: 当用户拥有 >1000 个会话时，`ORDER BY updated_at DESC` 可能变慢（全表扫描）

**缓解措施**:
- ✅ 已添加复合索引 `(status, updated_at DESC)` 覆盖排序查询
- ✅ 默认返回最近 20 条（size=20），避免大结果集
- ✅ 未来可引入 Redis 缓存热门列表（TTL=5min）

**监控指标**:
- P95 查询延迟 < 100ms
- PostgreSQL slow query log 监控

---

### Risk 2: 标题生成增加响应时间

**风险描述**: LLM 生成标题可能耗时 1-3s，影响用户体验感知

**缓解措施**:
- ✅ 采用异步模式（@Async），不阻塞 SSE 流式输出完成事件
- ✅ 规则降级方案保证 < 10ms 兜底响应
- ✅ 前端乐观更新：先显示"新对话"，后台替换为真实标题

**监控指标**:
- 标题生成成功率 > 95%
- 平均生成延迟 < 2s

---

### Risk 3: 数据一致性冲突

**风险描述**: 并发场景下（如多标签页同时操作同一会话），可能出现 message_count 更新不一致

**缓解措施**:
- ✅ 使用乐观锁（@Version 注解）防止并发覆盖
- ✅ 关键操作加事务边界（@Transactional）
- ✅ Redis 缓存失效策略：更新 DB 后主动删除对应 key（Cache-Aside 模式）

**示例代码**:
```java
@Transactional
public void incrementMessageCount(String memoryId) {
    ConversationEntity conv = conversationRepo.findByMemoryId(memoryId)
        .orElseThrow(() -> new NotFoundException("Conversation not found"));
    conv.setMessageCount(conv.getMessageCount() + 1);
    conversationRepo.save(conv);  // @Version 自动检查版本号
    cacheService.invalidate(memoryId);  // 清除旧缓存
}
```

---

### Trade-off 1: 功能完整性 vs 开发速度

**权衡点**: 是否在本次迭代中实现"搜索过滤"和"收藏固定"功能？

**决策**: 
- ✅ 本次实现基础搜索（keyword 参数）
- ❌ 收藏功能延后（需新建 favorites 表 + 关联关系）

**理由**: MVP 原则，优先解决核心痛点（会话可见性），高级功能后续迭代

---

## Migration Plan

### 部署步骤

1. **预部署准备**
   ```bash
   # 1. 备份数据库
   pg_dump astra_studio > backup_$(date +%Y%m%d).sql
   
   # 2. 运行 Flyway 迁移（V7 脚本自动执行）
   mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/astra_studio
   ```

2. **蓝绿部署**
   - **Green 环境**: 部署新版本（包含新 API + 前端改造）
   - **验证测试**: 调用 `GET /api/conversation` 确认返回格式正确
   - **流量切换**: Nginx/Gateway 将流量切到 Green
   - **回滚方案**: 若异常，切回 Blue（旧版本忽略新字段，nullable 保证兼容）

3. **Post-deployment**
   - 监控错误日志（重点关注 `ConversationController` 和 `ConversationQueryService`）
   - 验证 Redis 缓存命中率（应 > 80%）

### 回滚策略

- **数据库回滚**: Flyway 支持 `mvn flyway:repair` 回滚到 V6 版本（新字段设为 DROP COLUMN）
- **代码回滚**: Git revert commit，重新部署旧镜像
- **前端回滚**: CDN 刷新静态资源缓存（Cache-Control: max-age=0）

---

## Open Questions

1. **Q: 是否需要会话导入/导出功能？**
   - **A**: 当前不纳入范围（用户可通过复制粘贴手动备份）。若需求强烈，后续可支持 Markdown 导出。

2. **Q: 会话列表是否需要分页加载（无限滚动 vs 传统分页）？**
   - **A**: 建议采用传统分页（底部"加载更多"按钮），原因：
     - 实现简单，与后端 page/size 参数对齐
     - 避免滚动事件频繁触发请求
     - 未来可升级为虚拟滚动（vue-virtual-scroller）

3. **Q: 标题生成的 LLM 调用是否计入用户的 API 配额？**
   - **A**: 建议**不计入**（内部系统调用），单独统计到 `system_prompt_tokens` 分类中。

4. **Q: 多设备登录时，会话如何同步？**
   - **A**: 当前单设备场景，暂不考虑实时同步。未来可通过 WebSocket 推送变更通知。
