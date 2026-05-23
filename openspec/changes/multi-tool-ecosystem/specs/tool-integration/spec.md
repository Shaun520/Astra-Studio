## ADDED Requirements

### Requirement: AiCodeHelperServiceFactory 动态工具注入
系统 SHALL 扩展现有的 AiCodeHelperServiceFactory，支持将 ToolRegistry 中的本地工具动态注入到 AiServices 构建器中。

#### Scenario: 默认启用所有本地工具
- **WHEN** 调用 `AiCodeHelperServiceFactory.getService(deepThink, webSearch, modelName, knowledgeBase)`
- **AND** 未显式禁用工具功能
- **THEN** `buildService()` 方法内部执行：
  1. 调用 `ToolRegistry.getInstance().getAllTools()` 获取工具列表
  2. 如果列表非空，调用 `builder.tools(toolsArray)` 注入
  3. 继续执行现有的 MCP 工具注入逻辑（if webSearch）
- **AND** 最终构建的 AiCodeHelperService 实例同时具备本地工具和 MCP 工具能力

#### Scene: 显式禁用工具注入
- **WHEN** 配置 `tools.enabled: false`
- **THEN** `buildService()` 跳过 `builder.tools()` 调用
- **AND** 行为完全等同于变更前（向后兼容）

#### Scenario: 工具注入顺序保证
- **WHEN** 同时存在本地工具和 MCP 工具
- **THEN** 本地工具先注入（优先级高），MCP 工具后注入
- **AND** langchain4j 自动合并两者工具列表，AI 可自由选择任一工具

---

### Requirement: ChatService 工具调用状态反馈（可选增强）
系统 SHALL 在流式输出过程中可选地反馈工具调用的中间状态，提升用户体验。

#### Scenario: 工具调用开始通知
- **WHEN** AI 决定调用某个工具（如 WebScraperTool.scrape）
- **AND** 配置 `chat.tool-call-notification: true`
- **THEN** 通过 SSE 发送事件：
  ```json
  {
    "type": "tool_call_start",
    "toolName": "scrape_webpage",
    "params": {"url": "https://example.com"}
  }
  ```

#### Scenario: 工具调用完成通知
- **WHEN** 工具执行完毕并返回结果
- **THEN** 通过 SSE 发送事件：
  ```json
  {
    "type": "tool_call_end",
    "toolName": "scrape_webpage",
    "duration_ms": 1234,
    "success": true
  }
  ```

#### Scenario: 工具调用错误通知
- **WHEN** 工具执行过程中抛出异常
- **THEN** 通过 SSE 发送事件：
  ```json
  {
    "type": "tool_call_error",
    "toolName": "scrape_webpage",
    "error": "Connection timeout after 10s"
  }
  ```
- **AND** AI 继续生成回复（可能建议替代方案）

#### Scenario: 禁用通知模式（默认）
- **WHEN** 未配置 `chat.tool-call-notification` 或设置为 false
- **THEN** 不发送任何工具相关事件
- **AND** 仅输出最终的文本内容（减少网络开销）

---

### Requirement: McpConfig 多源扩展
系统 SHALL 扩展现有 McpConfig 类，新增图片搜索 MCP 工具提供商的 Bean 定义。

#### Scenario: 条件性创建图片搜索 MCP Provider
- **WHEN** 配置文件中存在 `image-search.mcp.enabled: true`
- **AND** 配置了 `image-search.mcp.sse-url` 或 stdio command
- **THEN** McpConfig 创建 `imageSearchMcpProvider()` Bean
- **AND** 类型为 `McpToolProvider`

#### Scenario: 图片搜索 MCP 未启用时
- **WHEN** 配置 `image-search.mcp.enabled: false` 或缺少配置
- **THEN** 不创建 `imageSearchMcpProvider()` Bean
- **AND** 应用正常启动（无报错）

#### Scenario: 双 MCP Provider 共存
- **WHEN** 同时启用了联网搜索和图片搜索 MCP
- **THEN** Spring Container 中存在两个 McpToolProvider Bean：
  - `mcpToolProvider` (联网搜索)
  - `imageSearchMcpProvider` (图片搜索)
- **AND** 通过 `@Qualifier` 区分注入

---

### Requirement: REST API 工具管理端点（运维辅助）
系统 SHALL 提供可选的 REST API 用于查询和管理工具状态（主要用于调试和监控）。

#### Scenario: 查询可用工具列表
- **WHEN** 发送 GET 请求到 `/api/tools`
- **THEN** 返回 200 OK 和 JSON 数组：
  ```json
  [
    {
      "name": "search_web",
      "description": "联网搜索",
      "status": "ACTIVE",
      "registeredAt": "2026-05-22T10:00:00Z",
      "callCount": 150
    }
  ]
  ```

#### Scenario: 单个工具详情查询
- **WHEN** 发送 GET 请求到 `/api/tools/{toolName}`
- **AND** 工具存在
- **THEN** 返回工具完整元数据 + 最近调用统计（成功/失败次数、平均耗时）

#### Scenario: 手动触发工具测试
- **WHEN** 发送 POST 请求到 `/api/tools/{toolName}/test`
- **AND** Body 包含测试参数
- **THEN** 同步执行工具调用并返回结果（超时时间 30 秒）
- **AND** 记录测试日志（DEBUG 级别）

#### Scenario: API 安全控制
- **WHEN** 未认证的用户访问 `/api/tools/*` 端点
- **THEN** 返回 401 Unauthorized（如果启用了 Spring Security）
- **OR** 正常返回（开发环境默认允许匿名访问）

---

### Requirement: 工具调用链路追踪
系统 SHALL 在工具执行过程中记录关键节点日志，便于问题排查。

#### Scenario: 结构化日志记录
- **WHEN** 任意工具被调用
- **THEN** 记录 MDC (Mapped Diagnostic Context) 日志：
  ```
  [toolName=search_web] [memoryId=abc123] [startTime=10:00:00] [endTime=10:00:02] [status=SUCCESS] [resultCount=5]
  ```

#### Scenario: 异常堆栈保留
- **WHEN** 工具执行失败
- **THEN** 日志包含完整异常堆栈（ERROR 级别）
- **AND** 脱敏处理敏感信息（API Key、URL 中的 token 等）

#### Scenario: 性能指标采集
- **WHEN** 工具执行完成
- **THEN** 更新内部计数器（AtomicLong）：
  - `totalCalls`: 总调用次数
  - `successCount`: 成功次数
  - `failureCount`: 失败次数
  - `totalDurationMs`: 累计耗时
- **AND** 可通过 `/api/tools/stats` 端点查看聚合统计
