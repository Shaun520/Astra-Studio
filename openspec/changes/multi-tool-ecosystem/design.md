## Context

### 当前状态
Astra-Studio-Open-Ai 系统已具备完整的 AI 对话能力，基于 langchain4j 框架实现流式 SSE 输出。当前系统仅通过 `McpConfig.java` 配置了一个联网搜索 MCP 工具（BigModel Web Search），该工具在 `AiCodeHelperServiceFactory.buildService()` 中通过 `builder.toolProvider(mcpToolProvider)` 注入到 AiServices 中。

系统架构特点：
- 使用工厂模式（`AiCodeHelperServiceFactory`）动态构建 AI 服务实例
- 支持 deepThink、webSearch、knowledgeBase 等功能开关
- 已有完整的会话持久化、RAG 知识库检索、自动路由等能力
- 前端为 Vue 3 + TypeScript 的独立项目（`Astra-Studio/`）

### 业务需求
用户期望 AI 助手能够：
1. **自主选择工具**：根据对话上下文自动调用合适的工具完成任务
2. **多样化工具支持**：除了搜索，还需要抓取网页内容、生成 PDF 文档、分析图片等
3. **图片搜索能力**：独立的图片检索服务，支持跨项目集成
4. **统一管理**：所有工具集中管理，便于维护和扩展

## Goals / Non-Goals

**Goals:**
- 构建可扩展的 @Tool 工具生态系统，支持 6 种核心工具类型
- 实现单例模式的 ToolRegistry，提供统一的工具注册、查询和管理接口
- 开发图片搜索 MCP Server，支持 Stdio 和 SSE 双模式运行
- 无缝集成到现有 ChatService 流程，不影响现有功能
- 提供清晰的工具元数据接口，便于前端展示和调试

**Non-Goals:**
- ❌ 不包含 Serverless 部署配置（Docker/K8s/云函数）
- ❌ 不包含前端 UI 改造（工具调用状态可视化留待后续迭代）
- ❌ 不包含工具权限控制和用户级别隔离（假设所有用户共享相同工具集）
- ❌ 不包含工具执行结果的持久化存储（仅实时返回）
- ❌ 不包含图片 CDN 或本地缓存优化

## Decisions

### 决策 1：使用 langchain4j 原生 @Tool 注解而非自定义抽象层

**选择方案**：直接利用 langchain4j 的 `@Tool` 注解标记工具方法

**理由**：
- ✅ 与现有框架完全兼容，无需额外适配层
- ✅ langchain4j 自动处理工具描述生成、参数解析、错误处理
- ✅ 支持 streaming 工具调用结果
- ✅ 社区成熟稳定，文档完善

**替代方案**：
- 自定义 Tool 接口 + 反射机制 → 增加复杂度，重复造轮子
- Function Calling 手动实现 → 无法利用框架内置的工具编排能力

---

### 决策 2：ToolRegistry 采用饿汉式单例 + ConcurrentHashMap

**选择方案**：
```java
public class ToolRegistry {
    private static final ToolRegistry INSTANCE = new ToolRegistry();
    private final Map<String, Object> tools = new ConcurrentHashMap<>();
    
    public static ToolRegistry getInstance() { return INSTANCE; }
}
```

**理由**：
- ✅ 线程安全：ConcurrentHashMap 保证并发注册/查询安全
- ✅ 启动时初始化：避免懒加载的竞态条件
- ✅ 简单直观：符合 Spring 应用生命周期
- ✅ 便于测试：可通过反射重置单例

**替代方案**：
- Spring Bean 单例 → 增加框架耦合，不利于跨模块使用
- 双重检查锁 → 实现复杂，容易出错

---

### 决策 3：图片搜索 MCP Server 作为独立模块（非内嵌）

**选择方案**：创建 `image-search-mcp` 子模块，独立打包为 JAR

**架构设计**：
```
image-search-mcp/
├── src/main/java/
│   └── com/example/mcp/imagesearch/
│       ├── ImageSearchMcpServer.java    # 主入口
│       ├── service/
│       │   ├── UnsplashService.java     # 数据源适配器
│       │   └── PexelsService.java
│       └── transport/
│           ├── StdioServerHandler.java   # Stdio 模式
│           └── SseServerHandler.java    # SSE 模式
├── pom.xml
```

**理由**：
- ✅ 职责分离：MCP Server 逻辑与主应用解耦
- ✅ 独立部署：可作为微服务或 Serverless 函数单独运行
- ✅ 多实例支持：不同环境可启动不同配置的实例
- ✅ 版本管理：独立发版周期

**替代方案**：
- 内嵌到主应用 → 耦合度高，无法独立扩展
- 外部进程管理 → 增加运维复杂度

---

### 决策 4：双模式传输层抽象（Stdio + SSE）

**选择方案**：基于策略模式实现传输层切换

```java
public interface McpTransportStrategy {
    void start(ToolRegistry registry);
    void stop();
}

@Component
@Profile("dev")
public class StdioTransportStrategy implements McpTransportStrategy { ... }

@Component
@Profile("prod")
public class SseTransportStrategy implements McpTransportStrategy { ... }
```

**理由**：
- ✅ 符合 Open-MCP 协议标准
- ✅ 本地开发用 Stdio（简单快速）
- ✅ 生产环境用 SSE（支持负载均衡、监控）
- ✅ 通过 Spring Profile 自动切换

