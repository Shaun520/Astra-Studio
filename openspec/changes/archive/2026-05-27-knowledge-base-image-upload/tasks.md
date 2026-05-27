## 1. 数据库与配置准备

- [x] 1.1 在 `knowledge_documents` 表新增 `content_type` 字段（ENUM: text/image），默认值 'text'，并添加 Flyway 迁移脚本
- [x] 1.2 在 `application.yaml` 中添加**多模态 Embedding**相关配置项：
  ```yaml
  knowledge-base:
    multimodal-embedding:
      enabled: true
      model-name: tongyi-embedding-vision-plus-2026-03-06
      dimensions: 1024  # 强制输出1024维（与 text-embedding-v3 统一）
      api-endpoint: https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding
      timeout-seconds: 30
      max-retries: 3
      instruct: "用于RAG检索的图片内容描述"  # 可选，提升1-5%精度
      max-file-size: 5MB  # DashScope API 限制
      supported-formats: jpg,png,webp
      max-batch-count: 10  # 单次上传数量限制（成本低可放宽）
  ```
- [x] 1.3 确认 `document_chunks` 表的 metadata JSONB 字段可存储扩展结构：`{ source_type, image_format, original_file, vector_type, model, dimensions }`
- [x] 1.4 （可选）添加向量维度一致性校验启动逻辑：系统启动时验证文本 Embedding（1024维）和多模态 Embedding（1024维）维度一致

## 2. 后端核心服务 - 多模态 Embedding 管道

- [x] 2.1 创建 **`MultimodalEmbeddingService.java`** 类，实现以下核心功能：
  - 封装 DashScope Multimodal-Embedding API 调用逻辑（HTTP Client + JSON 序列化/反序列化）
  - 支持**两种输入模式**：
    a) **URL 模式**（推荐）：直接传入 OSS 公开 URL，无需下载图片
    b) **Base64 模式**：下载图片文件后 Base64 编码传入（用于私有存储场景）
  - 构造标准请求体：
    ```json
    {
      "model": "${knowledge-base.multimodal-embedding.model-name}",
      "input": {
        "contents": [{"image": "url_or_base64"}],
        "parameters": {
          "dimensions": ${knowledge-base.multimodal-embedding.dimensions},
          "instruct": "${knowledge-base.multimodal-embedding.instruct}"
        }
      }
    }
    ```
  - 解析响应：提取 `output.embeddings[0].embedding` 数组，验证长度 = 1024
  - 返回值：`float[1024]` 向量数组 + 元数据（model名称、调用耗时等）
  - 异常处理：
    - 网络超时（>30秒）→ 重试 3 次后抛出异常
    - HTTP 429（限流）→ 等待后重试或降级
    - HTTP 401/403（认证失败）→ 记录 CRITICAL 日志，禁止后续调用
    - 图片格式不支持 → 返回明确错误信息
    - 向量维度不匹配 → 记录 CRITICAL 日志，标记为配置错误

- [x] 2.2 在 `MultimodalEmbeddingService` 中添加图片预处理和校验逻辑：
  - MIME 类型白名单校验（image/jpeg, image/png, image/webp）
  - 文件大小校验（≤ 5MB，符合 DashScope API 要求）
  - 图片分辨率读取（使用 javax.imageio.ImageIO 获取宽高，用于日志记录和建议提示）

- [x] 2.3 扩展 **`DocumentETLPipeline.java`** 的 `process()` 方法，增加图片类型分支：
  - 若 `contentType == 'image'` → 调用 `MultimodalEmbeddingService.embedImage(imageUrl)` 获取向量
  - 直接创建**单个 chunk**（chunk_index=0），包含完整图片元数据
  - 更新文档状态为 READY，设置 chunk_count=1
  - 若 `contentType == 'text'` → 保持原有 Tika + TextChunker + Embedding 流程不变
  - 统一异常处理：任一步骤失败 → 状态 FAILED + error_message

## 3. 后端核心服务 - 向量存储兼容性验证

