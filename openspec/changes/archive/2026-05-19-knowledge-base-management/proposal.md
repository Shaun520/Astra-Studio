## Why

当前系统已具备底层的文档 ETL 管道、向量检索能力以及对话时的知识库 RAG 开关，但缺乏前端知识库管理界面和完整的文档生命周期管理（如删除、状态查看等）。为了让用户能够方便地管理自己的知识库文档，需要实现完整的前后端知识库管理功能，补齐前端 UI 和后端的缺失接口。

## What Changes

- 新增前端知识库管理面板（StudioPanel 中或独立弹窗），支持文档列表展示、上传和删除。
- 补齐后端文档删除接口（`DELETE /api/ai/knowledge/documents/{id}`），并级联删除相关的向量数据。
- 完善后端文档列表接口（`GET /api/ai/knowledge/documents`），支持分页和状态查询。
- 完善后端文档上传/导入接口与前端的对接，支持上传进度和状态轮询。
- 在前端展示文档的解析状态（PROCESSING, READY, FAILED）及错误信息。

## Capabilities

### New Capabilities
- `knowledge-base-management`: 知识库文档的完整生命周期管理，包括前端管理界面、文档上传、列表展示、状态追踪和文档删除。

### Modified Capabilities
- `knowledge-base-rag`: 扩展现有的知识库 RAG 能力，增加对文档删除和状态更新的处理（如删除后不再参与检索）。

## Impact

- **前端**：`StudioPanel.vue` 或新增组件，`api.ts` 新增知识库相关接口调用。
- **后端**：`AiController` 和 `KnowledgeService` 新增删除接口，完善列表接口。
- **数据库**：依赖现有的 `knowledge_documents` 和 `document_chunks` 表，需确保外键级联删除生效。
