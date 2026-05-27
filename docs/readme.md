# Astra Studio

Astra Studio 是一个前后端分离的 AI 创作工作台。前端负责交互、附件上传和流式内容展示；后端负责模型接入、会话记忆、联网搜索、深度思考和自动模型路由。

当前仓库按两个子项目组织：

- `Astra-Studio`：前端项目，基于 Vue 3、Vite 和 Tailwind CSS。
- `Astra-Studio-Open-Ai`：后端项目，基于 Spring Boot 3、Java 21 和 LangChain4j。

## 一、功能现状

- 支持普通对话、深度思考、联网搜索和模型选择。
- 支持 SSE 流式响应，前端可分段接收 thinking 与 text 内容。
- 支持图片、文档等文件上传到 OSS（后端预签名），再把文件 URL 作为上下文传给后端。
- 支持自动模型路由，根据用户输入意图选择更合适的模型。
- **支持知识库（RAG）**：可上传 PDF/DOCX/TXT 文档，AI 回答时自动检索相关知识库内容并引用回答。
  - 前端提供知识库管理面板（上传、列表、删除、状态轮询、文档预览）。
  - 后端基于 PostgreSQL + Pgvector 实现向量存储与相似度搜索。
  - 使用 DashScope `text-embedding-v3` 模型生成 1024 维嵌入向量。
  - SSE 流式推送中新增 `sources` 事件，展示参考文档来源。
- **支持会话管理**：创建、恢复、删除、搜索对话，支持会话标题编辑和历史消息浏览。
  - 前端侧边栏展示会话列表，支持搜索、新建、恢复、删除操作。
  - 后端提供会话 CRUD 接口，消息持久化到数据库。
  - 页面刷新后自动恢复上次会话。
- **支持工具调用**：后端注册多种工具（如 PDF 生成），前端可选择工具并执行。
- **支持聊天参数调节**：temperature、maxTokens、topP、systemPrompt 等参数可实时调整。
- **Toast 通知系统**：统一的错误分类与提示，支持 success/error/warning/info/loading 类型。
- 后端已有服务缓存和路由规则文件，便于后续继续扩展模型、工具和功能开关。

## 二、项目结构

```text
.
├── Astra-Studio/                 # 前端
│   ├── src/
│   │   ├── components/           # 页面组件与业务组件
│   │   │   ├── ai/               # AI 相关组件（图像生成、视频生成、图片预览）
│   │   │   ├── chat/             # 聊天组件（消息、输入框、附件卡片、PDF下载卡片、右键菜单）
│   │   │   ├── common/           # 通用组件（消息历史视图、骨架加载、Toast容器）
│   │   │   ├── conversation/     # 会话管理组件（会话卡片、搜索、标题编辑、删除确认）
│   │   │   ├── layout/           # 布局组件（侧边栏、顶部栏）
│   │   │   └── panels/           # 面板组件（工作空间、参数调节、知识库）
│   │   ├── composables/          # 组合式函数（useToast）
│   │   ├── services/             # API、OSS 等外部服务封装
│   │   │   ├── chat/             # 聊天服务（SSE 流式通信、类型定义）
│   │   │   ├── conversation/     # 会话服务（CRUD 接口）
│   │   │   ├── knowledge/        # 知识库服务（文档管理接口、类型定义）
│   │   │   ├── api.ts            # 统一 API 入口（含工具接口）
│   │   │   ├── index.ts          # 服务统一导出
│   │   │   └── oss.ts            # OSS 预签名上传
│   │   ├── types/                # 前端类型定义
│   │   │   ├── index.ts          # 全局类型（Message、Attachment、ModelInfo 等）
│   │   │   └── conversation.ts   # 会话相关类型
│   │   ├── utils/                # 工具函数
│   │   │   ├── debounce.ts       # 防抖、节流、异步防抖
│   │   │   ├── file.ts           # 文件大小格式化、类型标签、下载
│   │   │   ├── logger.ts         # 开发模式日志
│   │   │   └── markdown.ts       # highlight.js 语言注册
│   │   ├── App.vue
│   │   ├── main.ts
│   │   └── style.css
│   ├── .env.example              # 前端环境变量示例
│   ├── package.json
│   └── vite.config.ts
│
├── Astra-Studio-Open-Ai/          # 后端
│   ├── src/main/java/com/example/astrastudioopenai/
│   │   ├── ai/                   # LangChain4j 服务、模型工厂、MCP 配置
│   │   ├── config/               # Web、跨域等基础配置（含 ContentRetriever）
│   │   ├── controller/           # HTTP 接口（含知识库、会话、工具管理接口）
│   │   ├── dto/                  # 接口返回结构（含 RetrievedChunk）
│   │   ├── entity/               # 数据实体（KnowledgeDocument, DocumentChunk）
│   │   ├── repository/           # 数据访问层（含向量检索 SQL）
│   │   ├── routing/              # 意图识别与模型路由
│   │   ├── service/
│   │   │   ├── chat/             # 聊天服务（含 RAG 集成）
│   │   │   └── knowledge/        # 知识库服务（ETL、检索、管理）
│   │   └── utils/                # 消息构建等工具
│   ├── src/main/resources/
│   │   ├── application.yaml      # 主配置（含 RAG 参数）
│   │   ├── application-local.yaml
│   │   ├── intent-rules.yaml     # 意图路由规则
│   │   └── db/migration/         # Flyway 数据库迁移脚本
│   ├── pom.xml
│   └── mvnw.cmd
│
└── openspec/                      # 需求与规格沉淀
```

