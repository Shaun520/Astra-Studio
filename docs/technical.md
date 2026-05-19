# Astra Studio 技术文档

> 本文档随项目迭代持续更新。每新增一个功能模块，请在对应章节补充说明；新增接口时更新「接口协议」章节；新增可配置项时更新「配置参考」章节。

---

## 目录

1. [架构总览](#1-架构总览)
2. [前端](#2-前端)
   - 2.1 [技术栈](#21-技术栈)
   - 2.2 [目录结构](#22-目录结构)
   - 2.3 [状态与通信模型](#23-状态与通信模型)
   - 2.4 [组件说明](#24-组件说明)
   - 2.5 [服务层](#25-服务层)
   - 2.6 [Markdown 渲染](#26-markdown-渲染)
   - 2.7 [类型定义](#27-类型定义)
3. [后端](#3-后端)
   - 3.1 [技术栈](#31-技术栈)
   - 3.2 [目录结构](#32-目录结构)
   - 3.3 [AI 服务层](#33-ai-服务层)
   - 3.4 [自动路由模块](#34-自动路由模块)
   - 3.5 [输入护轨](#35-输入护轨)
   - 3.6 [SSE 流式实现](#36-sse-流式实现)
   - 3.7 [MCP 联网搜索](#37-mcp-联网搜索)
   - 3.8 [对话持久化](#38-对话持久化)
   - 3.9 [知识库 RAG](#39-知识库-rag)
4. [前后端交互协议](#4-前后端交互协议)
   - 4.1 [SSE 数据格式](#41-sse-数据格式)
   - 4.2 [接口列表](#42-接口列表)
5. [配置参考](#5-配置参考)
   - 5.1 [后端配置文件](#51-后端配置文件)
   - 5.2 [前端环境变量](#52-前端环境变量)
   - 5.3 [意图路由规则](#53-意图路由规则)
6. [扩展指南](#6-扩展指南)
   - 6.1 [新增前端页面/面板](#61-新增前端页面面板)
   - 6.2 [新增后端接口](#62-新增后端接口)
   - 6.3 [新增模型](#63-新增模型)
   - 6.4 [新增 MCP 工具](#64-新增-mcp-工具)
   - 6.5 [新增意图路由规则](#65-新增意图路由规则)
   - 6.6 [新增敏感词过滤](#66-新增敏感词过滤)

---

## 1 架构总览

```
┌─────────────────────────────────────────┐
│            浏览器（用户）                │
│   Vue 3 + Vite + Tailwind CSS           │
│                                         │
│  ┌──────────┐  ┌──────────────────────┐ │
│  │ Composer │  │   ChatMessage        │ │
│  │ (输入区) │  │ (Markdown + Thinking)│ │
│  └────┬─────┘  └──────────┬───────────┘ │
│       │  SSE + multipart  │             │
└───────┼───────────────────┼─────────────┘
        │ POST /api/ai/chat │ SSE stream
        ▼                   ▲
┌─────────────────────────────────────────┐
│         Spring Boot 3 (Java 21)         │
│                                         │
│  AiController → AiCodeHelperService     │
│       ↓                 ↓               │
│  AutoRoutingService  LangChain4j        │
│  IntentClassifier    TokenStream        │
│  ModelRouter         McpToolProvider    │
│                      ContentRetriever   │ ← RAG 知识库检索
│  KnowledgeService ← DocumentETLPipeline │ ← 文档管理 + ETL
│       ↓                 ↓               │
│  RAGRetrievalService → PostgreSQL       │ ← 向量搜索
│                                         │
└─────────┬───────────────────────────────┘
          │  OpenAI-compatible HTTP API
          ▼
  ┌──────────────────────┐
  │   大语言模型服务      │
  │  DashScope / BigModel │
  └──────────────────────┘
```

| 层次 | 职责 |
|------|------|
| 前端 | 交互、文件上传（直传 OSS）、SSE 接收与渲染 |
| 后端 | 模型调用、会话记忆、意图路由、联网搜索、流式推送 |
| 模型层 | 实际推理。通过 OpenAI-compatible API 对接 DashScope/BigModel |

---

## 2 前端

### 2.1 技术栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5 | 渐进式框架 |
| Vite | 8.x | 构建与开发服务 |
| TypeScript | 6.0 | 类型检查 |
| Tailwind CSS | 4.2 | 原子化样式 |
| highlight.js | 11.x | 代码语法高亮 |
| lucide-vue-next | 1.0 | 图标 |

包管理器为 **pnpm**，锁文件 `pnpm-lock.yaml` 已纳入版本控制。

### 2.2 目录结构

```
Astra-Studio/
├── src/
│   ├── App.vue              # 根组件，持有全局状态和会话逻辑
│   ├── main.ts              # 应用入口
│   ├── style.css            # 全局样式 & CSS 变量
│   ├── components/
│   │   ├── AppSidebar.vue   # 左侧导航栏
│   │   ├── MainHeader.vue   # 顶部 header（含模型选择）
│   │   ├── Composer.vue     # 输入区（文本、附件、功能开关）
│   │   ├── ChatMessage.vue  # 消息气泡（含 Markdown 渲染）
│   │   ├── AttachCard.vue   # 附件卡片展示
│   │   ├── ImagePreview.vue # 图片全屏预览
│   │   ├── StudioPanel.vue  # 创作工作台面板（占位）
│   │   ├── TweaksPanel.vue  # 参数调节面板
│   │   ├── ImageGenerator.vue  # 图像生成（占位）
│   │   └── VideoGenerator.vue  # 视频生成（占位，BETA）
│   ├── services/
│   │   ├── api.ts           # 后端 HTTP/SSE 通信
│   │   └── oss.ts           # 阿里云 OSS 直传
│   └── types/
│       └── index.ts         # 全局类型定义
├── .env.example             # 环境变量示例
├── vite.config.ts
└── package.json
```

### 2.3 状态与通信模型

前端不使用独立的状态管理库，全局状态通过 Vue 的 `provide / inject` 从 `App.vue` 向下传递。

#### 当前 provide 的值

| key | 类型 | 说明 |
|-----|------|------|
| `selectedModel` | `Ref<string>` | 当前选中的模型名称，默认 `'auto'` |
| `startNewSession` | `() => void` | 清空会话历史、生成新 sessionId |
| `openImagePreview` | `(images, index) => void` | 打开图片全屏预览 |

#### 会话 ID（memoryId）

由 `App.vue` 在组件初始化和「新对话」时通过以下方式生成：

```ts
const id = `session_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`
```

每次对话请求都将 `currentSessionId` 作为 `memoryId` 传给后端，后端以此维持多轮记忆。

#### 文件上传流程

```
用户选择/拖入/粘贴文件
       ↓
Composer 收集 PendingAttachment（含本地预览 URL）
       ↓
handleSend 触发 → uploadFiles()（直传 OSS）
       ↓
拿到公开 OSS URL → 加入 sendChatMessage 的 files 参数
       ↓
后端拿到 URL 列表，拼接为多模态消息发给模型
```

### 2.4 组件说明

#### AppSidebar

左侧导航栏，包含品牌标识、工作空间切换、「创作」导航（对话、图像生成、视频生成、代码助手、翻译润色）、资料库（最近、收藏、项目）和底部用户卡片。

导航事件通过 `emit('navigate', label)` 冒泡至 `App.vue`，`App.vue` 根据 label 切换 `activeView` 来控制主内容区的视图。

新增导航项时，在 `toolItems` 数组里追加一条即可，`badge` 字段可选（用于展示「BETA」等标签）。

#### Composer

输入区，核心职责：

- 自动伸缩的 `<textarea>`，最大高度 160 px
- 支持 `Enter` 发送、`Shift+Enter` 换行
- 支持拖拽、粘贴（含截图）添加文件
- 「深度思考」和「联网」开关，激活后发送时携带对应参数
- 附件预览区，图片点击可全屏，文件显示类型和大小
- 发送中展示「停止」按钮，点击触发 `AbortController.abort()`

#### ChatMessage

消息气泡，根据 `role` 决定左右布局。

- `role === 'user'`：右对齐，高亮气泡背景
- `role === 'assistant'`：左对齐，带 avatar

内部包含：

- 骨架加载动画（三个弹跳气泡，仅在 `isLoading && 无任何内容` 时显示）
- 思维链折叠区（`hasThinking` 为真时出现绿色渐变 header，可展开/折叠）
- Markdown 渲染主体（`renderedContent`）
- 光标闪烁动画（流式接收中）
- 附件卡片列表（`AttachCard` 组件）

#### ImagePreview

全屏图片预览，支持左右切换、ESC 关闭。通过 `provide('openImagePreview', fn)` 注入到子组件，不依赖全局状态库。

### 2.5 服务层

#### api.ts

与后端对话接口通信，核心函数：

```ts
sendChatMessage(options: SendChatMessageOptions, callbacks?, signal?): Promise<void>
```

`SendChatMessageOptions` 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `memoryId` | `string` | 会话 ID |
| `text` | `string` | 用户文本 |
| `files` | `string[]` | OSS 文件 URL 列表 |
| `deepThink` | `boolean` | 是否启用深度思考 |
| `webSearch` | `boolean` | 是否启用联网搜索 |
| `model` | `string` | 模型名称，`'auto'` 为自动路由 |

回调接口 `ChatStreamCallbacks`：

| 回调 | 触发时机 |
|------|---------|
| `onThinking(content)` | 收到 thinking 类型 chunk |
| `onMessage(content)` | 收到 text 类型 chunk |
| `onComplete()` | 流结束（done） |
| `onError(error)` | 发生错误 |

内部实现：通过 `fetch` 发送 `multipart/form-data` 请求，使用 `ReadableStream` 逐行解析 SSE，不依赖额外的 SSE 库。

当前 API 基础地址硬编码在文件顶部：

```ts
const API_BASE_URL = 'http://localhost:8089/api'
```

后续如需多环境支持，改为读取 `import.meta.env.VITE_API_BASE_URL`。

#### oss.ts

阿里云 OSS 直传，不经过后端。核心函数：

| 函数 | 说明 |
|------|------|
| `uploadToOSS(file, onProgress?)` | 上传单个文件，返回 `UploadResult` |
| `uploadFiles(files, onProgress?)` | 批量顺序上传 |
| `uploadFileWithPreview(file, previewUrl?)` | 上传并携带本地预览 URL |

签名流程：在浏览器端用 `WebCrypto` 对 Policy 做 HMAC-SHA1 签名（临时实现，生产环境建议改为后端签名以避免密钥泄露）。

所有配置从 `import.meta.env` 读取，对应 `.env` 文件中的 `VITE_OSS_*` 变量。

### 2.6 Markdown 渲染

`ChatMessage.vue` 内置了轻量级 Markdown 渲染器，不引入 `marked` 或 `markdown-it`，以精确控制流式场景下的增量渲染行为。

渲染流水线（`mdToHtml` 函数）：

```
原始文本
  → renderCodeBlocks    代码块（支持流式不完整块）
  → renderInlineCode    行内代码
  → renderTables        表格
  → renderHeaders       标题 h1-h6
  → renderBold          加粗
  → renderItalic        斜体
  → renderStrikethrough 删除线
  → renderLinks         链接（Markdown 格式 + 裸 URL）
  → renderBlockquotes   引用块
  → renderLists         有序/无序列表
  → renderHorizontalRule 分割线
  → splitParagraphs     包裹 <p> 段落
  → removeDuplicateContent 去重（防止流式重复）
```

代码块实现要点：

- 完整代码块：正则 ` ```lang\n...``` `
- **不完整代码块**：额外匹配 ` ```lang\n...$ ` 并同样渲染，保证流式过程中已收到的代码即时着色
- 语法高亮：使用 `highlight.js`，已注册 JS、TS、Python、C、C++、Java、CSS、HTML、JSON、Bash、SQL、YAML、Markdown
- 代码块 header 提供「复制」按钮，通过 `navigator.clipboard` 写入剪贴板

思维链有独立的渲染器 `renderThinkingMarkdown`，样式与正文分离（绿色系配色）。

### 2.7 类型定义

`src/types/index.ts` 集中定义全局类型：

| 类型 | 说明 |
|------|------|
| `Message` | 一条消息（role、author、time、content、attachments） |
| `Attachment` | 附件（image / video / audio / code / file） |
| `ModelInfo` | 模型信息（name、tags） |
| `ParamConfig` | 参数滑块配置（name、value、min、max、step） |
| `ThemeConfig` | 主题配置（mode、accent、density） |
| `NavItem` / `LibraryItem` | 侧栏导航项 |
| `ToolChip` | 工具按钮 |
| `RecentAsset` | 最近资产列表项 |

---

## 3 后端

### 3.1 技术栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5 | Web 框架 |
| Java | 21 | 运行时（需要 Record、Pattern Matching 等特性） |
| LangChain4j | 1.3.0 | AI 服务抽象 |
| langchain4j-open-ai-spring-boot-starter | 1.3.0 | OpenAI 兼容模型自动配置 |
| langchain4j-reactor | 1.1.0-beta7 | Flux 流式支持（按需引入） |
| langchain4j-mcp | 1.1.0-beta7 | MCP 工具协议 |
| Lombok | - | 减少样板代码 |
| SnakeYAML | Spring Boot 内置 | 解析 intent-rules.yaml |

构建工具：**Maven Wrapper**（`mvnw.cmd`），无需本地安装 Maven。

### 3.2 目录结构

```
Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/
├── AstraStudioOpenAiApplication.java   # 启动入口
├── ai/
│   ├── AiCodeHelperService.java        # LangChain4j AI Service 接口
│   ├── AiCodeHelperServiceFactory.java # 服务工厂（含实例缓存）
│   ├── guardrail/
│   │   └── SafeInputGuardrail.java     # 输入安全护轨
│   └── mcp/
│       └── McpConfig.java              # MCP 客户端配置
├── config/
│   └── CorsConfig.java                 # 全局跨域配置
├── controller/
│   └── AiController.java               # HTTP 接口（/ai/chat、/ai/routing-stats）
├── dto/
│   └── AiStreamChunk.java              # SSE 数据块结构
├── routing/
│   ├── AutoRoutingService.java         # 自动路由服务
│   ├── AutoRouteResult.java            # 路由结果（Record）
│   ├── ClassificationResult.java       # 分类结果（Record）
│   ├── IntentClassifier.java           # 意图分类器
│   ├── ModelRouter.java                # 模型选择器
│   ├── RoutingDecision.java            # 路由决策（Record）
│   └── RoutingStatsService.java        # 路由统计
└── utils/
    └── MultipartUserMessageBuilder.java # 多模态消息构建器

src/main/resources/
├── application.yaml
├── application-local.yaml   # 本地密钥（.gitignore 排除）
└── intent-rules.yaml        # 意图路由规则
```

### 3.3 AI 服务层

#### AiCodeHelperService 接口

```java
@InputGuardrails({ SafeInputGuardrail.class })
public interface AiCodeHelperService {
    ChatResponse chat(UserMessage message);                         // 同步
    TokenStream chatWithStream(@MemoryId String memoryId,          // 流式
                               @UserMessage String message);
}
```

- `@InputGuardrails`：请求在到达模型前先经过护轨检测
- `@MemoryId`：LangChain4j 自动从 `MessageWindowChatMemory` 中取出该 ID 对应的历史记录（窗口大小：最近 10 条）
- `TokenStream`：LangChain4j 原生流式类型，不需要 Reactor 依赖；订阅 `onPartialThinking`、`onPartialResponse`、`onCompleteResponse`、`onError` 四个回调

#### AiCodeHelperServiceFactory

服务工厂，管理所有 `AiCodeHelperService` 实例。

```
getService(deepThink, webSearch, modelName) → AiCodeHelperService
```

缓存策略：以 `"deepThink:X,webSearch:X,model:Y"` 为键存入 `ConcurrentHashMap`，相同配置复用已有实例。

模型白名单（需要手动维护）：

```java
private static final Set<String> ALLOWED_MODELS = Set.of(
    "glm-5",
    "deepseek-v4-flash",
    "qwen3.6-flash-2026-04-16"
);
```

超时时间动态计算规则：

| 配置组合 | 超时（秒） |
|---------|-----------|
| 基础 | 30 |
| 深度思考 | +30 |
| 联网搜索 | +15 |
| 深度思考 + 联网 | +45 |

#### MultipartUserMessageBuilder

将文本和文件 URL 列表组装为 LangChain4j 的 `UserMessage`，支持纯文本和多模态（图文混合）两种格式。

### 3.4 自动路由模块

整体流程：

```
用户请求 model=auto
       ↓
AutoRoutingService.autoRoute(text)
       ↓
IntentClassifier.classify(text)
       ↓
  ┌─ 置信度 ≥ 0.6 ─── ModelRouter.route(classification)
  │                         ↓
  │                   RoutingDecision（selectedModel, reason）
  │
  └─ 置信度 < 0.6 ─── 兜底使用 default-model（glm-5）
```

#### IntentClassifier

从 `intent-rules.yaml` 加载规则，启动时编译正则表达式，对每条意图进行关键词组匹配和正则匹配，取最大分后乘以 `weight` 得到最终置信度。

关键词组匹配逻辑：一个意图可配置多个「关键词组」，每组内任意一个词命中即计为该组匹配，最终分数 = 命中组数 / 总组数。

#### ModelRouter

根据 `ClassificationResult.intent()` 在规则映射中找到对应的 `target_model`，生成 `RoutingDecision`。

#### RoutingStatsService

内存中统计每次路由的意图类型、模型选择和置信度分布，通过 `GET /api/ai/routing-stats` 接口暴露。

### 3.5 输入护轨

`SafeInputGuardrail` 在消息进入 LangChain4j 调用链之前拦截，对文本内容做敏感词检测（当前使用简单的词表匹配）。

```java
private static final Set<String> sensitiveWords = Set.of("kill", "evil");
```

检测命中时返回 `fatal(reason)`，LangChain4j 会直接中止调用并抛出异常，控制器捕获后通过 SSE 推送错误事件。

扩展方式：在 `sensitiveWords` 中追加词汇，或引入更完整的词库、正则规则，或替换为基于模型的内容审核。

### 3.6 SSE 流式实现

控制器使用 `SseEmitter`（Spring MVC 内置），不依赖 WebFlux。

关键设计决策：

- **异步线程**：SSE 响应体在 `ExecutorService.newCachedThreadPool()` 的线程中写入，主线程立即返回 `SseEmitter` 对象，不阻塞 Tomcat 线程池
- **连接状态**：用 `boolean[] connectionClosed = {false}` 作为共享标志，在 `onCompletion`、`onTimeout`、`onError` 回调中置为 `true`
- **客户端断开检测**：检查 `IllegalStateException("already completed")`、`AsyncRequestNotUsableException`、`Connection reset / Broken pipe` 等典型异常，避免误日志
- **超时**：`SseEmitter` 超时设置为 60 秒，动态超时由模型请求层控制

SSE 事件类型：

| 事件名 | data 内容 | 触发时机 |
|--------|-----------|---------|
| `message` | `AiStreamChunk` JSON | 每个 thinking/text 片段，以及 routing_info |
| `complete` | `{"status":"done"}` | 流式输出全部完成 |
| `error` | `{"error":"..."}` | 发生错误 |

### 3.7 MCP 联网搜索

通过 LangChain4j MCP 模块接入智谱 BigModel 的联网搜索能力。

```java
// McpConfig.java
McpTransport transport = new HttpMcpTransport.Builder()
    .sseUrl("https://open.bigmodel.cn/api/mcp/web_search/sse?Authorization=" + apiKey)
    .build();
McpClient mcpClient = new DefaultMcpClient.Builder().key("shaunMcpClient").transport(transport).build();
McpToolProvider mcpToolProvider = McpToolProvider.builder().mcpClients(mcpClient).build();
```

`McpToolProvider` 作为 Spring Bean 注入，在 `AiCodeHelperServiceFactory.buildService` 中当 `webSearch=true` 时挂载到 AI 服务实例：

```java
if (webSearch) {
    builder.toolProvider(mcpToolProvider);
}
```

模型通过函数调用触发搜索，结果自动注入上下文，对前端透明。

### 3.8 对话持久化

对话持久化模块实现会话历史的多级存储与恢复能力，确保用户刷新页面或服务重启后可恢复上下文。

#### 架构

```
用户发送消息
       ↓
ConversationPersistenceService.saveContext()
       ↓
  ┌─ Level 1: Redis（热缓存）── Kryo 序列化 ── TTL=2h
  │         ↓ miss
  │    Level 2: PostgreSQL（冷存储）── conversation_snapshots 表
  │
恢复流程：restoreOrCreate(memoryId)
       ↓
  L1 Redis hit → 反序列化 → ConversationContext ✅
       ↓ miss
  L2 PostgreSQL hit → 加载快照 + 消息详情 → ConversationContext ✅
       ↓ miss
  空 ConversationContext（新会话）→ 返回 recovered=false
```

#### 核心类

| 类 | 职责 |
|----|------|
| `ConversationPersistenceService` | 双写协调器：saveContext / loadContext / restoreOrCreate |
| `ConversationCacheService` | Redis 操作层：序列化/反序列化/TTL管理 |
| `ConversationQueryService` | 查询层：提供 REST 接口查询会话状态 |
| `KryoSerializer` | Kryo 序列化工具（带 checksum 校验） |
| `ConversationContext` | 会话上下文 POJO（messages, version, modelName） |

#### 关键代码

```java
public void saveContext(String memoryId, List<MessageEntry> messages) {
    ConversationContext ctx = new ConversationContext(memoryId, messages, modelName);
    byte[] serialized = kryoSerializer.serialize(ctx);
    cacheService.saveToRedis(memoryId, serialized);      // L1 热缓存
    snapshotRepo.saveToPostgres(memoryId, ctx);           // L2 冷存储
}
```

### 3.9 知识库 RAG

知识库 RAG（Retrieval-Augmented Generation）系统实现文档导入、向量化存储和语义检索增强生成。

#### ETL 管道

```
文档上传 (URL)
       ↓
DocumentETLPipeline.processDocument()
       ↓
  1. 文档解析（PDF/Word/TXT/Markdown）
  2. 分块（chunk_size=512, overlap=64）
  3. 向量化（EmbeddingModel embed）
  4. 入库（document_chunks 表，pgvector 存储）
```

#### 检索流程

```
用户提问 + knowledgeBase=true
       ↓
RAGRetrievalService.retrieve(query)
       ↓
  EmbeddingModel.embed(query) → query vector
       ↓
  DocumentChunkRepository.findSimilarChunks(vector, topK=5)
       ↓
  过滤 similarityThreshold ≥ 0.75 → RetrievedChunk[]
       ↓
  ContentRetriever.retrieve() → 注入 AiServices 上下文
```

#### Retriever 集成方式

通过 `ContentRetrieverConfig` 将 `RAGRetrievalService` 包装为 LangChain4j `ContentRetriever` Bean：

```java
@Bean
public ContentRetriever ragContentRetriever() {
    return query -> ragRetrievalService.retrieve(query.text())
        .stream().map(RetrievedChunk::toTextContent).toList();
}
```

在 `AiCodeHelperServiceFactory` 中按需挂载：

```java
if (knowledgeBase && ragContentRetriever != null) {
    builder.contentRetriever(ragContentRetriever);
}
```

#### 核心类

| 类 | 职责 |
|----|------|
| `DocumentETLPipeline` | 文档 ETL 管道（解析→分块→向量→入库） |
| `RAGRetrievalService` | 语义检索服务（embedding + pgvector 相似度搜索） |
| `KnowledgeService` | 知识库 REST 服务层（上传/搜索/列表） |
| `ContentRetrieverConfig` | LangChain4j ContentRetriever Bean 配置 |
| `EmbeddingConfig` | EmbeddingModel 配置 |

---

## 4 前后端交互协议

### 4.1 SSE 数据格式

所有 SSE 消息（`message` 事件）的 `data` 字段均为 `AiStreamChunk` 的 JSON 序列化结果。

```json
// thinking 类型
{
  "type": "thinking",
  "content": "让我先分析一下这个问题..."
}

// text 类型
{
  "type": "text",
  "content": "根据分析，答案是..."
}

// routing_info 类型（model=auto 且成功路由时推送，在第一个 thinking/text 之前）
{
  "type": "routing_info",
  "usage": {
    "model": "deepseek-v4-flash",
    "savingsRate": 1.0,
    "confidence": 0.81
  }
}
```

`complete` 事件：

```
event: complete
data: {"status":"done"}
```

`error` 事件：

```
event: error
data: {"error":"Sensitive word detected: kill"}
```

### 4.2 接口列表

#### POST /api/ai/chat

流式对话接口。

- Content-Type：`multipart/form-data`
- Accept：`text/event-stream`

请求参数（form fields）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `memoryId` | string | 是 | 会话 ID，后端用于查找对话历史 |
| `text` | string | 否 | 用户输入文本 |
| `files` | string（多值） | 否 | 文件的 OSS 公开访问 URL，可传多个 |
| `deepThink` | boolean | 否 | 默认 `false` |
| `webSearch` | boolean | 否 | 默认 `false` |
| `knowledgeBase` | boolean | 否 | 默认 `false`；启用知识库 RAG 检索 |
| `model` | string | 否 | 默认 `glm-5`；传 `auto` 启用自动路由 |

响应：SSE 流，格式见 [4.1](#41-sse-数据格式)。

新增 SSE 事件类型：

```
event: message
data: {"type":"sources","sources":[{"chunk_id":1,"content_snippet":"...","document_name":"doc.pdf","page_number":1,"score":0.92}]}
```

当前允许的模型值：`glm-5`、`deepseek-v4-flash`、`qwen3.6-flash-2026-04-16`、`auto`。

#### POST /api/ai/knowledge/upload

上传文档到知识库（同步 ETL 处理）。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `fileUrl` | string | 是 | 文件 OSS URL |
| `fileName` | string | 是 | 原始文件名 |

返回：`{ documentId, status, chunkCount }`。

支持格式：PDF、DOCX、TXT。ETL 完成后 status 变为 `READY`。

#### GET /api/ai/knowledge/documents

分页查询知识库文档列表。

| 参数 | 类型 | 说明 |
|------|------|------|
| `page` | int | 页码（从 0 开始） |
| `size` | int | 每页大小 |

返回：`{ content: [], totalElements, totalPages, ... }`，按创建时间倒序。

#### GET /api/ai/knowledge/documents/{id}

查询单个文档详细状态。返回：`{ id, filename, status, chunkCount, errorMessage, createdAt }`。

#### DELETE /api/ai/knowledge/documents/{id}

删除文档及其所有分块向量数据（数据库外键级联删除）。返回：`204 No Content`。

#### POST /api/ai/knowledge/search?query=xxx

手动搜索知识库（测试用）。返回：`List<RetrievedChunk>`，包含 chunk 内容和来源文档名。

#### GET /api/ai/conversations/{memoryId}/restore

恢复或创建会话上下文。返回 JSON：`{ memoryId, messages, version, recovered }`。

#### POST /api/ai/knowledge/import

导入文档到知识库（multipart/form-data）。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `fileUrl` | string | 是 | 文件 URL |
| `fileName` | string | 否 | 文件名 |

返回：`{ documentId, status: "PROCESSING" }`。

#### GET /api/ai/knowledge/documents

查询知识库文档列表和状态，返回 JSON 数组。

#### GET /api/ai/routing-stats

查询自动路由统计信息，返回 JSON。

---

## 5 配置参考

### 5.1 后端配置文件

**application.yaml**（公共配置，提交到仓库）

```yaml
spring:
  application:
    name: Astra-Studio-Open-Ai
  profiles:
    active: local        # 激活 local profile，从 application-local.yaml 读取密钥

server:
  port: 8089
  servlet:
    context-path: /api

langchain4j:
  open-ai:
    chat-model:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: deepseek-v4-flash
      api-key: ${custom.api-keys.dashscope}
      return-thinking: true
      timeout: 30s
      max-retries: 2
    streaming-chat-model:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: deepseek-v4-flash
      api-key: ${custom.api-keys.dashscope}
      timeout: 30s

auto-routing:
  enabled: true
  confidence-threshold: 0.6   # 低于此值使用 default-model
  default-model: glm-5
  rules-file: classpath:intent-rules.yaml

spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ""
      database: 0
  datasource:
    url: jdbc:postgresql://localhost:5432/astra_studio
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  sql:
    init:
      mode: always

conversation-persistence:
  redis-ttl-hours: 2
  kryo-checksum-enabled: true

knowledge-base:
  rag:
    enabled: true
    embedding-model: text-embedding-v3
    embedding-dimensions: 1024
    chunk-size: 512
    chunk-overlap: 64
    batch-size: 10
    top-k: 5
    similarity-threshold: 0.5
    retrieval-timeout-ms: 3000
    cache-ttl-seconds: 30

sse:
  timeout-ms: 300000
```

**application-local.yaml**（本地密钥，不提交）

```yaml
custom:
  api-keys:
    dashscope: sk-xxxx         # 阿里云 DashScope API Key
    bigmodel: xxxx.xxxx        # 智谱 BigModel API Key（联网搜索用）
```

### 5.2 前端环境变量

复制 `.env.example` 为 `.env`，不提交 `.env` 文件。

| 变量 | 说明 |
|------|------|
| `VITE_OSS_ACCESS_KEY_ID` | OSS AccessKey ID |
| `VITE_OSS_ACCESS_KEY_SECRET` | OSS AccessKey Secret |
| `VITE_OSS_BUCKET` | Bucket 名称 |
| `VITE_OSS_REGION` | 区域，如 `oss-cn-hangzhou` |
| `VITE_OSS_ENDPOINT` | 端点 URL，如 `https://oss-cn-hangzhou.aliyuncs.com` |
| `VITE_OSS_CUSTOM_DOMAIN` | 可选，自定义域名/CDN 域名 |

### 5.3 意图路由规则

`intent-rules.yaml` 结构：

```yaml
intents:
  INTENT_NAME:
    keywords:
      - ["词A", "词B", "词C"]    # 关键词组，每组内任一词命中计为该组匹配
      - ["词D", "词E"]
    patterns:
      - "正则表达式1"              # 整句匹配（Pattern.matches）
      - "正则表达式2"
    weight: 0.9                   # 最终得分乘以此权重，1.0 为最高
    target_model: "model-name"    # 命中时选用的模型
    description: "说明文字"
```

当前三个意图：

| 意图 | 描述 | 目标模型 |
|------|------|---------|
| `CODE_GENERATION` | 代码生成、算法实现 | `deepseek-v4-flash` |
| `CHINESE_QA` | 中文理解、知识问答 | `qwen3.6-flash-2026-04-16` |
| `GENERAL_CHAT` | 通用兜底 | `glm-5` |

---

## 6 扩展指南

### 6.1 新增前端页面/面板

1. 在 `src/components/` 下新建 `XXX.vue`。
2. 在 `AppSidebar.vue` 的 `toolItems` 数组追加入口：

   ```ts
   { icon: YourIcon, label: '新功能', badge: 'NEW' }
   ```

3. 在 `App.vue` 根据 `activeView` 值条件渲染新组件：

   ```html
   <NewFeature v-if="activeView === '新功能'" />
   ```

4. 如果新组件需要调用后端，在 `src/services/` 新建对应服务文件，保持与 `api.ts` 同样的封装风格。

5. 新增共享类型时统一放到 `src/types/index.ts`。

### 6.2 新增后端接口

1. 在 `controller/` 新增方法或新建控制器文件。
2. 请求/响应体放到 `dto/`，不直接暴露内部对象。
3. 业务逻辑放独立 Service，不写在控制器里。
4. 如果需要调用 AI 模型，通过 `AiCodeHelperServiceFactory.getService(...)` 获取实例，复用已有缓存。
5. 新接口遵循 `/api/模块/操作` 的路径约定。

### 6.3 新增模型

1. 确认新模型的 base-url 和 API Key。
2. 在 `AiCodeHelperServiceFactory` 的 `ALLOWED_MODELS` 中加入模型名称：

   ```java
   private static final Set<String> ALLOWED_MODELS = Set.of(
       "glm-5",
       "deepseek-v4-flash",
       "qwen3.6-flash-2026-04-16",
       "new-model-name"           // 新增
   );
   ```

3. 如果新模型使用不同的 base-url 或 API Key，在 `createModel` 方法中增加分支判断。

4. 若希望自动路由能选中该模型，在 `intent-rules.yaml` 中对应意图的 `target_model` 填写新模型名，或新增一个意图类型（见 [6.5](#65-新增意图路由规则)）。

5. 在 `MainHeader.vue` 或对应的模型选择组件中补充前端展示项。

6. 本地验证手动指定和 `auto` 两种模式均正常。

### 6.4 新增 MCP 工具

1. 在 `ai/mcp/` 下新建工具配置类（或在 `McpConfig.java` 中追加 Bean）：

   ```java
   @Bean
   public McpToolProvider newToolProvider() {
       McpTransport transport = new HttpMcpTransport.Builder()
           .sseUrl("https://...")
           .build();
       McpClient client = new DefaultMcpClient.Builder()
           .key("newToolClient")
           .transport(transport)
           .build();
       return McpToolProvider.builder().mcpClients(client).build();
   }
   ```

2. 在 `AiCodeHelperServiceFactory.buildService` 中增加对应的功能开关参数和条件：

   ```java
   if (newFeature) {
       builder.toolProvider(newToolProvider);
   }
   ```

3. 在 `AiCodeHelperService` 接口、`AiController`、`SendChatMessageOptions`（前端）分别添加对应的参数。

4. 工具调用会消耗额外时间，记得在 `calculateTimeout` 中增加对应的时间补偿。

### 6.5 新增意图路由规则

在 `intent-rules.yaml` 中追加一个新意图块：

```yaml
intents:
  # 已有意图...
  
  NEW_INTENT:
    keywords:
      - ["关键词1", "关键词2"]
      - ["关键词3"]
    patterns:
      - "触发这个意图的正则.*"
    weight: 0.85
    target_model: "target-model-name"
    description: "意图描述"
```

注意事项：
- `weight` 值影响不同意图之间的优先级，相同置信度原始分时高 `weight` 的意图胜出
- `patterns` 使用 Java `Pattern.matches()`，即全字符串匹配（等价于 `^pattern$`）
- 如果需要让分类器重新加载规则而不重启服务，可以将 `auto-routing.rules-hot-reload` 设为 `true`（当前未实现，保留配置项）

同时需要在 `AiController.extractIntentFromReason` 中增加对新意图名称的识别，以保证统计日志的准确性。

### 6.6 新增敏感词过滤

在 `SafeInputGuardrail.java` 的 `sensitiveWords` 中追加词汇，或替换为更完善的过滤方案：

```java
// 简单词表方式
private static final Set<String> sensitiveWords = Set.of("kill", "evil", "newword");

// 或者引入词库文件
private Set<String> sensitiveWords = loadWordsFromResource("sensitive-words.txt");
```

---

## 3.9 知识库 RAG

知识库模块实现文档检索增强生成（RAG）能力，允许用户上传 PDF/DOCX/TXT 文档后，AI 在回答时自动检索相关知识库内容并引用回答。

### 3.9.1 架构概览

```
用户上传文档 → ETL Pipeline（解析/分块/向量化）→ PostgreSQL + Pgvector
                                                              ↓
用户提问（开启知识库）→ Embedding 查询 → 向量相似度搜索 → Top-K chunks
                                                              ↓
                                              Chunks 注入 LLM 上下文 → 带引用的回答 + Sources 推送
```

### 3.9.2 核心组件

| 组件 | 类名 | 职责 |
|------|------|------|
| ETL 管道 | `DocumentETLPipeline` | 文件解析→文本分块→向量化→存储 |
| 向量检索 | `RAGRetrievalService` | 查询向量化、Pgvector 相似度搜索、结果过滤与缓存 |
| 内容检索器 | `ContentRetrieverConfig` | LangChain4j 集成，将检索结果注入 AI 上下文 |
| 知识库服务 | `KnowledgeService` | 文档 CRUD 管理（上传/列表/删除/状态查询） |
| 数据实体 | `KnowledgeDocumentEntity` / `DocumentChunkEntity` | JPA 实体映射 |

### 3.9.3 ETL 流程

**同步执行**（非异步），确保嵌入完成后才标记 READY：

```
POST /knowledge/upload {fileUrl, fileName}
    ↓
创建 KnowledgeDocumentEntity (status=PROCESSING)
    ↓
parser.parseToText(fileUrl)          // 解析 PDF/DOCX/TXT 为纯文本
    ↓
chunker.chunk(text, fileName)        // 按 512 tokens 分块，重叠 64 tokens
    ↓
embedAndStore(doc, chunks):          // 同步嵌入+存储
    for each batch of 10:            // DashScope API 批次限制
      embeddings = model.embedAll(texts)  // text-embedding-v3, 1024 维
      saveChunk(doc, chunk, vector)   // 写入 DB（失败抛异常，不存 NULL）
    ↓
更新 status=READY, chunkCount=N
```

**关键参数**：
- 分块大小：`chunk-size=512` tokens，重叠 `chunk-overlap=64` tokens
- 批次大小：`batch-size=10`（DashScope API 限制）
- 向量维度：`embedding-dimensions=1024`（text-embedding-v3 固定值）

### 3.9.4 向量检索流程

```java
retrieve(query):
  1. 缓存检查（TTL=30s，同一查询复用结果）
  2. queryEmbedding = embed(query)            // 1024 维向量
  3. rawResults = findSimilarChunks(
       queryVec, maxDist=0.5, topK=10)        // 余弦距离 <= 0.5
  4. if empty:
       fallback = findSimilarChunks(..., maxDist=1.0)  // 无阈值重试
  5. map to RetrievedChunk (id, content, documentName)
  6. return results
```

**Fallback 机制**：阈值过滤无结果时，用 `maxDist=1.0` 重试一次。

### 3.9.5 数据库设计

**knowledge_documents** — 文档元数据：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| filename | VARCHAR(255) | 原始文件名 |
| file_type | VARCHAR(20) | pdf/docx/txt |
| file_url | TEXT | OSS 地址 |
| status | VARCHAR(20) | PROCESSING/READY/FAILED |
| chunk_count | INTEGER | 分块数量 |
| error_message | TEXT | 失败原因 |

**document_chunks** — 文档分块（含向量）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| document_id | BIGINT FK | 关联文档（级联删除） |
| chunk_index | INTEGER | 分块序号 |
| content | TEXT | 分块文本 |
| **embedding** | **vector(1024)** | **浮点向量（Hibernate Vector 映射）** |
| token_count | INTEGER | Token 数量 |

**向量列映射**：
```java
@JdbcTypeCode(SqlTypes.VECTOR)
@Array(length = 1024)
@Column(name = "embedding", columnDefinition = "vector(1024)")
private float[] embedding;
```

### 3.9.6 SSE 事件扩展

知识库问答时，SSE 流新增 `sources` 事件：

```json
{
  "type": "sources",
  "sources": [
    {
      "chunkId": 5,
      "contentSnippet": "...",
      "documentName": "xxx.pdf"
    }
  ]
}
```

前端 `ChatMessage.vue` 在收到此事件时展示来源卡片。

### 3.9.7 配置参考

```yaml
knowledge-base:
  rag:
    enabled: true
    embedding-model: text-embedding-v3
    embedding-dimensions: 1024
    chunk-size: 512
    chunk-overlap: 64
    batch-size: 10
    top-k: 5
    similarity-threshold: 0.5
    retrieval-timeout-ms: 3000
    cache-ttl-seconds: 30
```

> 详细技术文档见 [rag-knowledge-base.md](./develop/rag-knowledge-base.md)。

如需基于模型的内容审核，可新增一个 `InputGuardrail` 实现，并在 `AiCodeHelperService` 的 `@InputGuardrails` 注解中追加。
