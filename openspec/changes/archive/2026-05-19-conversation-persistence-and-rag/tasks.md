## 1. 基础设施与依赖

- [x] 1.1 引入 Kryo 依赖：`pom.xml` 添加 `com.esotericsoftware:kryo:5.5.0`
- [x] 1.2 引入 Redis 依赖：`pom.xml` 添加 `spring-boot-starter-data-redis`
- [x] 1.3 引入 JPA + PostgreSQL 依赖：`pom.xml` 添加 `spring-boot-starter-data-jpa` + `postgresql`
- [x] 1.4 引入 Apache Tika 依赖：`pom.xml` 添加 `tika-core:2.9.0` + `tika-parsers-standard-package:2.9.0`
- [x] 1.5 引入 Flyway 依赖：`pom.xml` 添加 `flyway-core` + `flyway-database-postgresql`
- [x] 1.6 更新 [application.yaml](file:///d:/project/Astra-Studio/Astra-Studio-Open-Ai/src/main/resources/application.yaml)，添加配置段：
  ```yaml
  spring:
    datasource:
      url: jdbc:postgresql://localhost:5432/astra_studio
      username: ${DB_USERNAME:astra}
      password: ${DB_PASSWORD:astra}
    data:
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
    jpa:
      hibernate:
        ddl-auto: validate
  
  conversation:
    persistence:
      enabled: true
      redis-ttl-hours: 24
      async-flush: true
      kryo-registered-classes:
        - com.example.astrastudioopenai.conversation.ConversationContext
        - com.example.astrastudioopenai.conversation.MessageEntry
  
  knowledge-base:
    rag:
      enabled: true
      embedding-model: text-embedding-v3
      embedding-dimensions: 1536
      chunk-size: 512
      chunk-overlap: 64
      top-k: 5
      similarity-threshold: 0.75
      retrieval-timeout-ms: 3000
  ```
- [x] 1.7 创建 `application-local.yaml` 本地开发配置（覆盖密钥和数据库连接）

---

## 2. 数据库迁移脚本

- [x] 2.1 创建 Flyway 迁移 V1__create_conversations_table.sql：
  - 表 `conversations`（id, memory_id, title, model_name, message_count, status, created_at, updated_at）
  - 索引 `idx_memory_id`, `idx_updated_at`

- [x] 2.2 创建 Flyway 迁移 V2__create_conversation_messages_table.sql：
  - 表 `conversation_messages`（id, conversation_id FK, role ENUM, content, thinking_content, attachments JSON, sequence_num, token_count, created_at）
  - 索引 `idx_conversation_seq`

- [x] 2.3 创建 Flyway 迁移 V3__create_conversation_snapshots_table.sql：
  - 表 `conversation_snapshots`（id, conversation_id FK UNIQUE, snapshot_data BYTEA, version, checksum, kv_size, created_at, updated_at）
  - 索引 `idx_conv_version`

- [x] 2.4 创建 Flyway 迁移 V4__enable_pgvector.sql：
  - `CREATE EXTENSION IF NOT EXISTS vector;`

- [x] 2.5 创建 Flyway 迁移 V5__create_knowledge_tables.sql：
  - 表 `knowledge_documents`（id, filename, file_type, file_url, file_size, chunk_count, status, error_message, created_at, updated_at）
  - 表 `document_chunks`（id, document_id FK, chunk_index, content, embedding vector(1536), metadata JSONB, token_count, created_at）
  - HNSW 索引 `idx_chunks_embedding`

---

## 3. 对话持久化模块（conversation/persistence/）

### 3.1 领域模型
- [x] 3.1.1 创建 `ConversationContext` 实体类（memoryId, messages, modelName, version, checksum, timestamps）
- [x] 3.1.2 创建 `MessageEntry` 记录类（role, content, thinkingContent, attachments, sequenceNum, timestamp）

### 3.2 Kryo 序列化器
- [x] 3.2.1 创建 `KryoSerializer.java`：实现 ThreadLocal\<Kryo\> 池化 + CompatibleFieldSerializer 注册
- [x] 3.2.2 实现 `toBytes(ConversationContext)` 方法：序列化 + SHA-256 校验和生成
- [x] 3.2.3 实现 `fromBytes(byte[])` 方法：反序列化 + JSON 降级后备路径
- [x] 3.2.4 实现 `validateChecksum(byte[], String)` 完整性校验

### 3.3 JPA 实体与 Repository
- [x] 3.3.1 创建 `ConversationEntity` JPA 实体（映射 conversations 表）
- [x] 3.3.2 创建 `MessageEntity` JPA 实体（映射 conversation_messages 表）
- [x] 3.3.3 创建 `SnapshotEntity` JPA 实体（映射 conversation_snapshots 表）
- [x] 3.3.4 创建 `ConversationRepository`（JpaRepository，自定义 findByMemoryId 方法）
- [x] 3.3.5 创建 `MessageRepository`（findByConversationIdOrderBySequenceNum）
- [x] 3.3.6 创建 `SnapshotRepository`（findByConversationId）

### 3.4 Redis 缓存服务
- [x] 3.4.1 创建 `ConversationCacheService.java`：封装 RedisTemplate 操作
- [x] 3.4.2 实现 `cacheContext(memoryId, ctx, bytes)` —— SET + EXPIRE（pipeline 批量）
- [x] 3.4.3 实现 `getCachedBytes(memoryId)` —— GET 返回 byte[]
- [x] 3.4.4 实现 `invalidate(memoryId)` —— DEL key
- [x] 3.4.5 实现 `getVersion(memoryId)` —— GET version key
- [x] 3.4.6 配置 `RedisTemplate` 的序列化器（StringRedisSerializer + byte[]）

### 3.5 持久化编排服务
- [x] 3.5.1 创建 `ConversationPersistenceService.java`：注入 KryoSerializer + CacheService + Repositories
- [x] 3.5.2 实现 `saveContext(memoryId, ctx)` 双写流程：
  - 同步：Kryo 序列化 → Redis SET
  - 异步（`@Async`）：更新/插入 conversations 表 + snapshot 表 + 增量写入 messages 表
- [x] 3.5.3 实现 `loadContext(memoryId)` 多级恢复链：
  - Level 1: Redis GET → 命中则直接返回
  - Level 2: PostgreSQL snapshot → 校验 checksum → 反序列化 → 回写 Redis
  - Level 3: PostgreSQL messages → 逐条重建 ConversationContext
  - Level 4: 返回空 ConversationContext（新会话）
- [x] 3.5.4 实现 `restoreOrCreate(memoryId)` 公开方法
- [x] 3.5.5 实现 `recoverFromMessages(conversationId)` 重建逻辑

---

## 4. 知识库 RAG 模块（rag/）

### 4.1 Embedding 配置
- [x] 4.1.1 创建 EmbeddingModel Bean：`OpenAiEmbeddingModel` 指向 DashScope compatible-mode
- [x] 4.1.2 配置 `EmbeddingModelProperties`（modelName=text-embedding-v3, dimensions=1536）

### 4.2 文档解析
- [x] 4.2.1 创建 `DocumentParserService.java`：使用 Apache Tika AutoDetectParser
- [x] 4.2.2 实现 `parseToText(fileUrl)` —— 从 OSS URL 读取流 → Tika 提取文本
- [x] 4.2.3 实现 `detectFileType(fileUrl)` —— 基于 Tika Content-Type 检测或文件名后缀

### 4.3 文本分块
- [x] 4.3.1 创建 `TextChunker.java`：可配置 chunkSize=512, overlap=64
- [x] 4.3.2 实现递归字符分割器（`\n\n` → `\n` → `。` → `.` → ` `）
- [x] 4.3.3 实现 `chunk(text)` 返回 `List<TextChunk>`（含 metadata：页码、章节标题）

### 4.4 ETL 管道
- [x] 4.4.1 创建 `DocumentETLPipeline.java`（@Service，注入 Parser + Chunker + EmbeddingModel + PgvectorTemplate）
- [x] 4.4.2 实现 `processDocument(fileUrl, fileName)` 全链路：
  - 解析 → 清洗 → 分块 → Embedding（批量） → 写入 Pgvector → 更新 `knowledge_documents` 状态
- [x] 4.4.3 实现进度监控（chunk 级别进度日志）
- [x] 4.4.4 配置 `@Async("etlExecutor")` 异步线程池（核心 2 线程，最大 4 线程）

### 4.5 向量检索服务
- [x] 4.5.1 创建 `RAGRetrievalService.java`：注入 EmbeddingModel + PgvectorTemplate
- [x] 4.5.2 实现 `retrieve(query, topK)` 检索流程：
  - `EmbeddingModel.embed(query)` → float[1536]
  - PostgreSQL 向量相似度查询（`ORDER BY embedding <=> query_vector LIMIT topK`）
  - 过滤 `similarity_threshold < 0.75` 的结果
- [x] 4.5.3 实现 `formatContext(List<DocumentChunk>)` —— 拼接格式：`[文档:filename, 页码:p] content`

### 4.6 LangChain4j ContentRetriever 适配
- [x] 4.6.1 创建 `ContentRetrieverConfig.java`：将 `RAGRetrievalService` 包装为 LangChain4j `ContentRetriever`
- [x] 4.6.2 在 Bean 中配置检索参数（topK, minScore, timeout）

---

## 5. AiCodeHelperServiceFactory 扩展

- [x] 5.1 修改 `getService()` 方法签名：新增 `boolean knowledgeBase` 参数
- [x] 5.2 扩展缓存 Key 生成逻辑：`cacheKey = "deepThink:%s,webSearch:%s,model:%s,rag:%s"`
- [x] 5.3 在 `buildService()` 中新增 RAG 挂载：
  ```java
  if (knowledgeBase) {
      builder.contentRetriever(ragContentRetriever);
  }
  ```
- [x] 5.4 扩展 `calculateTimeout()`：`if (knowledgeBase) baseTimeout += 10`
- [x] 5.5 注入 RAG `ContentRetriever` Bean（通过 `@Resource` 或构造器注入）

---

## 6. AiController 扩展

- [x] 6.1 `chat()` 方法新增 `@RequestParam knowledgeBase` 参数（默认 `false`）
- [x] 6.2 修改 `getService()` 调用：传入 `knowledgeBase` 参数
- [x] 6.3 新增 `GET /conversations/{memoryId}/restore` 接口：
  - 调用 `ConversationPersistenceService.restoreOrCreate(memoryId)`
  - 返回 JSON：`{ memoryId, messages: [...], version, recovered: boolean }`
- [x] 6.4 新增 `POST /knowledge/import` 接口：
  - 接收 FormData：`{ fileUrl: string, fileName: string }`
  - 异步触发 `DocumentETLPipeline.processDocument()`
  - 返回 `{ documentId, status: 'PROCESSING' }`
- [x] 6.5 新增 `GET /knowledge/documents` 查询知识库文档列表和状态
- [x] 6.6 在 SSE 结束时推送 `sources` 事件（如果 knowledgeBase=true 且有命中）

---

## 7. 前端适配

- [x] 7.1 Composer.vue：新增知识库开关按钮（`Brain` 或 `Library` 图标，与深度思考/联网搜索并列）
- [x] 7.2 Composer.vue：扩展 `emit('send', ...)` 事件，传出 `isKnowledgeBase` 参数
- [x] 7.3 api.ts：`SendChatMessageOptions` 接口新增 `knowledgeBase?: boolean`
- [x] 7.4 api.ts：`sendChatMessage()` FormData 构建新增 `formData.append('knowledgeBase', 'true')`
- [x] 7.5 ChatMessage.vue：新增 `sources` 溯源展示区（在助手消息末尾，以引用卡片形式展示 Top-3 来源）
- [x] 7.6 ChatMessage.vue：`parseSSELine` 新增 `type: "sources"` 事件解析
- [x] 7.7 types/index.ts：新增 `KnowledgeSource` 类型（chunk_id, content_snippet, document_name, page_number, score）

---

## 8. 测试

### 8.1 单元测试
- [ ] 8.1.1 `KryoSerializerTest`：
  - `testSerializeDeserialize()`: 创建 ConversationContext → 序列化 → 反序列化 → 断言字段一致
  - `testVersionCompatibility()`: 用旧版本 bytes 测试新版本类可反序列化（新字段=null）
  - `testChecksumValidation()`: 篡改 bytes → 断言校验失败
  - `testJsonFallback()`: 无效 Kryo bytes → 断言降级到 JSON 后备 → 返回非空 Context

- [ ] 8.1.2 `TextChunkerTest`：
  - `testChunkSize()`: 长文本分块后每块 ≤ 512 tokens
  - `testOverlap()`: 连续块有 64 token 重叠
  - `testChineseBoundary()`: 中文句号（`。`）处正确切分
  - `testEmptyText()`: 空文本不抛异常，返回空列表

- [ ] 8.1.3 `ConversationPersistenceServiceTest`：
  - `testSaveAndLoad()`: 保存 Context → 从 Redis/DB 加载 → 断言恢复数据一致
  - `testFallbackToDB()`: 清除 Redis → load → 断言从 DB 恢复成功
  - `testFallbackToMessages()`: 清除 Redis + snapshot → load → 断言从 messages 重建
  - `testEmptyRecovery()`: 全链路无数据 → load → 断言返回空 Context

### 8.2 集成测试
- [ ] 8.2.1 `RAGRetrievalIntegrationTest`：
  - 预加载 100 条测试 chunks → retrieve("Spring Boot 配置") → 断言返回相关 chunks（score≥0.75）
  - 测试检索超时配置（3s → 断言抛出 TimeoutException）

- [ ] 8.2.2 `AiControllerIntegrationTest`：
  - `POST /chat` + `knowledgeBase=true` → 验证 SSE 流正常推送
  - `GET /conversations/{id}/restore` → 验证历史消息恢复 JSON
  - `POST /knowledge/import` → 验证返回 documentId + status=PROCESSING

### 8.3 性能基准测试
- [ ] 8.3.1 Kryo vs Java 序列化 Benchmark（JMH）：
  - 场景：100KB Context 对象，10000 次 warmup + 100000 次迭代
  - 指标：吞吐量、平均延迟、P99 延迟、输出体积

- [ ] 8.3.2 Pgvector HNSW 检索 Benchmark：
  - 场景：索引 100K 向量，QPS 测试（1/10/100 并发）
  - 指标：P50/P95/P99 延迟、Recall@K

---

## 9. 文档与清理

- [x] 9.1 更新 [technical.md](file:///d:/project/Astra-Studio/docs/technical.md)：
  - 新增"3.8 对话持久化"章节（架构图、核心类说明、关键代码示例）
  - 新增"3.9 知识库 RAG"章节（ETL 管道图、检索流程、Retriever 集成方式）
  - 更新"4.2 接口列表"新增 restore 和 import 接口
  - 更新"5.1 后端配置文件"新增 Redis/PostgreSQL/RAG 配置

- [ ] 9.2 更新 [readme.md](file:///d:/project/Astra-Studio/docs/readme.md)：
  - "一、功能现状"新增对话恢复和知识库检索
  - "四、主要接口"新增 restore 和 knowledge 接口
  - "八、本地启动"补充 PostgreSQL + Redis 的 Docker 启动说明

- [ ] 9.3 代码审查 checklist：
  - 无 `System.out.println`（全部 Slf4j）
  - 所有 Redis Key 遵循 `astra:conv:{memoryId}` 命名空间规范
  - 所有异步操作配置了专用 `@Async` 线程池
  - Kryo Serializer 使用 ThreadLocal 防止并发冲突
  - Embedding API 调用有重试机制（`maxRetries=2`）
  - 数据库软删除逻辑完整（status= -1 的会话不在 restore 接口返回）
