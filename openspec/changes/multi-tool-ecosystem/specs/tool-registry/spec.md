## ADDED Requirements

### Requirement: ToolRegistry 单例实例管理
系统 SHALL 提供全局唯一的 ToolRegistry 单例，用于集中管理所有 @Tool 工具实例。

#### Scenario: 获取单例实例
- **WHEN** 任意代码调用 `ToolRegistry.getInstance()`
- **THEN** 系统返回同一个 ToolRegistry 实例（饿汉式初始化）
- **AND** 该实例在 JVM 生命周期内保持不变

#### Scenario: 线程安全并发访问
- **WHEN** 多个线程同时调用 `getInstance()` 或操作工具列表
- **THEN** 所有操作线程安全，无数据竞争或死锁风险

---

### Requirement: 工具注册与注销机制
系统 SHALL 支持动态注册和注销 @Tool 工具实例。

#### Scenario: 注册新工具
- **WHEN** 调用 `ToolRegistry.getInstance().registerTool("webSearch", webSearchToolInstance)`
- **THEN** 系统将工具实例存储到内部 ConcurrentHashMap 中，key 为工具名称
- **AND** 如果同名工具已存在，抛出 `IllegalStateException`（防止重复注册）

#### Scenario: 注销已存在工具
- **WHEN** 调用 `ToolRegistry.getInstance().unregisterTool("webSearch")`
- **THEN** 系统从 ConcurrentHashMap 中移除该工具
- **AND** 如果工具不存在，静默忽略（不抛异常）

#### Scenario: 批量注册工具列表
- **WHEN** 调用 `ToolRegistry.getInstance().registerTools(List<Object> tools)`
- **THEN** 系统遍历列表，通过反射获取 @Tool 注解的 name 属性作为 key 进行注册
- **AND** 任一工具注册失败时，回滚所有已注册的工具（原子性保证）

---

### Requirement: 工具查询接口
系统 SHALL 提供多种查询方式以获取工具信息。

#### Scenario: 获取所有工具实例
- **WHEN** 调用 `ToolRegistry.getInstance().getAllTools()`
- **THEN** 返回包含所有已注册工具的不可变列表（`Collections.unmodifiableList()`）

#### Scenario: 按名称查询单个工具
- **WHEN** 调用 `ToolRegistry.getInstance().getTool("webSearch")`
- **AND** 该工具已注册
- **THEN** 返回对应的 Object 实例

#### Scenario: 查询不存在的工具
- **WHEN** 调用 `ToolRegistry.getInstance().getTool("nonExistentTool")`
- **THEN** 返回 null（不抛异常）

#### Scenario: 获取工具元数据列表
- **WHEN** 调用 `ToolRegistry.getInstance().getToolInfos()`
- **THEN** 返回 List<ToolInfo>，每个 ToolInfo 包含：
  - `name`: String（@Tool 注解的 name）
  - `description`: String（@Tool 注解的 description）
  - `className`: String（工具类的全限定名）
  - `registeredAt`: Instant（注册时间戳）

---

### Requirement: 工具生命周期管理
系统 SHALL 在 Spring 应用启动时自动扫描并注册标注了 @Tool 的 Bean。

#### Scenario: 自动发现并注册工具 Bean
- **WHEN** Spring ApplicationContext 初始化完成
- **AND** 存在标注了 `@Component` 和 `@Tool` 的类
- **THEN** ToolRegistry 自动将这些 Bean 注册到工具列表中

#### Scenario: 手动注册非 Spring 管理的工具
- **WHEN** 开发者创建了未交给 Spring 管理的工具实例（如 new WebScraperTool()）
- **THEN** 可通过 `registerTool()` 方法手动注册
- **AND** 该工具同样参与 AI 服务的工具注入

#### Scenario: 工具健康检查
- **WHEN** 调用 `ToolRegistry.getInstance().healthCheck()`
- **THEN** 系统遍历所有工具，验证其非 null 且可正常调用（可选：执行简单的 ping 操作）
- **AND** 返回 Map<String, Boolean> 表示各工具的健康状态

---

### Requirement: 工具配置与限制
系统 SHALL 提供可配置的工具行为参数。

#### Scenario: 全局工具超时设置
- **WHEN** 在 application.yaml 中配置 `tools.default-timeout-ms: 10000`
- **THEN** 所有未单独配置超时的工具默认使用该值
- **AND** 单个工具可通过 `@Tool(timeout = 5000)` 覆盖全局设置

#### Scenario: 最大并发工具数限制
- **WHEN** 配置 `tools.max-concurrent-executions: 10`
- **THEN** ToolRegistry 内部维护一个 Semaphore(10)
- **AND** 当并发工具调用达到上限时，后续请求阻塞等待或快速失败（可配置策略）

#### Scenario: 启用/禁用特定工具
- **WHEN** 配置 `tools.disabled-list: [codeExecutor, dataProcessor]`
- **THEN** 这些工具虽然已注册，但在 `getAllTools()` 时被过滤掉
- **AND** AI 服务无法调用被禁用的工具
