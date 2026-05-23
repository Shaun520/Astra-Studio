## ADDED Requirements

### Requirement: WebSearchTool 联网搜索功能
系统 SHALL 提供 @Tool 标注的联网搜索工具，支持多搜索引擎后端。

#### Scenario: 基础关键词搜索
- **WHEN** AI 调用 `WebSearchTool.search(query="Spring Boot 最佳实践", count=5)`
- **THEN** 工具返回 List<SearchResult>，每项包含：
  - `title`: String（网页标题）
  - `url`: String（原始链接）
  - `snippet`: String（摘要文本，最多200字符）
  - `source`: String（搜索引擎名称，如 "Google"、"Bing"）

#### Scenario: 搜索结果数量限制
- **WHEN** 调用时指定 count=3
- **THEN** 最多返回 3 条结果（即使搜索引擎返回更多）
- **AND** 结果按相关性降序排列

#### Scenario: 搜索超时处理
- **WHEN** 搜索引擎响应时间超过 10 秒
- **THEN** 抛出 `ToolExecutionException` 并包含超时错误信息
- **AND** 记录 WARN 级别日志

---

### Requirement: WebScraperTool 网页内容抓取
系统 SHALL 提供 @Tool 标注的网页抓取工具，支持提取结构化内容。

#### Scenario: 抓取指定 URL 的正文内容
- **WHEN** AI 调用 `WebScraperTool.scrape(url="https://example.com/article")`
- **THEN** 工具返回 ScrapedContent 对象，包含：
  - `title`: String（页面标题）
  - `text`: String（纯文本正文，去除 HTML 标签）
  - `metadata`: Map（发布时间、作者等元数据）
  - `links`: List<String>（页面内链接列表）

#### Scenario: 自定义内容提取规则
- **WHEN** 调用时传入 `selector="article.content"` 参数
- **THEN** 仅提取匹配 CSS 选择器的 DOM 元素内容
- **AND** 忽略导航栏、侧边栏、广告等无关内容

#### Scenario: 处理反爬虫机制
- **WHEN** 目标网站返回 403 Forbidden 或检测到爬虫行为
- **THEN** 自动重试一次（更换 User-Agent）
- **AND** 若仍失败，返回部分已获取的内容 + 错误提示

#### Scenario: URL 合法性校验
- **WHEN** 传入的 URL 格式无效（如 "not-a-url"）
- **THEN** 立即抛出 `IllegalArgumentException`，不发起网络请求

---

### Requirement: 图片搜索 MCP 服务端协议实现
系统 SHALL 实现符合 Open-MCP 协议规范的图片搜索服务。

#### Scenario: Stdio 模式启动
- **WHEN** 通过命令行启动 `java -jar image-search-mcp.jar --mode stdio`
- **THEN** 服务监听标准输入/输出流
- **AND** 响应 MCP 协议的 initialize、tools/list、tools/call 等请求

#### Scenario: SSE 模式启动
- **WHEN** 通过命令行启动 `java -jar image-search-mcp.jar --mode sse --port 8090`
- **THEN** 服务在 8090 端口提供 HTTP SSE 端点
- **AND** 客户端可通过 GET /sse 建立长连接

#### Scenario: 工具列表暴露
- **WHEN** MCP Client 发送 `tools/list` 请求
- **THEN** 返回图片搜索工具定义：
  ```json
  {
    "name": "search_images",
    "description": "搜索高质量图片，支持关键词、颜色、方向筛选",
    "inputSchema": {
      "type": "object",
      "properties": {
        "query": {"type": "string", "description": "搜索关键词"},
        "count": {"type": "integer", "default": 10, "maximum": 30},
        "orientation": {"type": "string", "enum": ["landscape", "portrait", "squarish"]}
      },
      "required": ["query"]
    }
  }
  ```

#### Scenario: 工具调用执行
- **WHEN** MCP Client 发送 `tools/call` 请求，参数为 `{query: "sunset", count: 5}`
- **THEN** 服务调用 Unsplash/Pexels API 获取图片
- **AND** 返回结果：
  ```json
  {
    "content": [{
      "type": "image",
      "image": {
        "url": "https://images.unsplash.com/photo-xxx",
        "thumbnailUrl": "https://images.unsplash.com/photo-xxx?w=200",
        "altText": "Beautiful sunset over ocean",
        "width": 1920,
        "height": 1080,
        "source": "unsplash",
        "photographer": "John Doe"
      }
    }],
    "isError": false
  }
  ```

---

### Requirement: 多数据源适配器模式
系统 SHALL 支持多个图片搜索 API 提供商，具备 fallback 能力。

#### Scenario: 主数据源正常工作
- **WHEN** 配置主数据源为 Unsplash，且 API 可用
- **THEN** 所有图片搜索请求发送至 Unsplash API
- **AND** 不触发 fallback 逻辑

#### Scenario: 主数据源故障自动切换
- **WHEN** Unsplash API 返回 429 Too Many Requests 或 500 错误
- **THEN** 自动切换至备用数据源 Pexels 重试相同请求
- **AND** 记录切换日志（INFO 级别）

#### Scenario: 所有数据源均不可用
- **WHEN** Unsplash 和 Pexels 都返回错误
- **THEN** 返回空结果集 + 错误说明："所有图片源暂时不可用"
- **AND** 触发告警通知（可选集成邮件/钉钉 webhook）

#### Scenario: 数据源优先级配置
- **WHEN** application.yaml 中配置：
  ```yaml
  image-search:
    sources:
      - name: unsplash
        priority: 1
        api-key: ${UNSPLASH_API_KEY}
      - name: pexels
        priority: 2
        api-key: ${PEXELS_API_KEY}
  ```
- **THEN** 系统按 priority 升序尝试数据源
