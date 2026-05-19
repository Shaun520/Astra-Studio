## Why

当前系统存在两个关键短板，制约产品体验和功能的进一步演进：

**短板 1：对话上下文随服务重启丢失**
- 会话记忆完全依赖进程内 `MessageWindowChatMemory`（最多 10 条），重启清零
- 用户刷新页面 / 换设备 / 服务部署后无法恢复之前的对话
- 长期多轮对话（代码评审、项目管理讨论等）体验断裂，用户不得不重复描述上下文

**短板 2：LLM 幻觉与知识时效性问题**
- 答案完全依赖 LLM 训练数据（截止日期），对实时信息和垂直领域知识缺乏支撑
- 用户上传的文档仅作为文件 URL 传入提示词，无结构化索引和语义检索
- 投影到纯 LLM 场景下，知识问答准确率约 78%（存在大量幻觉或过时信息）

本次变更将在 Astra Studio 中新增两个核心能力：

1. **对话持久化与高恢复率**：基于 Kryo 高性能序列化 + Redis 热缓存 + PostgreSQL 冷存储，实现服务重启后秒级恢复完整对话上下文
2. **知识库 RAG 系统**：构建文档 ETL 管道 + 向量检索（Pgvector），将企业知识/文档内容注入对话流，目标将知识问答准确率从 78% 提升至 92%

---

## What Changes

### 新增能力

**conversation-persistence**（对话持久化）：
- 引入 Kryo 5.x + Redis + PostgreSQL 双层存储架构
- 实现 `ConversationPersistenceService`：双写编排、多级恢复链（Redis → PostgreSQL → messages 重建）
- 实现 `KryoSerializer`：ConversationContext 高效序列化/反序列化，支持版本兼容和 JSON 降级
- `AiCodeHelperServiceFactory` 缓存 Key 扩展为 4 维（新增 `knowledgeBase`）
- 新增 `GET /api/ai/conversations/{memoryId}/restore` 接口

**knowledge-base-rag**（知识库 RAG）：
- 基于 Apache Tika 的文档 ETL 管道（解析 → 分块 → Embedding → 入库）
- Pgvector 向量存储：`document_chunks` 表 + HNSW 索引
- LangChain4j `ContentRetriever` 适配层，注入对话流
- 检索溯源：SSE 响应中附加 `sources` 事件，前端展示引用来源
- 新增 `POST /api/knowledge/import` 批量导入接口

### 修改能力

**model-selection**（模型选择）：
- `AiCodeHelperServiceFactory.getService()` 方法签名扩展：新增 `knowledgeBase` 参数
- `AiController.chat()` 接口扩展：新增 `knowledgeBase` 表单参数
- `calculateTimeout()` 扩展：知识库模式 +10 秒超时

### 技术栈新增

| 依赖 | 用途 |
|------|------|
| `com.esotericsoftware:kryo:5.5.0` | ConversationContext 序列化 |
| `org.springframework.boot:spring-boot-starter-data-redis` | Redis 缓存 |
| `org.springframework.boot:spring-boot-starter-data-jpa` | JPA/PostgreSQL |
| `org.postgresql:postgresql` | PostgreSQL 驱动 |
| `net.postgis:postgis-jdbc` 或 Pgvector JDBC | 向量扩展支持 |
| `org.apache.tika:tika-core:2.9` | 文档文本提取 |

---

## Capabilities

### New Capabilities
- `conversation-persistence`: 基于 Kryo + Redis + PostgreSQL 的对话上下文持久化与恢复
- `knowledge-base-rag`: 文档 ETL 管道 + 向量检索增强生成（RAG）

### Modified Capabilities
- `model-selection`: 扩展 `getService()` 方法签名，支持 knowledgeBase 第 4 维度

---

## Impact

### 后端影响

| 文件/模块 | 变更类型 | 说明 |
|-----------|---------|------|
| [AiCodeHelperServiceFactory.java](file:///d:/project/Astra-Studio/Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/ai/AiCodeHelperServiceFactory.java) | 修改 | `getService()` 签名扩展；`buildService()` 新增 RAG 挂载 |
| [AiController.java](file:///d:/project/Astra-Studio/Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/controller/AiController.java) | 修改 | 新增 `knowledgeBase` 参数；新增 `GET /conversations/{id}/restore` |
| [application.yaml](file:///d:/project/Astra-Studio/Astra-Studio-Open-Ai/src/main/resources/application.yaml) | 修改 | 新增 Redis、PostgreSQL 数据源和 RAG 配置 |
| `pom.xml` | 修改 | 新增 Kryo、Redis、JPA、PostgreSQL、Tika 依赖 |
| `conversation/persistence/` | 新增模块 | KryoSerializer, ConversationPersistenceService, ConversationCacheService 等 |
| `rag/` | 新增模块 | DocumentETLPipeline, RAGRetrievalService, DocumentParserService, TextChunker 等 |
| `DB migration` | 新增 | Flyway 脚本：5 张表 + pgvector 扩展 |

### 前端影响

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| [Composer.vue](file:///d:/project/Astra-Studio/Astra-Studio/src/components/Composer.vue) | 修改 | 新增知识库开关按钮 |
| [ChatMessage.vue](file:///d:/project/Astra-Studio/Astra-Studio/src/components/ChatMessage.vue) | 修改 | 新增 `sources` 溯源展示区域 |
| [api.ts](file:///d:/project/Astra-Studio/Astra-Studio/src/services/api.ts) | 修改 | FormData 新增 `knowledgeBase` 字段 |
| [types/index.ts](file:///d:/project/Astra-Studio/Astra-Studio/src/types/index.ts) | 修改 | Message 接口新增 `sources` 字段 |

### 兼容性影响
- ✅ 完全向后兼容：新增功能默认关闭（`enabled: false`）
- ✅ 不传 `knowledgeBase` 参数时行为与现有版本一致
- ✅ 前端旧版本不传新参数时后端默认值处理

### 性能影响
- 对话持久化写入延迟：+0.8ms（Kryo 序列化）+ < 1ms（Redis SET）→ 总增量 < 2ms
- RAG 检索延迟：+150-400ms（Embedding API + Pgvector 查询）→ 不影响 SSE 流式吞吐
- 缓存命中场景：无额外延迟