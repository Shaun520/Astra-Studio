## Context

Astra Studio 目前已经实现了底层的知识库 RAG 检索能力（基于 Pgvector 和 LangChain4j），并在前端对话界面提供了知识库检索的开关。然而，目前的知识库文档只能通过后端的 API 进行导入，缺乏一个前端的可视化管理界面。用户无法直观地查看已上传的文档列表、解析状态，也无法删除不再需要的文档。

为了提供完整的知识库体验，我们需要在前端实现一个知识库管理面板，并在后端补齐相关的管理接口（特别是删除接口和分页查询接口）。

## Goals / Non-Goals

**Goals:**
- 提供前端可视化界面，支持上传、查看和删除知识库文档。
- 补齐后端文档删除 API，确保向量数据和元数据的级联删除。
- 完善文档列表 API，支持分页和状态展示。
- 修复现有 RAG 检索中的一些潜在问题（如向量未正确写入 `embedding` 列，检索 SQL 参数绑定问题等）。

**Non-Goals:**
- 不实现多租户隔离（当前为单用户本地工具）。
- 不实现文档的增量更新（仅支持删除后重新上传）。
- 不实现复杂的文档解析策略配置（使用现有的统一 ETL 策略）。

## Decisions

1. **前端管理面板位置**
   - **Decision**: 在 `StudioPanel.vue` 中新增一个“知识库管理”区块，或者作为一个独立的弹窗/页面。考虑到 `StudioPanel` 已经承担了参数配置等功能，我们将知识库管理作为一个独立的 Tab 或区块集成在 `StudioPanel` 中。
   - **Rationale**: 集中管理配置项，避免引入过多的页面层级。

2. **后端删除接口与级联删除**
   - **Decision**: 实现 `DELETE /api/ai/knowledge/documents/{id}` 接口。在数据库层面，`document_chunks` 表已经配置了 `ON DELETE CASCADE`，因此只需删除 `knowledge_documents` 记录即可自动清理向量块。
   - **Rationale**: 利用数据库外键级联删除，简化应用层逻辑，保证数据一致性。

3. **修复已知的 RAG 向量存储与检索问题**
   - **Decision**: 
     - 修复 `DocumentETLPipeline` 中向量未写入 `embedding` 列的问题（目前被序列化为 JSON 写入了 `metadata`）。
     - 修复 `RAGRetrievalService` 中检索 SQL 的参数问题（`document_id = :docId` 在 `docId` 为 null 时会导致查不到数据，应改为可选过滤或移除该条件）。
   - **Rationale**: 确保现有的 RAG 检索链路真正可用。

## Risks / Trade-offs

- **[Risk]** 向量删除可能导致短时间的数据库锁或性能抖动。
  - **Mitigation**: 知识库规模较小（单用户），级联删除性能可控。
- **[Risk]** 前端轮询解析状态可能增加后端压力。
  - **Mitigation**: 限制轮询频率（如每 3 秒一次），且在解析完成或失败后停止轮询。
