## ADDED Requirements

### Requirement: 图片格式支持与校验
系统 SHALL 支持上传 JPG/JPEG、PNG、WebP 格式的图片文件，并在接收时进行格式和大小校验。

#### Scenario: 上传合法图片格式
- **WHEN** 用户通过 `POST /api/ai/knowledge/upload` 上传 `{ fileUrl: "https://oss.xxx/image.png", fileName: "screenshot.png" }`，且 MIME 类型为 `image/jpeg`、`image/png` 或 `image/webp`
- **THEN** 系统接受该文件，在 `knowledge_documents` 表中创建记录，设置 `content_type = 'image'`，状态为 `PROCESSING`

#### Scenario: 拒绝不支持的图片格式
- **WHEN** 用户尝试上传 GIF、BMP、SVG 或其他非目标格式的图片
- **THEN** 系统返回 HTTP 400 Bad Request，错误信息包含 "不支持的图片格式，仅支持 JPG/PNG/WebP"

#### Scenario: 图片文件大小超限
- **WHEN** 上传的图片文件大小超过配置的限制（默认 10MB）
- **THEN** 系统返回 HTTP 413 Payload Too Large，错误信息包含当前限制大小

---

### Requirement: 图片多模态向量化（DashScope Multimodal-Embedding API）
系统 SHALL 使用 DashScope 多模态 Embedding 模型（tongyi-embedding-vision-plus-2026-03-06）通过 `parameters.dimensions: 1024` 将图片编码为 1024 维向量（与 text-embedding-v3 统一），保留完整视觉特征。

#### Scenario: 调用多模态 Embedding API（URL 模式）
- **WHEN** ETL 管道检测到文档类型为 `image` 且图片有公开访问的 OSS URL
- **THEN** 系统：
  1. 构造 Multimodal-Embedding API 请求体：
     ```json
     {
       "model": "tongyi-embedding-vision-plus-2026-03-06",
       "input": {
         "contents": [{"image": "https://oss.xxx/image.png"}],
         "parameters": {
           "dimensions": 1024,
           "instruct": "用于RAG检索的图片内容描述"
         }
       }
     }
     ```
  2. 调用 `POST https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding`
  3. 解析返回结果，提取 `output.embeddings[0].embedding` 数组（长度应为 1024）

#### Scenario: 调用多模态 Embedding API（Base64 模式）
- **WHEN** 图片无公开 URL 或需要私有存储（如内网 OSS）
- **THEN** 系统：
  1. 通过 HTTP 客户端下载图片到临时文件
  2. 将图片文件读取并 Base64 编码
  3. 构造请求体，`image` 字段使用 `data:image/png;base64,{base64_data}` 格式
  4. 调用 API 并解析响应

#### Scenario: 向量化成功（单 Chunk 存储）
- **WHEN** API 返回 HTTP 200 且 embedding 数组长度 = 1024
- **THEN** 系统创建**单个 chunk**（整张图片作为一个向量单元），字段如下：
  - `document_id`: 关联的知识库文档 ID
  - `chunk_index`: 0（固定值，单 Chunk 模式）
  - `content`: 图片文件名或可选的简短描述（如"截图_架构设计.png"）
  - `embedding`: 1024 维浮点数向量数组
  - `metadata`: JSONB 格式：`{ source_type: "image", image_format: "png", original_file: "screenshot.png", vector_type: "multimodal", model: "tongyi-embedding-vision-plus-2026-03-06" }`
  - 更新文档状态为 `READY`，设置 `chunk_count = 1`

#### Scenario: API 调用失败处理
- **WHEN** API 返回错误（网络超时、认证失败 401、模型不可用、图片格式不支持等）
- **THEN** 系统：
  1. 记录 ERROR 日志，包含原始异常堆栈和请求 ID
  2. 更新文档状态为 `FAILED`
  3. 设置 `error_message` 为 "图片向量化失败: {具体原因} (错误码: {http_status})"
  4. 不创建任何 chunk 记录

#### Scenario: 向量维度不匹配
- **WHEN** API 返回成功但 embedding 数组长度 ≠ 1024（如配置错误导致返回 768 或 1152 维）
- **THEN** 系统视为配置错误，记录 CRITICAL 日志"多模态 Embedding 返回维度 {actual_dim} 与预期 1024 不匹配"，更新文档状态为 FAILED

---

### Requirement: 图片单 Chunk 存储策略
系统 SHALL 对图片采用**单 Chunk 模式**（每张图片生成 1 个向量单元），简化存储和检索逻辑。

#### Scenario: 单张图片作为单个 Chunk
- **WHEN** 图片成功通过多模态 Embedding 向量化
- **THEN** 系统将整张图片存储为 **1 个 chunk**（chunk_index=0），原因：
  1. 多模态 Embedding 已捕获全局视觉特征，无需再分割
  2. 避免过度切割导致语义丢失（如流程图被切分后失去完整性）
  3. 降低存储成本（1张图=1个chunk=4KB vs 原方案的1-10个chunks）
  4. 提升检索速度（更少的向量比较操作）

