## 1. 基础设施搭建

- [ ] 1.1 创建 `service/tools/` 目录结构，包含 BaseToolService 接口和 ToolInfo DTO
- [ ] 1.2 实现 ToolRegistry 单例类（饿汉式 + ConcurrentHashMap），包含注册、查询、注销方法
- [ ] 1.3 添加 Maven 依赖到 pom.xml：Jsoup (1.15+)、Apache PDFBox (2.0+)、HttpClient (5.x)
- [ ] 1.4 在 application.yaml 中添加工具配置段（tools.enabled、tools.default-timeout-ms、tools.max-concurrent-executions）
- [ ] 1.5 编写 ToolRegistry 单元测试：验证单例性、线程安全、并发注册/注销
- [ ] 1.6 实现 ToolRegistry 自动发现机制（Spring BeanPostProcessor 扫描 @Tool 注解的 @Component）

## 2. 核心工具开发 - 网络工具集

- [ ] 2.1 实现 WebSearchTool 类，使用 @Tool 注解标注 search() 方法
- [ ] 2.2 封装搜索引擎 API 调用逻辑（支持 Google Custom Search / Bing API / SerpAPI）
- [ ] 2.3 实现 SearchResult 数据类（title, url, snippet, source）
- [ ] 2.4 添加搜索超时处理（默认 10 秒）和重试机制（最多 2 次）
- [ ] 2.5 实现 WebScraperTool 类，基于 Jsoup 实现 scrape() 方法
- [ ] 2.6 支持自定义 CSS 选择器提取特定 DOM 内容
- [ ] 2.7 实现 URL 合法性校验（正则表达式或 java.net.URL 解析）
- [ ] 2.8 处理反爬虫机制（User-Agent 轮换、请求间隔控制）
- [ ] 2.9 编写 WebSearchTool 和 WebScraperTool 的集成测试（Mock HTTP Server）

## 3. 核心工具开发 - 文档处理工具

- [ ] 3.1 实现 PdfGeneratorTool 类，提供 generateFromMarkdown() 方法
- [ ] 3.2 实现 PdfOptions 配置类（pageSize, margin, fontFamily, headerFooter, watermark）
- [ ] 3.3 集成中文字体支持（SimSun / Microsoft YaHei），确保无乱码
- [ ] 3.4 实现大文档自动分页和目录生成功能（>50 页时触发）
- [ ] 3.5 定义 ImageAnalyzerTool 接口签名（预留实现），抛出 UnsupportedOperationException
- [ ] 3.6 定义 ImageAnalysisResult 返回值结构（caption, objects, text, confidence）
- [ ] 3.7 定义 CodeExecutorTool 接口签名（预留实现），抛出 UnsupportedOperationException
- [ ] 3.8 定义 ExecutionResult 返回值结构（stdout, stderr, exitCode, durationMs）
- [ ] 3.9 定义 DataProcessorTool 接口签名（预留实现），抛出 UnsupportedOperationException
- [ ] 3.10 定义 ProcessingResult 返回值结构（data, statistics, warnings）

## 4. 图片搜索 MCP 服务端

- [ ] 4.1 创建 `image-search-mcp` Maven 子模块（独立 pom.xml，spring-boot-starter 依赖）
- [ ] 4.2 实现 ImageSearchMcpApplication 启动类（排除主应用 DataSource/JPA 自动配置）
- [ ] 4.3 实现 McpTransportStrategy 接口及 StdioTransportStrategy 实现（JSON-RPC over stdin/stdout）
- [ ] 4.4 实现 SseTransportStrategy（HTTP SSE 端点 + POST /mcp/message 双向通道）
- [ ] 4.5 实现 UnsplashService 适配器（调用 Unsplash REST API，处理认证、分页、错误码）
- [ ] 4.6 实现 PexelsService 适配器（调用 Pexels v1 API，类似 Unsplash 的封装模式）
- [ ] 4.7 实现多数据源 fallback 机制（按 priority 尝试，失败自动切换下一个源）
- [ ] 4.8 实现图片质量评分排序算法（relevance * 0.4 + resolution * 0.3 + popularity * 0.2 + freshness * 0.1）
- [ ] 4.9 实现 Redis 缓存层（Key: imgsearch:{md5(query)}, TTL: 3600s）
- [ ] 4.10 实现敏感内容过滤（基于 API 安全标志，自动替换备选图）
- [ ] 4.11 实现 API 密钥安全管理（Jasypt 加密存储、运行时热更新、泄露监控告警）
- [ ] 4.12 编写 MCP 协议兼容性测试（模拟 Client 发送 initialize/tools/list/tools/call 请求）

## 5. 工具集成与系统改造

- [ ] 5.1 修改 AiCodeHelperServiceFactory.buildService() 方法，注入 ToolRegistry.getAllTools()
- [ ] 5.2 添加 `tools.enabled` 开关控制（false 时跳过工具注入，保持向后兼容）
- [ ] 5.3 扩展 McpConfig.java，新增 imageSearchMcpProvider() Bean（条件性创建）
- [ ] 5.4 可选：在 ChatService 中添加工具调用状态 SSE 通知（tool_call_start/end/error 事件）
- [ ] 5.5 创建 ToolController REST API（GET /api/tools, GET /api/tools/{name}, POST /api/tools/{name}/test）
- [ ] 5.6 实现工具调用链路追踪（MDC 结构化日志、性能指标采集、异常堆栈脱敏）
- [ ] 5.7 更新 application.yaml 示例文件，添加所有新配置项的注释说明

## 6. 测试与文档

- [ ] 6.1 编写端到端测试：ChatService.streamChat() → AI 调用 WebScraperTool → 返回结果
- [ ] 6.2 编写并发压力测试：模拟 10 个用户同时调用不同工具，验证 Semaphore 限流
- [ ] 6.3 编写异常场景测试：网络超时、API 限流、空结果集、非法参数
- [ ] 6.4 性能基准测试：记录各工具的平均响应时间（目标：<2s for WebSearch, <5s for WebScraper, <10s for PdfGenerator）
- [ ] 6.5 更新 README.md，添加"多种工具集功能"章节（架构图、快速开始、配置指南）
- [ ] 6.6 编写开发者文档：如何添加新的 @Tool 工具（3 步流程示例）
