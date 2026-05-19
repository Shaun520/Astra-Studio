## 1. 后端 RAG 检索修复

- [x] 1.1 修改 `DocumentETLPipeline.java`，在保存 `DocumentChunkEntity` 时，将向量数据正确设置到实体中，以便 Hibernate / Pgvector 映射到 `embedding` 列（而不是存入 `metadata` JSON 中）。
- [x] 1.2 修改 `DocumentChunkRepository.java` 和 `RAGRetrievalService.java`，修复 `findSimilarChunks` SQL 语句中的参数问题，移除导致全局检索失效的 `document_id = null` 过滤条件。
- [x] 1.3 修改 `RAGRetrievalService.java`，在获取到 Top-K 结果后，应用 `similarityThreshold` 进行相似度过滤。

## 2. 后端管理 API 补齐

- [x] 2.1 在 `KnowledgeService.java` 中新增 `deleteDocument(Long id)` 方法，调用 Repository 删除文档（依赖数据库外键级联删除 chunks）。
- [x] 2.2 在 `AiController.java` 中新增 `DELETE /api/ai/knowledge/documents/{id}` 接口。
- [x] 2.3 修改 `KnowledgeService.java` 和 `KnowledgeDocumentRepository.java`，使 `listDocuments` 支持分页参数（`page`, `size`），并按 `created_at` 降序排列。
- [x] 2.4 在 `AiController.java` 中修改 `GET /api/ai/knowledge/documents` 接口以接收分页参数。
- [x] 2.5 在 `KnowledgeService.java` 和 `AiController.java` 中新增 `GET /api/ai/knowledge/documents/{id}` 接口，返回单个文档的状态信息。

## 3. 前端 API 封装

- [x] 3.1 在 `Astra-Studio/src/services/api.ts` 中新增 `getKnowledgeDocuments(page, size)` 方法。
- [x] 3.2 在 `api.ts` 中新增 `getKnowledgeDocumentStatus(id)` 方法。
- [x] 3.3 在 `api.ts` 中新增 `deleteKnowledgeDocument(id)` 方法。
- [x] 3.4 在 `api.ts` 中新增 `importKnowledgeDocument(fileUrl, fileName)` 方法。

## 4. 前端管理界面实现

- [x] 4.1 在 `StudioPanel.vue` 中新增“知识库管理”区块（或创建独立的 `KnowledgeBasePanel.vue` 组件并在 `App.vue` 中引入）。
- [x] 4.2 实现文档列表展示 UI，显示文档名称、状态（使用不同颜色标识 PROCESSING, READY, FAILED）、分块数量和创建时间。
- [x] 4.3 实现文档上传交互：点击上传按钮 -> 选择文件 -> 上传 OSS 获取 URL -> 调用后端 `importKnowledgeDocument` 接口 -> 列表新增一条 PROCESSING 状态的记录。
- [x] 4.4 实现状态轮询机制：当列表中存在 PROCESSING 状态的文档时，每隔 3 秒调用 `getKnowledgeDocumentStatus` 更新状态。
- [x] 4.5 实现文档删除交互：点击删除按钮 -> 弹出确认框 -> 调用 `deleteKnowledgeDocument` 接口 -> 成功后刷新列表。

## 5. 前端溯源展示完善

- [x] 5.1 检查并完善 `App.vue` 中的 `sendChatMessage` 调用，确保正确注册了 `onSources` 回调，并将 `sources` 数据赋值给对应的消息对象。
- [x] 5.2 确保 `ChatMessage.vue` 中的 `sources` 引用卡片能够正确渲染后端返回的知识库参考片段。
