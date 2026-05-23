## ADDED Requirements

### Requirement: 图片搜索服务独立模块化架构
系统 SHALL 将图片搜索 MCP Server 实现为独立的 Maven 子模块，具备完整的生命周期管理。

#### Scenario: 模块打包与依赖隔离
- **WHEN** 执行 `mvn package -pl image-search-mcp`
- **THEN** 生成独立的 `image-search-mcp-1.0.0-SNAPSHOT.jar`（fat jar）
- **AND** 该 JAR 包含所有运行时依赖（通过 spring-boot-maven-plugin repackage）
- **AND** 不依赖主应用的其他模块（解耦）

#### Scenario: 独立启动入口
- **WHEN** 运行 `java -jar image-search-mcp.jar --mode stdio`
- **THEN** 启动类 `ImageSearchMcpApplication` 初始化 Spring Context
- **AND** 仅加载图片搜索相关的 Bean（排除主应用的 DataSource、JPA 等）

#### Scenario: 配置文件外部化
- **WHEN** 在 JAR 同目录下放置 `application-image-search.yaml`
- **THEN** 优先加载外部配置文件而非 JAR 内嵌配置
- **AND** 支持环境变量覆盖（如 `${IMAGE_SEARCH_API_KEY}`）

---

### Requirement: Stdio 传输层实现
系统 SHALL 实现 MCP 协议的 Stdio（标准输入输出）传输模式，适用于本地开发场景。

#### Scenario: 消息帧解析
- **WHEN** 从 stdin 读取到 JSON-RPC 请求（以换行符分隔）
- **THEN** 解析请求 ID、方法名（method）、参数（params）
- **AND** 支持批量请求（数组格式）

#### Scenario: 响应序列化输出
- **WHEN** 处理完请求后
- **THEN** 将结果写入 stdout，格式符合 JSON-RPC 2.0 规范：
  ```json
  {
    "jsonrpc": "2.0",
    "id": 1,
    "result": { ... }
  }
  ```

#### Scenario: 错误响应标准化
- **WHEN** 请求处理过程中发生异常
- **THEN** 返回错误响应：
  ```json
  {
    "jsonrpc": "2.0",
    "id": 1,
    "error": {
      "code": -32603,
      "message": "Internal error",
      "data": "详细堆栈信息"
    }
  }
  ```

#### Scenario: 优雅关闭信号处理
- **WHEN** 收到 SIGTERM/SIGINT 信号
- **THEN** 完成当前正在处理的请求
- **AND** 关闭数据库连接池、HTTP 客户端等资源
- **AND** 退出进程（exit code 0）

---

### Requirement: SSE 传输层实现
系统 SHALL 实现 MCP 协议的 SSE（Server-Sent Events）传输模式，适用于生产环境和 Serverless 部署。

#### Scenario: SSE 端点建立连接
- **WHEN** MCP Client 发送 GET 请求到 `/mcp/sse`
- **THEN** 服务返回 200 OK，Content-Type: text/event-stream
- **AND** 保持长连接不断开（心跳间隔 15 秒）

#### Scenario: 双向通信通道
- **WHEN** SSE 连接建立后
- **THEN** 服务通过 SSE 流推送消息给客户端
- **AND** 客户端通过 POST `/mcp/message` 发送请求给服务
- **AND** 两端通过 sessionId 关联同一会话

#### Scenario: 会话管理与超时清理
- **WHEN** 新客户端建立 SSE 连接
- **THEN** 分配唯一 sessionId（UUID 格式）
- **AND** 存储到 ConcurrentHashMap<sessionId, McpSession>
- **AND** 后台定时任务每 60 秒清理超过 5 分钟无活动的会话

#### Scenario: 并发连接限制
- **WHEN** 同时在线的 SSE 连接数达到配置上限（默认 100）
- **THEN** 拒绝新连接，返回 503 Service Unavailable
- **AND** 响应头包含 `Retry-After: 60`（建议 60 秒后重试）

---

### Requirement: 图片搜索核心业务逻辑
系统 SHALL 实现多源聚合的图片检索算法，优化结果质量。

#### Scenario: 关键词语义扩展
- **WHEN** 用户输入查询词 "cat"
- **THEN** 系统自动扩展同义词：["cat", "kitten", "feline", "pet cat"]
- **AND** 分别用扩展后的关键词搜索，合并去重结果

#### Scenario: 图片质量评分排序
- **WHEN** 获取到候选图片列表
- **THEN** 按综合得分排序，权重公式：
  ```
  score = 0.4 * relevance + 0.3 * resolution_score + 0.2 * popularity + 0.1 * freshness
  ```
  - `relevance`: 与查询词的文本相似度
  - `resolution_score`: 分辨率归一化值（1920x1080=1.0）
  - `popularity`: 下载量/点赞数的对数变换
  - `freshness`: 发布时间的衰减函数

#### Scenario: 结果缓存策略
- **WHEN** 相同查询词在 1 小时内再次请求
- **THEN** 直接返回 Redis 缓存的结果（TTL: 3600 秒）
- **AND** 缓存 Key 格式：`imgsearch:{md5(query + params)}`

#### Scenario: 敏感内容过滤
- **WHEN** 检测到图片可能包含成人内容（基于 API 返回的安全标志）
- **THEN** 自动过滤该图片，不计入最终结果
- **AND** 补充下一张备选图片以保证返回数量满足要求

---

### Requirement: API 密钥安全管理
系统 SHALL 安全地存储和使用第三方 API 密钥。

#### Scenario: 密钥配置加密存储
- **WHEN** 在 application.yaml 中配置 `image-search.sources[0].api-key`
- **THEN** 支持使用 Jasypt 加密：`ENC(encrypted_value)`
- **AND** 启动时自动解密（需配置 jasypt.encryptor.password 环境变量）

#### Scenario: 密钥轮换机制
- **WHEN** 管理员更新 API Key（修改配置文件或环境变量）
- **THEN** 下次请求时立即生效（无需重启服务）
- **AND** 旧密钥的缓存结果在 TTL 过期后自然淘汰

#### Scenario: 密钥泄露监控
- **WHEN** 连续 5 次请求返回 401 Unauthorized
- **THEN** 触发安全告警（记录 ERROR 日志 + 发送通知）
- **AND** 临时禁用该数据源（5 分钟冷却期）
