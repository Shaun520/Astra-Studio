## ADDED Requirements

### Requirement: 知识库前端管理面板
系统 SHALL 在前端提供一个知识库管理面板，允许用户查看、上传和删除知识库文档。

#### Scenario: 打开知识库管理面板
- **WHEN** 用户在 StudioPanel 中点击"知识库管理"
- **THEN** 系统展示当前已上传的文档列表，包括文档名称、状态（解析中、已就绪、失败）、分块数量和上传时间。

#### Scenario: 上传新文档
- **WHEN** 用户在面板中选择文件并触发上传
- **THEN** 前端先将文件上传至 OSS 获取 URL，然后调用 `POST /api/ai/knowledge/upload` 提交给后端进行 ETL 解析，并在列表中展示"解析中"状态。

#### Scenario: 轮询文档状态
- **WHEN** 列表中存在状态为 `PROCESSING` 的文档
- **THEN** 前端每隔 3 秒调用状态查询接口，直到状态变为 `READY` 或 `FAILED`。

#### Scenario: 删除文档
- **WHEN** 用户点击某个文档的删除按钮并确认
- **THEN** 前端调用 `DELETE /api/ai/knowledge/documents/{id}`，成功后从列表中移除该文档。

---

### Requirement: 后端文档删除接口
系统 SHALL 提供 `DELETE /api/ai/knowledge/documents/{id}` 接口，用于删除指定的知识库文档。

#### Scenario: 成功删除文档
- **WHEN** 调用方发送 `DELETE /api/ai/knowledge/documents/{id}`
- **THEN** 系统从 `knowledge_documents` 表中删除对应记录，数据库外键级联自动删除 `document_chunks` 中的相关向量数据，返回 HTTP 204 No Content。

#### Scenario: 删除不存在的文档
- **WHEN** 调用方尝试删除一个不存在的文档 ID
- **THEN** 系统返回 HTTP 404 Not Found。

---

### Requirement: 后端文档列表接口完善
系统 SHALL 完善 `GET /api/ai/knowledge/documents` 接口，支持分页和按时间倒序排列。

#### Scenario: 获取分页文档列表
- **WHEN** 调用方发送 `GET /api/ai/knowledge/documents?page=0&size=10`
- **THEN** 系统返回按 `created_at` 降序排列的文档分页数据，包含总记录数、总页数和当前页的数据。返回格式为自定义 Map 结构（避免 PageImpl 序列化警告）。

> ⚠️ **实现要点**：不直接返回 Spring Data 的 `PageImpl` 对象，而是手动构建包含 `content`, `totalElements`, `totalPages`, `pageable` 等字段的 Map。

---

### Requirement: 后端单文档状态查询接口
系统 SHALL 提供 `GET /api/ai/knowledge/documents/{id}` 接口，用于查询单个文档的详细状态。

#### Scenario: 查询单文档状态
- **WHEN** 调用方发送 `GET /api/ai/knowledge/documents/{id}`
- **THEN** 系统返回该文档的当前状态（PROCESSING / READY / FAILED）、chunk_count（NULL 安全处理，返回 0）以及 error_message（如果失败）

> ⚠️ **实现要点**：`chunkCount` 可能为 NULL（ETL 失败时），使用三目运算符 `doc.getChunkCount() != null ? doc.getChunkCount() : 0` 避免 NPE。
