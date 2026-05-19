## ADDED Requirements

### Requirement: 文档 ETL 管道
系统 SHALL 实现基于 Apache Tika 的文档解析 + 递归字符分割 + DashScope Embedding 的完整 ETL 管道，支持 PDF/Word/TXT/Markdown 格式。

#### Scenario: 文本文本提取
- **WHEN** 系统收到文档的 OSS 公开访问 URL（PDF/Word/TXT/Markdown 格式）
- **THEN** `DocumentParserService.parseToText()` 调用 Apache Tika AutoDetectParser 提取纯文本，去除页眉页脚、统一换行符为 `\n`

#### Scenario: 智能文本分块
- **WHEN** 提取的文本进入分块阶段
- **THEN** `TextChunker.chunk()` 按以下优先级分割：段落边界 → 句子边界（`。` `.` `!` `?`）→ 固定大小（512 tokens）→ 每块末尾附加 64 tokens 的前块末尾作为重叠上下文

#### Scenario: 批量向量化与入库（同步执行）
- **WHEN** 所有分块就绪
- **THEN** 系统**同步**以批量方式（batchSize=10，DashScope API 限制）调用 DashScope Embedding API 生成 **1024 维向量**（text-embedding-v3），逐个写入 `document_chunks` 表的 `embedding` 列（使用 Hibernate Vector `@JdbcTypeCode(SqlTypes.VECTOR)` 映射）；**嵌入失败时抛出异常而非保存 NULL 向量**；ETL 完成后更新 `knowledge_documents.status = READY`

> ⚠️ **实现要点**：采用同步执行确保嵌入完成后再标记 READY；移除 `@Async` 注解避免异步线程中 Session 关闭导致的 LazyInitializationException。

#### Scenario: ETL 失败记录与状态流转
- **WHEN** ETL 管道中任何环节失败（如 Tika 解析异常、Embedding API 超时、数据库写入失败）
- **THEN** 系统将 `knowledge_documents.status` 更新为 `FAILED`，记录 `error_message`，并记录 ERROR 级别日志

---

### Requirement: Pgvector 向量存储与检索
系统 SHALL 使用 PostgreSQL + Pgvector 扩展作为向量存储引擎，基于 IVFFlat/HNSW 算法构建向量索引。

#### Scenario: 向量相似度检索（全局搜索）
- **WHEN** `RAGRetrievalService.retrieve(query, topK)` 被调用
- **THEN** 系统执行两步操作：（1）调用 Embedding 模型将 query 转为 **1024 维向量**；（2）执行 `SELECT * FROM document_chunks ORDER BY embedding <=> $1::vector LIMIT $2` 获取 Top-K 结果，**检索 SQL 不应包含会导致全表过滤失效的 `document_id = null` 条件，应支持全局检索**

#### Scenario: 相似度阈值过滤与 Fallback
- **WHEN** 检索返回 Top-K 原始结果（K = 配置的 `topK*2`，默认 10）
- **THEN** 系统过滤掉余弦距离 > `maxDist`（即相似度 < threshold，**默认阈值 0.5 / maxDist=0.5**）的 chunk；若过滤后无结果，触发 Fallback 以 `maxDist=1.0` 重试一次

> ⚠️ **实现要点**：短查询 vs 长文档 chunk 的语义差异较大，阈值不宜过严（原 0.75 导致大量零结果）；Fallback 机制保证降级可用。

#### Scenario: 向量索引性能
- **WHEN** 知识库规模 ≤ 100K 个文档块
- **THEN** 单次 Top-5 检索（含 Embedding 调用）P95 延迟 < 2000ms（含网络 I/O）

#### Scenario: 向量检索超时保护
- **WHEN** Pgvector 查询超过配置的超时时间（默认 3 秒）未返回
- **THEN** 系统触发超时取消，降级为纯 LLM 模式（不注入知识库上下文），记录 WARN 日志

---

### Requirement: RAG 集成到对话流程
系统 SHALL 在 `AiCodeHelperServiceFactory` 中当 `knowledgeBase=true` 时挂载 `ContentRetriever`，实现检索增强生成。

#### Scenario: 知识库模式下的服务构建
- **WHEN** `getService(deepThink, webSearch, model, knowledgeBase=true)` 被调用
- **THEN** 生成的 `AiCodeHelperService` 实例绑定 LangChain4j `ContentRetriever`，由 LLM 运行时自动触发 RAG 检索