## 三、本地启动

### 3.1 环境要求

- Node.js：建议使用当前 Vite 版本支持的 LTS 版本。
- pnpm：前端项目已有 `pnpm-lock.yaml`。
- JDK 21：后端 `pom.xml` 指定 Java 21。

### 3.2 启动后端

进入后端目录：

```powershell
cd Astra-Studio-Open-Ai
```

配置模型密钥。建议只放在本地环境变量或本地配置文件中，不要提交真实密钥。

```yaml
custom:
  api-keys:
    dashscope: your_dashscope_api_key
    bigmodel: your_bigmodel_api_key
```

启动服务：

```powershell
.\mvnw.cmd spring-boot:run
```

默认服务地址：

```text
http://localhost:8089/api
```

### 3.3 启动前端

进入前端目录：

```powershell
cd Astra-Studio
```

安装依赖并启动：

```powershell
pnpm install
pnpm dev
```

前端默认请求后端地址通过环境变量 `VITE_API_BASE_URL` 配置，默认为：

```text
http://localhost:8089/api
```

如果需要自定义后端地址或 OSS 自定义域名，复制 `.env.example` 为 `.env`，并补齐配置：

```env
# 后端 API 地址（可选，默认 http://localhost:8089/api）
VITE_API_BASE_URL=http://localhost:8089/api

# 可选：OSS 自定义访问域名（如 CDN 域名）
# VITE_OSS_CUSTOM_DOMAIN=https://cdn.yourdomain.com
```

> **注意**：OSS 密钥已迁移至后端配置。前端不再直接持有 OSS AccessKey，而是通过后端 `/api/oss/presign` 接口获取预签名上传凭证。请在后端 `application-local.yaml` 中配置 OSS 参数：

```yaml
oss:
  access-key-id: your_oss_access_key_id
  access-key-secret: your_oss_access_key_secret
  bucket: your_bucket_name
  region: oss-cn-hangzhou
  endpoint: https://oss-cn-hangzhou.aliyuncs.com
```

## 四、主要接口

### 4.1 流式对话

```text
POST /api/ai/chat
Content-Type: multipart/form-data
Accept: text/event-stream
```

参数：

- `memoryId`：会话 ID，用于维持多轮上下文。
- `text`：用户输入文本。
- `files`：文件 URL 列表，可传多个。
- `deepThink`：是否启用深度思考。
- `webSearch`：是否启用联网搜索。
- `knowledgeBase`：是否启用知识库检索（默认 false）。
- `selectedTools`：选中的工具名称列表。
- `model`：模型名称，支持 `auto` 自动路由。
- `temperature`：生成温度（可选）。
- `maxTokens`：最大输出 token 数（可选）。
- `topP`：Top-P 采样参数（可选）。
- `systemPrompt`：自定义系统提示词（可选）。

返回内容通过 SSE 推送。当前主要事件数据类型包括：

