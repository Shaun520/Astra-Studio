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
系统 SHALL 使用 PostgreSQL + Pgvector 扩展作为向量存储引擎，支持**跨模态统一向量检索**（文本和图片共享同一 1024 维语义空间）。

#### Scenario: 向量相似度检索（全局搜索，含跨模态匹配）
- **WHEN** `RAGRetrievalService.retrieve(query, topK)` 被调用且知识库中同时存在文本和图片类型的 chunk
- **THEN** 系统执行两步操作：（1）调用 **文本 Embedding 模型**（text-embedding-v3）将 query 文本转为 **1024 维向量**；（2）执行 SQL 查询获取 Top-K 结果，结果可能包含 `metadata.source_type = 'image'`（来自多模态 Embedding）或 `'text'`（来自文本 Embedding）的 chunk；**由于 DashScope 保证同一语义空间，文本 query 可直接与图片向量进行余弦相似度计算，实现"文搜图"效果**

#### Scenario: 跨模态检索场景示例
- **WHEN** 用户提问"系统架构图在哪里" 且知识库中存在一张架构设计截图
- **THEN** RAG 检索应能通过文本 query 的向量与图片向量的相似度匹配，返回该架构图的 chunk（metadata.source_type="image"），实现**文搜图**功能

#### Scenario: 相似度阈值过滤与 Fallback（统一处理）
- **WHEN** 检索返回 Top-K 原始结果（K = 配置的 topK*2，默认 10），其中混有文本和图片 chunk
- **THEN** 系统统一使用余弦距离阈值过滤（默认 maxDist=0.5），**不区分 source_type**；若过滤后无结果，触发 Fallback 以 maxDist=1.0 重试一次；初期采用统一阈值策略，后续可根据实际效果为文本和图片设置不同阈值

#### Scenario: 检索结果中的 Chunk 类型标识与元数据
- **WHEN** RAG 检索返回的结果中包含来自图片文档的 chunk
- **THEN** 每个 result 对象的 metadata 中包含完整信息：`{ source_type: "image", image_format: "png", original_file: "screenshot.png", vector_type: "multimodal", model: "tongyi-embedding-vision-plus-2026-03-06" }`，便于前端区分展示和处理

#### Scenario: 向量索引性能（优化后）
- **WHEN** 知识库规模 ≤ 100K 个文档块（含文本和图片）
- **THEN** 单次 Top-5 检索（含 Embedding 调用）P95 延迟 < **2000ms**（相比原方案 2500ms 有所改善，原因：图片采用单 Chunk 策略减少了总 chunk 数量）

#### Scenario: 向量维度一致性保障
- **WHEN** 系统启动或配置加载时
- **THEN** 自动验证文本 Embedding（text-embedding-v3, 1024维）和多模态 Embedding（tongyi-embedding-vision, 1024维）的输出维度一致；若不一致则记录 CRITICAL 日志并禁用图片上传功能

#### Scenario: 向量检索超时保护
- **WHEN** Pgvector 查询超过配置的超时时间（默认 3 秒）未返回
- **THEN** 系统触发超时取消，降级为纯 LLM 模式（不注入任何知识库上下文），记录 WARN 日志

---

### Requirement: RAG 集成到对话流程
系统 SHALL 在 `AiCodeHelperServiceFactory` 中当 `knowledgeBase=true` 时挂载 `ContentRetriever`，实现支持**跨模态检索增强生成**（文本+图片混合内容）。

#### Scenario: 知识库模式下的服务构建（混合内容 + 跨模态检索）
- **WHEN** `getService(deepThink, webSearch, model, knowledgeBase=true)` 被调用且知识库中同时存在文本和图片文档
- **THEN** 生成的 `AiCodeHelperService` 实例绑定 LangChain4j `ContentRetriever`，由 LLM 运行时自动触发 RAG 检索；检索范围覆盖所有类型的 chunk（text + image），且**无需区分处理逻辑**（统一向量空间）

#### Scenario: 跨模态检索效果验证
- **WHEN** 用户在知识库模式下提问与已上传图片相关的问题（如"这张图讲了什么"、"架构设计的关键点是什么"）
- **THEN** LLM 应能基于 RAG 检索到的图片 chunk 生成准确回答，并在 sources 中标注图片来源（📷 图标）

#### Scenario: 知识库开关独立于其他功能
- **WHEN** 用户同时启用知识库 + 深度思考 + 联网搜索（knowledgeBase=true, deepThink=true, webSearch=true）
- **THEN** 系统三者并行生效：联网搜索工具 + **跨模态知识库上下文（含文本和图片向量）** + 思维链展示

#### Scenario: 知识库缓存 Key 隔离
- **WHEN** 同一 `(deepThink, webSearch, model)` 组合下 knowledgeBase 值不同
- **THEN** Factory 缓存为两个独立的 Service 实例，Key 格式包含 `rag:true/false` 维度（与现有逻辑一致，无需修改）

#### Scenario: Query Embedding 本地缓存（防重复调用）
- **WHEN** 同一请求内多次调用 retrieve()（LangChain4j 自动调用 + 手动获取 sources）
- **THEN** 系统使用 ConcurrentHashMap 缓存 Query Embedding 结果（TTL 30s），避免重复调用 DashScope **文本** Embedding API（与现有逻辑一致）

---

### Requirement: SSE 溯源信息推送（含跨模态来源标识）
系统 SHALL 在知识库模式下，当检索到有效结果时（含文本和图片来源），在 SSE 流结束时向前端推送 `sources` 事件。

#### Scenario: 有命中时推送 sources 事件（含跨模态来源）
- **WHEN** RAG 检索返回了通过相似度阈值过滤的 chunk（可能包含 text 或 image 类型）且 LLM 完成回答
- **THEN** 系统在 `complete` 事件之前推送 `type: "sources"` 事件，数据包含检索到的 chunk 摘要信息：`[{ chunkId, contentSnippet, documentName, sourceType, metadata }]`，其中：
  - `sourceType`: `"text"` 或 `"image"`
  - `metadata`: 对 image 类型包含 `{ image_format, original_file, vector_type }` 等完整元数据

#### Scenario: 无命中时不推送空 sources
- **WHEN** RAG 检索未返回任何通过阈值的 chunk
- **THEN** 不推送 `sources` 事件，也不影响正常的 `text`/`thinking`/`complete` 事件流

#### Scenario: 前端展示溯源信息（区分跨模态来源类型）
- **WHEN** 前端接收到 `type: "sources"` 事件
- **THEN** 在助手消息末尾以引用卡片形式展示来源信息：
  - **文本来源**：显示 📄 图标 + 文档名 + 引用片段（可点击跳转到文档详情）
  - **图片来源**：显示 📷 图标 + 文件名 + 引用片段（可点击预览原图，若有 OSS URL；或显示"图片暂无预览"提示）
  - **视觉区分**：图片来源卡片使用淡蓝色背景（border-blue-500/20 bg-blue-500/5），文本来源使用默认灰色背景，便于用户快速识别

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
