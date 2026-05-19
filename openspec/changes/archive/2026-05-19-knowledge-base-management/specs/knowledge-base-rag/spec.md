## MODIFIED Requirements

### Requirement: 文档 ETL 管道
系统 SHALL 实现基于 Apache Tika 的文档解析 + 递归字符分割 + DashScope Embedding 的完整 ETL 管道，支持 PDF/Word/TXT/Markdown 格式。

#### Scenario: 文档文本提取
- **WHEN** 系统收到文档的 OSS 公开访问 URL（PDF/Word/TXT/Markdown 格式）
- **THEN** `DocumentParserService.parseToText()` 调用 Apache Tika AutoDetectParser 提取纯文本，去除页眉页脚、统一换行符为 `\n`

#### Scenario: 智能文本分块
- **WHEN** 提取的文本进入分块阶段
- **THEN** `TextChunker.chunk()` 按以下优先级分割：段落边界 → 句子边界（`。` `.` `!` `?`）→ 固定大小（512 tokens）→ 每块末尾附加 64 tokens 的前块末尾作为重叠上下文

#### Scenario: 批量向量化与入库
- **WHEN** 所有分块就绪
- **THEN** 系统以批量方式（batchSize=20）调用 DashScope Embedding API 生成 1536 维向量，逐个写入 `document_chunks` 表，**并将向量数据正确写入 `embedding` 列（而非仅仅存入 JSON metadata 中）**；ETL 完成后更新 `knowledge_documents.status = READY`

#### Scenario: ETL 异步执行不阻塞 API
- **WHEN** 触发文档导入请求
- **THEN** ETL 管道在 `@Async("etlExecutor")` 线程池中异步执行，API 立即返回 `{ documentId, status: "PROCESSING" }`，HTTP 202

#### Scenario: ETL 失败记录与状态流转
- **WHEN** ETL 管道中任何环节失败（如 Tika 解析异常、Embedding API 超时、数据库写入失败）
- **THEN** 系统将 `knowledge_documents.status` 更新为 `FAILED`，记录 `error_message`，并记录 ERROR 级别日志

### Requirement: Pgvector 向量存储与 HNSW 索引
系统 SHALL 使用 PostgreSQL + Pgvector 扩展作为向量存储引擎，基于 HNSW 算法构建向量索引。

#### Scenario: 向量相似度检索
- **WHEN** `RAGRetrievalService.retrieve(query, topK)` 被调用
- **THEN** 系统执行两步操作：（1）调用 Embedding 模型将 query 转为 1536 维向量；（2）执行 `SELECT * FROM document_chunks ORDER BY embedding <=> $1::vector LIMIT $2` 获取 Top-K 结果，**检索 SQL 不应包含会导致全表过滤失效的 `document_id = null` 条件，应支持全局检索**。

#### Scenario: 相似度阈值过滤
- **WHEN** 检索返回 Top-K 结果（K = 配置的 `topK`，默认 5）
- **THEN** 系统过滤掉余弦相似度 < `similarity_threshold`（默认 0.75）的 chunk，仅将满足阈值的 chunk 注入 LLM 上下文

#### Scenario: HNSW 索引性能
- **WHEN** 知识库规模 ≤ 100K 个文档块
- **THEN** 单次 Top-5 检索（含 Embedding 调用）P95 延迟 < 300ms，P99 < 500ms

#### Scenario: 向量检索超时保护
- **WHEN** Pgvector 查询超过 3 秒未返回
- **THEN** 系统触发超时取消，降级为纯 LLM 模式（不注入知识库上下文），记录 WARN 日志