- `thinking`：模型思考过程。
- `text`：最终回答正文。
- `sources`：知识库参考来源（knowledgeBase=true 时返回）。
- `routing_info`：自动路由结果。
- `done`：本次响应结束。
- `error`：异常信息。

### 4.2 路由统计

```text
GET /api/ai/routing-stats
```

用于查看自动模型路由的统计信息。

### 4.3 知识库管理接口

```text
# 上传文档（同步 ETL）
POST /api/ai/knowledge/upload
Body: { fileUrl, fileName }
Response: { documentId, status, chunkCount }

# 文档列表（分页）
GET /api/ai/knowledge/documents?page=0&size=10
Response: { content, totalElements, totalPages, ... }

# 单文档状态查询
GET /api/ai/knowledge/documents/{id}
Response: { status, chunkCount, errorMessage }

# 文档预览
GET /api/ai/knowledge/documents/{id}/preview
Response: { documentId, fileName, contentType, previewUrl, status }

# 删除文档（级联删除 chunks）
DELETE /api/ai/knowledge/documents/{id}
Response: 204 No Content

# 手动搜索测试
POST /api/ai/knowledge/search?query=xxx
Response: List<RetrievedChunk>
```

### 4.4 会话管理接口

```text
# 创建新会话
POST /api/ai/conversation
Body: { memoryId?, modelName? }
Response: ConversationItem

# 获取会话列表（分页、搜索）
GET /api/ai/conversation?page=0&size=20&keyword=xxx
Response: PageResult<ConversationItem>

# 获取单个会话
GET /api/ai/conversation/{memoryId}
Response: ConversationItem

# 获取会话消息（分页）
GET /api/ai/conversation/{memoryId}/messages?page=0&size=50&role=user
Response: PageResult<MessageItem>

# 更新会话标题
PUT /api/ai/conversation/{memoryId}/title
Body: { title }
Response: 204 No Content

# 删除会话
DELETE /api/ai/conversation/{memoryId}
Response: 204 No Content
```

### 4.5 工具管理接口

```text
# 获取所有已注册工具
GET /api/ai/tools
Response: List<ToolInfo>

# 执行工具
POST /api/ai/tools/{toolName}/execute
Body: { ...params }
Response: ToolExecuteResponse

# 下载 PDF（PDF 生成工具）
GET /api/ai/tools/pdfGeneratorTool/download?fileName=xxx
Response: application/pdf (Blob)
```

### 4.6 OSS 预签名接口

```text
# 获取 OSS 上传预签名
GET /api/ai/oss/presign?fileName=xxx
Response: { uploadUrl, objectName, policy, OSSAccessKeyId, signature, publicUrl }
```

前端拿到预签名后，直接向 OSS 发送 POST 上传文件，无需在前端持有 OSS 密钥。

## 五、配置说明

### 5.1 后端配置

`application.yaml` 管理服务端口、上下文路径、模型基础配置和自动路由配置。

```yaml
server:
  port: 8089
  servlet:
    context-path: /api

auto-routing:
  enabled: true
  confidence-threshold: 0.6
  default-model: glm-5
  rules-file: classpath:intent-rules.yaml
```

`intent-rules.yaml` 管理意图分类规则。新增路由场景时，优先在这里补充关键词、正则和目标模型，避免把规则散落在业务代码里。

### 5.2 知识库 RAG 配置

知识库功能在 `application.yaml` 中通过 `knowledge-base` 节点配置：

```yaml
knowledge-base:
  rag:
    enabled: true                    # 是否启用 RAG 功能
    embedding-model: text-embedding-v3   # DashScope 嵌入模型
    embedding-dimensions: 1024       # 向量维度（DashScope v3 仅支持此值）
    chunk-size: 512                  # 文本分块大小（tokens）
    chunk-overlap: 64                # 分块重叠（tokens）
    batch-size: 10                   # 嵌入 API 批次限制
    top-k: 5                         # 返回最相关的 K 条结果
    similarity-threshold: 0.5        # 余弦相似度阈值（距离 <= 0.5）
    retrieval-timeout-ms: 3000       # 检索超时时间
    cache-ttl-seconds: 30            # 检索结果缓存 TTL

sse:
  timeout-ms: 300000                 # SSE 超时（含 RAG 溯源推送）
```