#### Scenario: 知识库开关独立于其他功能
- **WHEN** 用户同时启用知识库 + 深度思考 + 联网搜索（knowledgeBase=true, deepThink=true, webSearch=true）
- **THEN** 系统三者并行生效：联网搜索工具 + 知识库上下文 + 思维链展示

#### Scenario: 知识库缓存 Key 隔离
- **WHEN** 同一 `(deepThink, webSearch, model)` 组合下 knowledgeBase 值不同
- **THEN** Factory 缓存为两个独立的 Service 实例，Key 格式包含 `rag:true/false` 维度

#### Scenario: Query Embedding 本地缓存（防重复调用）
- **WHEN** 同一请求内多次调用 retrieve()（LangChain4j 自动调用 + 手动获取 sources）
- **THEN** 系统使用 ConcurrentHashMap 缓存 Query Embedding 结果（TTL 30s），避免重复调用 DashScope Embedding API

---

### Requirement: SSE 溯源信息推送
系统 SHALL 在知识库模式下，当检索到有效结果时，在 SSE 流结束时向前端推送 `sources` 事件。

#### Scenario: 有命中时推送 sources 事件
- **WHEN** RAG 检索返回了通过相似度阈值过滤的 chunk 且 LLM 完成回答
- **THEN** 系统在 `complete` 事件之前推送 `type: "sources"` 事件，数据包含检索到的 chunk 摘要信息：`[{ chunkId, contentSnippet, documentName }]`

#### Scenario: 无命中时不推送空 sources
- **WHEN** RAG 检索未返回任何通过阈值的 chunk
- **THEN** 不推送 `sources` 事件，也不影响正常的 `text`/`thinking`/`complete` 事件流

#### Scenario: 前端展示溯源信息
- **WHEN** 前端接收到 `type: "sources"` 事件
- **THEN** 在助手消息末尾以引用卡片形式展示来源的文档名和引用片段

---

### Requirement: 知识库导入接口
系统 SHALL 提供 `POST /api/ai/knowledge/upload` 接口支持通过 API 导入知识库文档。

#### Scenario: 单文档导入
- **WHEN** 调用方发送 `POST /api/ai/knowledge/upload` 携带 `{ fileUrl, fileName }`
- **THEN** 系统在 `knowledge_documents` 表创建记录（status = PROCESSING），同步执行 ETL 后返回 `{ documentId, status: "READY", chunkCount }`，HTTP 200

#### Scenario: 查询文档状态
- **WHEN** 调用方调用 `GET /api/ai/knowledge/documents/{documentId}`
- **THEN** 返回该文档的当前状态（PROCESSING / READY / FAILED）、chunk_count（READY 时）、error_message（FAILED 时）

#### Scenario: 知识库文档列表
- **WHEN** 调用方调用 `GET /api/ai/knowledge/documents?page=&size=`
- **THEN** 返回按 `created_at` 降序排列的分页数据，包含总记录数、总页数和当前页数据

---

## MODIFIED Requirements

### Requirement: 后端模型参数接收与验证（扩展）
系统 SHALL 在 `/ai/chat/stream` 接口接收 `knowledgeBase` 参数，扩展 Factory 方法签名匹配新的 4 维缓存 Key。

#### Scenario: 接收 knowledgeBase 参数
- **WHEN** 后端收到请求且 `knowledgeBase` 参数值为 `true`
- **THEN** 后端在调用 `aiServiceFactory.getService()` 时传入该值，匹配到对应的（可能是新创建的）Service 实例

#### Scenario: 使用默认值处理缺失参数
- **WHEN** 后端收到请求但未包含 `knowledgeBase` 参数
- **THEN** 系统使用默认值 `false`，行为与升级前完全一致

---

### Requirement: 前端模型参数传递（扩展）
系统 SHALL 在用户发送消息时将知识库开关状态作为 `knowledgeBase` 参数传递给后端 API。

#### Scenario: 发送消息时包含知识库参数
- **WHEN** 用户在已开启知识库开关的情况下点击发送按钮
- **THEN** 前端构建请求时包含 `knowledgeBase` 字段，值为 `true`