- [x] 3.1 验证现有 Pgvector 写入逻辑兼容性：
  - 确认 **1024** 维向量空间统一（文本 text-embedding-v3 + 图片 multimodal-embedding-vision-plus）
  - 确认 `DocumentChunkEntity.embedding` 字段的 `@Array(length = 1024)` 和 `vector(1024)` 映射正确
  - 测试混合类型 chunk 的写入和查询性能（文本+图片共存场景）

- [x] 3.2 ~~删除~~ 原方案的 `ImageParserService` 和 `ImageChunkerStrategy`（不再需要 VL 文本提取和复杂分块逻辑）

- [x] 3.3 实现基于内容哈希的**去重缓存机制**（可选优化）：
  - 使用 SHA-256 对图片 URL 或文件内容计算哈希值作为缓存键
  - 相同图片不重复调用 Multimodal-Embedding API（节省成本）
  - 缓存策略：ConcurrentHashMap + TTL（30分钟）+ 最大容量（100条）

## 4. 后端 API 层改造

- [x] 4.1 修改 `KnowledgeController.uploadDocument()` 接口：
  - 根据 fileName 后缀（.jpg/.png/.webp）或 HTTP Content-Type 自动识别 `contentType`
  - 新增请求参数校验：
    - 若为图片类型且 `multimodal-embedding.enabled=false` → 返回 HTTP 403 "图片上传功能已禁用"
    - 若单次请求包含 >10 张图片 → 返回 HTTP 422 "超出单次上传数量限制"
    - 若图片大小 >5MB → 返回 HTTP 413 "图片文件过大，限制 5MB"
  - 在 `knowledge_documents` 记录中正确设置 `content_type='image'`

- [x] 4.2 修改 `KnowledgeController.getDocuments()` 和 `getDocumentById()` 接口：
  - 返回 DTO 中新增 `contentType` 字段（text/image）
  - 确保 分页、排序、过滤逻辑不受影响
  - 对图片类型文档额外返回 `previewUrl`（若 OSS URL 可访问）

- [x] 4.3 （可选）新增 `GET /api/ai/knowledge/documents/{id}/preview` 接口：
  - 仅对 `contentType='image'` 且 OSS URL 可访问的文档返回临时预览 URL
  - 对文本文档返回 HTTP 404 或空响应
  - 用于前端缩略图预览和 sources 卡片点击预览

## 5. 前端 UI 增强

- [x] 5.1 修改 `KnowledgePanel.vue` 的文件选择器组件：
  - `<input type="file">` 增加 accept 属性：`.pdf,.doc,.docx,.txt,.md,.jpg,.jpeg,.png,.webp`
  - 添加文件类型图标显示（📄 文本 / 📷 图片）
  - 选择图片后触发 `URL.createObjectURL(file)` 生成缩略图预览
  - 实现分辨率建议提示（<800x600 时显示黄色警告条："图片分辨率较低，可能影响向量化效果"）
  - 实现文件大小校验提示（>5MB 时显示红色错误提示）

- [x] 5.2 新建或修改 `ImagePreview.vue` 组件：
  - 接收 File 对象或 Blob URL 作为 prop（已在 KnowledgeBasePanel 中内联实现）
  - 渲染 `<img>` 标签展示缩略图（max-width: 400px, object-fit: contain, 圆角边框）
  - 显示文件名、大小（格式化为 KB/MB）、尺寸信息（宽x高）
  - 加载状态指示器（图片加载中显示 spinner）

- [x] 5.3 修改文档列表渲染逻辑：
  - 根据 `contentType` 字段显示不同图标（📄 / 📷 ImageIcon）
  - 图片类型文档在状态列显示文案："解析中（图片向量化处理）"/ "READY 1向量"
  - 鼠标悬停图片文档行时通过 ExternalLink 按钮打开原图预览（新窗口）
  - 成功状态的特殊标识：图片文档显示 ✅ + 文件名 + "已索引（1个向量）"

## 6. SSE 溯源信息适配（跨模态来源展示）

- [x] 6.1 修改 `ChatService.java` 中推送 sources 事件的逻辑：
  - 从 RAG 检索结果的 metadata JSON 中提取字段（通过 RetrievedChunk.sourceType/metadata 字段）
  - 构造 sources 事件数据结构包含 sourceType: "text" | "image" 和 metadata 原始 JSON

