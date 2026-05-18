## Why

当前系统要求用户手动选择AI模型（GLM-5/DeepSeek/Qwen），但用户往往不清楚各模型的擅长领域，导致次优选择（如用昂贵的GLM-5处理简单代码任务）。通过实现**基于任务类型的自动路由**，系统可智能识别用户意图并匹配最优模型，预计将API调用成本再降低15-25%，同时提升任务完成质量。

## What Changes

- **新增意图分类器（Intent Classifier）**：基于关键词+规则引擎的轻量级文本分类，识别用户输入的任务类型（代码生成、中文问答、通用对话）
- **新增自动路由服务（AutoRoutingService）**：编排层，当用户传入 `model=auto` 时触发智能路由，否则尊重手动选择
- **集成到 AiController**：在 `/ai/chat` 接口中增加路由决策逻辑，并在SSE响应中返回路由信息供前端展示
- **新增路由统计接口**：`GET /api/ai/routing-stats` 提供路由决策的监控数据（命中率、置信度分布、模型使用比例）
- **新增配置文件**：`intent-rules.yaml` 定义分类规则，支持热更新无需重启

## Capabilities

### New Capabilities
- `auto-routing`: 基于用户输入文本的自动模型选择功能，包括意图识别、路由决策、统计监控和用户覆盖机制

### Modified Capabilities
- `model-selection`: 扩展现有模型选择能力，支持 `auto` 模式作为新的模型选项值；在现有 model 参数基础上增加自动路由分支逻辑

## Impact

**后端影响**：
- [AiController.java](file:///d:/project/Astra-Studio/Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/controller/AiController.java)：chat() 方法增加自动路由调用和路由信息事件推送
- 新增 `AutoRoutingService.java`：核心编排服务，协调意图分类器和模型路由器
- 新增 `IntentClassifier.java`：规则引擎实现，加载 YAML 配置执行文本分类
- 新增 `ModelRouter.java`：根据意图映射到目标模型
- 新增 `RoutingStatsService.java`：线程安全的统计数据收集与查询
- 新增 `intent-rules.yaml`：可配置的分类规则定义
- [application.yaml](file:///d:/project/Astra-Studio/Astra-Studio-Open-Ai/src/main/resources/application.yaml)：添加 auto-routing 配置项（启用开关、置信度阈值等）

**前端影响**（可选增强）：
- 模型选择器UI：新增"自动"选项（显示为"🤖 智能"或类似标识）
- SSE消息处理：解析新增的 `routing_info` 事件类型，展示路由决策原因

**兼容性影响**：
- ✅ 完全向后兼容：不传 model 参数或传具体模型名时行为不变
- ✅ 渐进式采用：仅当显式传入 `model=auto` 时才启用自动路由
- ⚠️ 新增依赖：无外部库依赖（纯Java实现规则引擎）

**性能影响**：
- 分类延迟：< 5ms（关键词匹配 + 正则表达式）
- 内存开销：规则配置约 10KB，统计数据随请求量线性增长（可配置清理策略）
