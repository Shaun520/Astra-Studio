## Why

当前 Astra-Studio-Open-Ai 系统仅具备单一的联网搜索 MCP 工具能力，无法满足用户在网页内容提取、文档生成、图片搜索等多样化场景下的需求。为了构建完整的 AI 助手生态系统，亟需引入多种 @Tool 工具集并实现统一的单例管理模式，同时开发独立的图片搜索 MCP 服务以支持跨项目集成。

## What Changes

- **新增 6 种 @Tool 工具集**：利用 langchain4j 的 `@Tool` 注解开发包含联网搜索（WebSearch）、网页抓取（WebScraper）、PDF 生成（PdfGenerator）、图片分析（ImageAnalyzer）、代码执行（CodeExecutor）、数据处理（DataProcessor）的完整工具生态
- **创建 ToolRegistry 单例管理器**：通过单例模式集中管理所有本地工具实例，提供统一的注册、查询和生命周期管理接口
- **开发图片搜索 MCP Server**：独立模块化设计，支持 Stdio（本地开发）和 SSE（Serverless 生产环境）双模式部署，提供图片搜索能力
- **改造 AiCodeHelperServiceFactory**：扩展现有工厂类以支持动态注入本地 @Tool 工具集，与现有的 MCP 工具提供商协同工作
- **集成到 ChatService**：在流式聊天接口中暴露工具调用能力，让 AI 能够自主选择和使用合适的工具完成任务

## Capabilities

### New Capabilities
- `tool-registry`: 单例模式的工具注册中心，负责统一管理和分发所有 @Tool 工具实例
- `web-tools`: 网络相关工具集，包括联网搜索和网页内容抓取功能
- `document-tools`: 文档处理工具集，包括 PDF 生成、格式转换等功能
- `image-search-mcp`: 图片搜索 MCP 服务端，支持多数据源（Unsplash/Pexels/自建索引）的图片检索
- `tool-integration`: 工具与现有 ChatService 和 AiCodeHelperServiceFactory 的深度集成方案

### Modified Capabilities
- `ai-service-factory`: 扩展 AiCodeHelperServiceFactory 以支持动态注入本地工具列表（非破坏性修改）

## Impact

**受影响的代码模块**：
- `service/tools/` - 新增工具集目录（6 个工具实现类 + ToolRegistry）
- `service/mcp/` - 新增图片搜索 MCP Server 模块
- `service/ai/AiCodeHelperServiceFactory.java` - 扩展 buildService() 方法
- `service/chat/ChatService.java` - 可选：增加工具状态反馈
- `config/McpConfig.java` - 扩展：新增图片搜索 MCP 配置 Bean

**API 变更**：
- 新增内部 API：`ToolRegistry.getInstance().getAllTools()` / `registerTool()` / `getToolInfo()`
- 新增 REST 端点（可选）：`GET /api/tools/available` - 查询可用工具列表

**新增依赖项**：
- Jsoup（网页解析）
- Apache PDFBox 或 iText（PDF 生成）
- HttpClient（网络请求）
- Unsplash/Pexels API Client（图片搜索）

**系统集成点**：
- 与现有 langchain4j 框架的 `@Tool` 注解体系完全兼容
- 复用现有 McpConfig 的 HttpMcpTransport 基础设施
- 不影响现有的 SSE 流式输出和会话持久化机制