- [x] 6.2 修改前端 `ChatMessage.vue` 的 SourcesCard 组件：
  - 根据 sourceType 显示不同图标（Library 文本 / ImageIcon 图片）
  - **文本来源卡片**：默认灰色背景 + 文档名 + 引用片段
  - **图片来源卡片**：
    - 淡蓝色背景（border-blue-500/20 bg-blue-500/5）视觉区分
    - ImageIcon 图标 + 文件名
    - 蓝色 score 显示（text-blue-400/70）
  - Tooltip 提示：鼠标悬停显示"来自多模态向量检索"

## 7. 测试与验证

- [ ] 7.1 编写单元测试 **`MultimodalEmbeddingServiceTest.java`**：
   - 测试正常向量化流程（Mock API 返回 1024 维向量）
  - 测试 URL 模式和 Base64 模式两种输入方式
  - 测试异常场景：网络超时、API 500/429/401、空响应、维度不匹配
  - 测试图片格式校验（合法 JPG/PNG/WebP vs 非法 GIF/BMP/SVG）
  - 测试文件大小超限处理（≤5MB vs >5MB）
  - 测试参数配置（dimensions、instruct 等）

- [ ] 7.2 编写单元测试 **`DocumentETLPipelineImageTest.java`**：
  - 测试图片 ETL 全流程（embedImage → createSingleChunk → saveToPgvector）
  - 测试混合知识库检索（文本 chunk + 图片 chunk 共存时的 Top-K 结果排序）
  - 测试状态流转：PROCESSING → READY (chunk_count=1) / FAILED (error_message)
  - 测试功能开关：`multimodal-embedding.enabled=false` 时拒绝图片上传
  - 测试向量维度一致性校验逻辑

- [ ] 7.3 手动集成测试（端到端验证）：
  - **基础功能测试**：
    - 上传 JPG/PNG/WebP 格式图片各一张，验证 ETL 全流程成功（预计 <3秒完成）
    - 验证数据库：`document_chunks` 表新增 1 条记录，`metadata.source_type="image"`
    - 验证向量：`embedding` 字段长度=1024，非空且非全零
  - **边界条件测试**：
    - 上传低分辨率图片（<800x600），验证前端警告提示但不阻止上传
    - 上传超大图片（>5MB），验证后端返回 HTTP 413 错误
    - 上传不支持的格式（GIF/BMP），验证后端返回 HTTP 400 错误
    - 批量上传 11 张图片（超过限制 10 张），验证前端拦截或后端返回 422
  - **跨模态检索测试**（核心场景！）：
    - 准备测试数据：上传 3-5 张不同类型的图片（架构图、截图、流程图、照片等）
    - 在对话中启用知识库模式，提问：
      - "系统架构图在哪里？" → 应命中架构图 chunk，sources 显示 📷
      - "这张截图讲了什么功能？" → 应命中对应截图
      - "流程图的步骤有哪些？" → 应命中流程图图片
    - 验证 RAG 检索结果：Top-K 结果中文本和图片 chunk 混合排列
    - 验证 sources 事件：正确标注 sourceType="image"，显示 📷 图标
    - 验证 LLM 回答质量：基于图片上下文生成的回答是否准确相关
  - **性能基准测试**：
    - 单张图片 ETL 耗时：<3秒（API 调用 + 数据库写入）
    - 混合知识库 Top-5 检索 P95 延迟：<2000ms（含 Query Embedding）
    - 并发上传 10 张图片的总耗时：<30秒
  - **配置开关测试**：
    - 设置 `multimodal-embedding.enabled=false`，验证：
      - 前端隐藏图片上传选项
      - 后端拒绝图片类型文件上传（HTTP 403）
      - 已有图片文档仍可正常查询和删除
  - **回滚与容错测试**：
    - 模拟 API 故障（网络断开），验证文档状态变为 FAILED + 明确错误信息
    - 删除已上传的图片文档，验证级联删除 chunk 记录
    - 重复上传相同图片（相同 OSS URL），验证去重缓存生效（第2次应更快）
