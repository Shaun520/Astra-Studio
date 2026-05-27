## MODIFIED Requirements

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
- **THEN** 自动验证文本 Embedding（text-embedding-v3, 1024维）和多模态 Embedding（tongyi-embedding-vision-plus, 1024维）的输出维度一致；若不一致则记录 CRITICAL 日志并禁用图片上传功能

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
  - **视觉区分**：图片来源卡片使用淡蓝色背景（如 `bg-blue-50`），文本来源使用默认灰色背景，便于用户快速识别