#### Scenario: 图片元数据标注规范
- **WHEN** 创建图片类型的 chunk 记录
- **THEN** metadata JSONB 字段必须包含以下标准字段：
  ```json
  {
    "source_type": "image",
    "image_format": "png|jpeg|webp",
    "original_file": "original_filename.png",
    "vector_type": "multimodal",
    "model": "tongyi-embedding-vision-plus-2026-03-06",
    "dimensions": 1024,
    "created_at": "2026-05-26T10:00:00"
  }
  ```

#### Scenario: 未来扩展预留（象限分割）
- **WHEN** 后续版本需要支持超大图片（>4096x4096 或 >5MB）的精细检索
- **THEN** 系统可将图片切分为 4 个象限，分别调用 Embedding API 生成多个 chunks（当前版本不实现，通过配置项 `knowledge-base.multimodal-embedding.enable-quadrant-split=false` 控制）

---

### Requirement: 向量化与存储（统一向量空间）
系统 SHALL 将图片通过多模态 Embedding 生成的 1024 维向量直接存入 Pgvector，与文本文档共享同一向量空间。

#### Scenario: 图片 Chunk 写入数据库
- **WHEN** 图片成功向量化并创建单个 chunk
- **THEN** 系统将 chunk 写入 `document_chunks` 表，字段包括：`document_id`, `chunk_index`(0), `content`(文件名), `embedding`(1024维 vector), `metadata`(JSONB), `token_count`(可选, 用于统计)

#### Scenario: 混合检索兼容性（跨模态语义空间）
- **WHEN** RAG 检索查询命中图片类型的 chunk
- **THEN** 返回结果中 `metadata.source_type = "image"` 且 `metadata.vector_type = "multimodal"`，前端可根据此字段显示 📷 图标；**文本和图片向量可在同一余弦相似度计算中比较，无需特殊处理**

#### Scenario: 向量维度一致性验证
- **WHEN** 系统启动或配置变更时
- **THEN** 自动校验多模态 Embedding 模型的输出维度是否等于文本 Embedding 的维度（均为 1024），若不匹配则记录 CRITICAL 日志并禁止启用图片上传功能

---

### Requirement: 前端图片上传增强
系统 SHALL 在知识库管理面板中提供图片友好的上传体验。

#### Scenario: 文件选择器支持图片过滤
- **WHEN** 用户点击"上传文档"按钮触发文件选择对话框
- **THEN** 文件选择器的 accept 属性包含 `.jpg,.jpeg,.png,.webp,image/*`，对话框默认显示"图片文件"过滤器

#### Scenario: 图片缩略图预览
- **WHEN** 用户选择了一张本地图片文件（尚未上传）
- **THEN** 前端使用 `URL.createObjectURL(file)` 生成临时 URL，在弹窗中展示图片缩略图（最大宽度 400px，保持宽高比），并显示文件名和大小

#### Scenario: 图片类型文档列表展示
- **WHEN** 知识库文档列表中存在 `content_type = 'image'` 的文档
- **THEN** 该文档行显示缩略图图标（📷）、文件名、状态标签；鼠标悬停时可预览原图（若 OSS URL 可访问）

#### Scenario: 上传进度提示（图片快速处理）
- **WHEN** 用户上传图片文件并触发 ETL
- **THEN** 前端在文档列表中显示"解析中（图片向量化处理）"提示，轮询间隔保持 3 秒；由于多模态 Embedding API 响应迅速（通常 <1秒），大多数情况下用户会很快看到状态变为 READY

---

### Requirement: 配置开关与限流控制
系统 SHALL 提供图片上传和多模态 Embedding 功能的配置开关和使用限制。

#### Scenario: 功能开关禁用图片上传
- **WHEN** 配置项 `knowledge-base.multimodal-embedding.enabled = false`
- **THEN** 后端上传接口拒绝图片类型文件（返回 HTTP 403 "图片上传功能已禁用"），前端隐藏图片相关 UI 元素

#### Scenario: 单次上传数量限制
- **WHEN** 用户同时选择超过 10 张图片进行批量上传（多模态 Embedding 成本低，可适当放宽限制）
- **THEN** 前端弹出警告"单次最多上传 10 张图片"，阻止提交；或后端返回 HTTP 422 "超出单次上传数量限制"

#### Scenario: 图片大小和格式校验
- **WHEN** 用户选择图片文件
- **THEN** 前端和后端双重校验：
  - 文件大小 ≤ 5MB（DashScope API 限制）
  - 格式为 JPG/PNG/WebP（符合 API 支持列表）
  - 若超出限制，前端显示具体错误信息并阻止上传

#### Scenario: 图片分辨率建议（非强制）
- **WHEN** 用户选择的图片分辨率低于 800x600
- **THEN** 前端显示警告提示"图片分辨率较低，可能影响向量化效果，建议上传清晰度较高的图片"，但不阻止上传（用户可忽略）
