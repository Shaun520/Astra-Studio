## Why

当前系统硬编码使用单一 AI 模型（glm-5），用户无法根据任务需求选择不同的模型。不同模型在不同场景下各有优势：DeepSeek 适合代码生成和推理，GLM-5 适合通用对话，Qwen 适合中文理解和生成。提供模型选择功能可以让用户体验到不同模型的能力差异，并根据具体任务选择最优模型，提升对话质量和效率。

## What Changes

- **前端新增模型选择器组件**：在 Composer.vue 的工具栏中添加模型下拉选择框，显示可用模型列表
- **后端支持动态模型切换**：修改 AiCodeHelperServiceFactory，使其能够根据请求参数动态创建使用指定模型的 AI 服务实例
- **API 扩展**：在 `/ai/chat` 接口新增 `model` 参数，用于传递用户选择的模型名称
- **全栈参数传递打通**：api.ts → App.vue → Controller → Factory 完整传递模型参数
- **默认模型配置**：保留 glm-5 作为默认模型，向后兼容

### 支持的模型列表

| 模型标识 | 显示名称 | 适用场景 |
|---------|---------|---------|
| `glm-5` | GLM-5 | 通用对话、多轮交互（默认） |
| `deepseek-v4-flash` | DeepSeek V4 Flash | 代码生成、逻辑推理、数学计算 |
| `qwen3.6-flash-2026-04-16` | Qwen 3.6 Flash | 中文理解、文本生成、知识问答 |

## Capabilities

### New Capabilities
- **model-selection**: 前端模型选择UI + 后端动态模型路由功能，允许用户在对话前选择使用的AI模型，并在请求时将模型信息传递给后端以创建对应的服务实例

### Modified Capabilities
（无现有 capability 需要修改）

## Impact

**前端影响**：
- [Composer.vue](file:///d:/project/Astra-Studio/Astra-Studio/src/components/Composer.vue)：新增模型选择下拉框 UI 组件及状态管理
- [App.vue](file:///d:/project/Astra-Studio/Astra-Studio/src/App.vue)：handleSend 函数扩展 model 参数接收与透传
- [api.ts](file:///d:/project/Astra-Studio/Astra-Studio/src/services/api.ts)：SendChatMessageOptions 接口新增 model 字段，FormData 构建逻辑扩展

**后端影响**：
- [AiController.java](file:///d:/project/Astra-Studio/Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/controller/AiController.java)：chat 接口新增 @RequestParam model 参数
- [AiCodeHelperServiceFactory.java](file:///d:/project/Astra-Studio/Astra-Studio-Open-Ai/src/main/java/com/example/astrastudioopenai/ai/AiCodeHelperServiceFactory.java)：
  - getService() 方法签名扩展，增加 modelName 参数
  - buildService() 方法支持动态设置模型名称
  - createModel() 方法接收 modelName 参数而非使用字段值
  - 缓存 key 扩展包含模型信息（避免不同模型共享缓存）
  - calculateTimeout() 可根据模型特性调整超时时间（可选优化）

**配置文件影响**：
- [application.yaml](file:///d:/project/Astra-Studio/Astra-Studio-Open-Ai/src/main/resources/application.yaml)：可能需要移除硬编码的 model-name 或改为默认值配置

**依赖项变化**：
- 无新增外部依赖（仍使用 LangChain4j OpenAI 兼容接口）
- 利用现有的动态构建器架构（v2.0），改动量最小化

**兼容性**：
- ✅ 向后兼容：不传 model 参数时使用默认模型（glm-5）
- ✅ 不影响现有 deepThink 和 webSearch 功能：model 参数与这两个参数正交组合
- ⚠️ 缓存策略调整：缓存 key 从 `(deepThink, webSearch)` 变为 `(deepThink, webSearch, model)`，首次部署时会重建缓存
