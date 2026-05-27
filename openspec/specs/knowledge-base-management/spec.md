## ADDED Requirements

### Requirement: 知识库前端管理面板
系统 SHALL 在前端提供一个知识库管理面板，允许用户查看、上传和删除知识库文档（包括文本和图片类型）。

#### Scenario: 打开知识库管理面板
- **WHEN** 用户在 StudioPanel 中点击"知识库管理"
- **THEN** 系统展示当前已上传的文档列表，包括：文档名称、内容类型图标（📄 文本 / 📷 图片）、状态（解析中、已就绪、失败）、分块数量、上传时间

#### Scenario: 上传新文档（文本或图片）
- **WHEN** 用户在面板中选择文件并触发上传
- **THEN** 前端校验文件类型：
  - 文本类（PDF/Word/TXT/Markdown）：走原有流程上传至 OSS
  - 图片类（JPG/PNG/WebP）：显示缩略图预览后上传至 OSS
  - 调用 `POST /api/ai/knowledge/upload` 提交给后端进行 ETL 解析
  - 图片类型文档在列表中显示"解析中（图片处理较慢）"状态

#### Scenario: 轮询文档状态
- **WHEN** 列表中存在状态为 `PROCESSING` 的文档
- **THEN** 前端每隔 3 秒调用状态查询接口，直到状态变为 `READY` 或 `FAILED`

#### Scenario: 删除文档
- **WHEN** 用户点击某个文档的删除按钮并确认
- **THEN** 前端调用 `DELETE /api/ai/knowledge/documents/{id}`，成功后从列表中移除该文档（文本或图片均可删除）

---

### Requirement: 后端文档删除接口
系统 SHALL 提供 `DELETE /api/ai/knowledge/documents/{id}` 接口，用于删除指定的知识库文档（支持文本和图片类型）。

#### Scenario: 成功删除图片文档
- **WHEN** 调用方发送 `DELETE /api/ai/knowledge/documents/{id}` 且该文档为图片类型
- **THEN** 系统从 `knowledge_documents` 表中删除对应记录，数据库外键级联自动删除 `document_chunks` 中的相关向量数据（含图片元数据的 chunks），返回 HTTP 204 No Content

#### Scenario: 删除不存在的文档
- **WHEN** 调用方尝试删除一个不存在的文档 ID
- **THEN** 系统返回 HTTP 404 Not Found

---

### Requirement: 后端文档列表接口完善
系统 SHALL 完善 `GET /api/ai/knowledge/documents` 接口，支持分页、按时间倒序排列，并在返回结果中包含 `content_type` 字段。

#### Scenario: 获取混合类型文档列表
- **WHEN** 调用方发送 `GET /api/ai/knowledge/documents?page=0&size=10`
- **THEN** 系统返回按 `created_at` 降序排列的文档分页数据，每条记录包含：`id`, `fileName`, `contentType`（text/image）, `status`, `chunkCount`, `createdAt`, `errorMessage`（如有）

---

### Requirement: 后端单文档状态查询接口
系统 SHALL 提供 `GET /api/ai/knowledge/documents/{id}` 接口，用于查询单个文档的详细状态（包括图片类型的元数据）。

#### Scenario: 查询图片文档状态
- **WHEN** 调用方发送 `GET /api/ai/knowledge/documents/{id}` 且该文档为图片类型
- **THEN** 系统返回该文档的当前状态（PROCESSING / READY / FAILED）、chunk_count（NULL 安全处理）、error_message（如果失败）以及 `content_type: "image"` 字段

### Requirement: 图片文档预览接口
系统 SHALL 提供 `GET /api/ai/knowledge/documents/{id}/preview` 接口用于获取图片文档的预览 URL。

#### Scenario: 获取图片文档预览
- **WHEN** 调用方发送 `GET /api/ai/knowledge/documents/{id}/preview` 且该文档 contentType='image' 且有有效 OSS URL
- **THEN** 系统返回 `{ documentId, fileName, contentType, previewUrl, status }`，previewUrl 为原始 OSS 地址

#### Scenario: 非图片类型文档预览
- **WHEN** 调用方对文本文档发送预览请求
- **THEN** 系统返回 HTTP 404，error="仅支持图片类型文档预览"