**数据库要求**：PostgreSQL + Pgvector 扩展，需执行 Flyway 迁移脚本创建 `knowledge_documents` 和 `document_chunks` 表。

### 5.3 前端配置

前端 API 地址通过环境变量 `VITE_API_BASE_URL` 配置，在 `.env` 文件中设置：

```env
VITE_API_BASE_URL=http://localhost:8089/api
```

可选配置 OSS 自定义域名：

```env
VITE_OSS_CUSTOM_DOMAIN=https://cdn.yourdomain.com
```

聊天参数（temperature、maxTokens、topP、systemPrompt）通过前端面板实时调节，自动持久化到 `localStorage`。

## 六、扩展约定

### 6.1 新增前端页面或面板

1. 在 `src/components/` 对应子目录下新增组件：
   - `ai/`：AI 生成相关组件
   - `chat/`：聊天交互组件
   - `common/`：通用 UI 组件
   - `conversation/`：会话管理组件
   - `layout/`：布局组件
   - `panels/`：侧边面板组件
2. 如果组件需要调用后端，在 `src/services/` 对应子目录下封装请求逻辑。
3. 共享数据结构放到 `src/types/`，避免组件之间重复定义。
4. 页面级状态尽量留在 `App.vue` 或更明确的上层容器中，基础组件只接收 props 和事件。
5. 可复用的有状态逻辑放到 `src/composables/`，以 `use` 前缀命名。
6. 通用工具函数放到 `src/utils/`，保持纯函数、无副作用。

### 6.2 新增后端接口

1. 在 `controller/` 中新增入口。
2. 业务逻辑放到独立 service 或现有业务模块中。
3. 请求、响应结构放到 `dto/`，不要直接暴露内部对象。
4. 涉及模型调用时，优先复用 `ai/` 下的服务工厂和已有缓存机制。

### 6.3 新增模型

1. 在 `AiCodeHelperServiceFactory` 的模型白名单中加入模型名称。
2. 确认模型支持的能力，例如流式输出、thinking、工具调用。
3. 如需自动路由，在 `intent-rules.yaml` 中配置目标模型。
4. 本地验证 `/api/ai/chat` 的普通模式和 `auto` 模式。

### 6.4 新增工具能力

1. 工具接入放在 `ai/mcp/` 或新的工具模块中。
2. 在服务工厂中按功能开关挂载工具。
3. 前端只暴露清晰的开关或入口，不直接关心后端工具细节。
4. 工具调用可能增加耗时，记得同步调整超时策略和错误提示。

### 6.5 新增会话管理功能

1. 会话相关接口统一放在 `controller/` 的会话控制器中。
2. 前端会话组件放在 `components/conversation/` 目录。
3. 会话服务封装放在 `services/conversation/` 目录。
4. 会话类型定义放在 `types/conversation.ts`。

### 6.6 新增通用 UI 组件

1. 通用组件放在 `components/common/` 目录。
2. 如果涉及全局状态通知（如 Toast），使用 `composables/useToast.ts`。
3. 如果涉及防抖/节流等通用逻辑，使用 `utils/debounce.ts`。

## 七、常用命令

前端：

```powershell
cd Astra-Studio
pnpm dev
pnpm build
pnpm preview
```

后端：

```powershell
cd Astra-Studio-Open-Ai
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd package
```

## 八、开发注意事项

- 不要把真实 API Key、OSS 密钥、模型密钥提交到仓库。
- OSS 上传已改为后端预签名模式，前端不持有 OSS AccessKey。
- 前端上传到 OSS 后，只把可访问 URL 传给后端。
- SSE 接口依赖长连接，代理层需要关闭响应缓冲或设置合理超时。
- 自动路由规则应先写在 `intent-rules.yaml`，只有规则无法表达时再改代码。
- 新功能尽量保留清晰入口：前端组件、服务封装、后端 controller、业务 service、配置文件各司其职。
- 聊天参数（temperature 等）自动持久化到 `localStorage`，无需额外处理。
- 会话 ID 在页面刷新后从 `localStorage` 恢复，自动加载上次对话。
- 错误提示统一使用 `useToast` 的 `fromError` 方法，自动分类网络/超时/权限等错误。
