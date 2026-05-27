## Why

当前知识库系统仅支持文本类文档（PDF/Word/TXT/Markdown）的上传与向量化，无法处理图片内容。随着企业知识库场景的扩展，大量知识以图片形式存在（如截图、扫描件、图表、流程图等），用户迫切需要将图片纳入 RAG 检索范围，实现"所见即所得"的知识检索体验。

## What Changes

- **新增图片解析能力**：扩展 DocumentETLPipeline 管道，支持 JPG/PNG/WebP 等常见图片格式的上传与内容提取
- **集成多模态 Embedding 模型**：使用 DashScope/qwen-vl 等视觉语言模型将图片转换为文本描述或直接生成图片向量
- **智能切片策略**：针对图片特点优化分块逻辑（如按图片区域、OCR 文本段落分割）
- **前端文件选择器增强**：在知识库管理面板中添加图片类型过滤器，支持拖拽上传图片
- **向后兼容**：现有文本文档 ETL 流程不受影响，新增能力作为可选模块注入

## Capabilities

### New Capabilities
- `image-upload`: 图片文档上传与解析管道，包括图片内容提取（OCR/视觉理解）、分块策略、向量化和存储全流程

### Modified Capabilities
- `knowledge-base-management`: 扩展支持的文件类型列表，增加图片格式校验和预览功能
- `knowledge-base-rag`: 适配混合向量检索（文本向量 + 图片向量），优化相似度计算策略

## Impact

**代码影响**：
- 后端：`DocumentETLPipeline` 新增图片解析分支；新增 `ImageParserService`；修改 `KnowledgeController` 上传接口的 MIME 类型白名单
- 前端：`KnowledgePanel.vue` 增加图片类型支持和缩略图预览
- 数据库：`knowledge_documents` 表可能新增 `content_type` 字段区分 text/image

**API 变更**：
- `POST /api/ai/knowledge/upload` 扩展接受 `image/jpeg`, `image/png`, `image/webp`
- 可能新增 `GET /api/ai/knowledge/documents/{id}/preview` 用于图片预览

**依赖变更**：
- 新增 DashScope VL API 调用（qwen-vl-max 或类似模型）
- 可选依赖 Apache Tika 的图片元数据提取能力

**系统影响**：
- ETL 管道执行时间增加（图片解析比文本慢）
- 向量存储空间增长（需评估图片向量的维度和存储成本）