**替代方案**：
- 仅支持一种模式 → 降低灵活性
- 运行时动态切换 → 增加复杂度，实际场景不需要

---

### 决策 5：工具注入采用"本地优先，MCP补充"策略

**选择方案**：在 `AiCodeHelperServiceFactory.buildService()` 中同时注入本地工具和 MCP 工具

```java
var builder = AiServices.builder(AiCodeHelperService.class)
    .chatModel(openAiChatModel)
    .streamingChatModel(streamingModel);

// 1. 注入本地 @Tool 工具
List<Object> localTools = ToolRegistry.getInstance().getAllTools();
if (!localTools.isEmpty()) {
    builder.tools(localTools.toArray(new Object[0]));
}

// 2. 注入 MCP 工具（可选）
if (webSearch) {
    builder.toolProvider(mcpToolProvider);
}

return builder.build();
```

**理由**：
- ✅ 本地工具响应更快（无网络开销）
- ✅ MCP 工具作为补充（外部 API 能力）
- ✅ 两者不冲突，langchain4j 自动合并工具列表
- ✅ 保持向后兼容（现有 webSearch 功能不受影响）

**替代方案**：
- 仅使用 MCP 工具 → 本地工具性能损失
- 仅使用本地工具 → 无法接入外部 API

---

## Risks / Trade-offs

### 风险 1：工具执行超时导致流式输出阻塞
**影响**：如果某个工具（如网页抓取）耗时过长，会导致整个对话卡住
**缓解措施**：
- 为每个工具设置独立超时时间（WebScraper: 10s, PdfGenerator: 30s）
- 在 ToolRegistry 中增加全局超时配置
- 异步执行模式：工具调用返回 Future，主线程继续流式输出

### 风险 2：工具间存在依赖冲突
**影响**：例如 PdfGenerator 需要 WebScraper 先获取内容
**缓解措施**：
- 工具设计遵循单一职责原则，每个工具独立完成一个任务
- 复杂工作流由 AI 智能体自主编排（多轮工具调用）
- 工具文档中明确标注前置条件

### 风险 3：图片搜索 API 配额限制
**影响**：高频调用可能触发 Unsplash/Pexels 的速率限制
**缓解措施**：
- 实现请求限流（Rate Limiter）：每分钟最多 50 次
- 多数据源 fallback：Unsplash → Pexels → 本地缓存
- 缓存热门查询结果（Redis TTL: 1小时）

### 风险 4：内存占用增长（ToolRegistry 单例持有大量对象）
**影响**：6 个工具类 + 图片搜索服务常驻内存
**缓解措施**：
- 工具实例采用轻量级设计（无状态或有限状态）
- 懒加载非核心工具（按需初始化）
- 监控 JVM 内存使用情况，设置告警阈值

### 权衡 1：功能丰富性 vs. 实现复杂度
- **选择**：先实现核心工具（WebSearch, WebScraper, PdfGenerator），后续迭代添加 ImageAnalyzer, CodeExecutor, DataProcessor
- **原因**：降低初期开发风险，快速验证工具生态价值

### 权衡 2：性能 vs. 可扩展性
- **选择**：ToolRegistry 使用 ConcurrentHashMap（高性能）而非数据库存储（高可扩展）
- **原因**：工具列表相对固定（<20 个），无需动态增删

## Migration Plan

### 阶段一：基础设施搭建（Day 1-2）
1. 创建 `service/tools/` 目录结构
2. 实现 `ToolRegistry` 单例类
3. 添加 Maven 依赖（Jsoup, PDFBox, HttpClient）
4. 编写单元测试验证 ToolRegistry 功能

### 阶段二：核心工具开发（Day 3-5）
1. 实现 `WebSearchTool`（复用现有 MCP 或封装 HTTP 调用）
2. 实现 `WebScraperTool`（基于 Jsoup）
3. 实现 `PdfGeneratorTool`（基于 PDFBox）
4. 集成到 `AiCodeHelperServiceFactory`

### 阶段三：图片搜索 MCP 服务（Day 6-8）
1. 创建 `image-search-mcp` 子模块
2. 实现 Unsplash/Pexels API 适配器
3. 开发 Stdio 和 SSE 传输层
4. 配置 `McpConfig.java` 新增 Bean

### 阶段四：集成测试与优化（Day 9-10）
1. 端到端测试：ChatService → ToolRegistry → 具体工具
2. 性能压测：并发工具调用的响应时间和资源消耗
3. 错误处理：网络异常、API 限流、超时场景
4. 文档更新：README、API 文档、使用示例

### 回滚策略
- 所有改动通过 Feature Toggle 控制（`tools.enabled=false` 可完全禁用）
- 数据库无 Schema 变更，无需迁移脚本
- 如遇严重问题，可直接回退代码版本并重启服务

## Open Questions

1. **Q**: 是否需要在工具调用时记录审计日志？
   - **建议**: 初期不实现，待稳定后按需添加（考虑 ELK 集成成本）

2. **Q**: 图片搜索是否需要支持用户自定义 API Key？
   - **建议**: 第一版使用系统级 API Key（配置在 application.yaml），后续支持多租户

3. **Q**: 工具执行的并发度如何控制？
   - **建议**: 使用 Semaphore 限制最大并发数（默认 10），防止资源耗尽

4. **Q**: 是否需要工具版本管理？
   - **建议**: 暂不需要，工具接口保持向后兼容即可
