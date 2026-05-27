## Context

**当前状态：**
- `StudioPanel.vue` 已有完整的参数配置 UI（温度/最大输出/Top-p 滑块 + 系统提示文本域 + 模型卡片），但所有参数为组件内局部 `ref`，与聊天流程完全隔离
- 前端 `SendChatMessageOptions` 仅包含 memoryId/text/files/deepThink/webSearch/knowledgeBase/model/selectedTools，无 LLM 参数字段
- 后端 `ChatController.chat()` 接收固定参数集，不包含 temperature/maxTokens/topP/systemPrompt
- 后端 `AiCodeHelperServiceFactory.createModel()` 使用 `OpenAiStreamingChatModel.builder()` 构建，该 builder 原生支持 `.temperature()/.maxTokens()/.topP()` 方法
- 后端 `AiCodeHelperService` 接口使用 LangChain4j `@UserMessage` 注解，当前未使用 `@SystemMessage`
- 模型选择器在 `MainHeader.vue` 中独立管理，StudioPanel 的模型卡片硬编码为 "Astra Sage 4"

**约束：**
- LangChain4j OpenAI 模型的 temperature/maxTokens/topP 在 builder 阶段设置，非请求级别动态参数
- System prompt 在 LangChain4j AiServices 中可通过 `systemMessageProvider` 动态设置
- 现有 Factory 缓存机制基于 `(deepThink, webSearch, modelName, knowledgeBase)` 组合 key，加入参数后需评估缓存策略

## Goals / Non-Goals

**Goals：**
1. StudioPanel 参数调整后，立即生效于下次发送的消息
2. 参数值跨页面刷新持久化（localStorage）
3. 模型卡片实时反映当前选中模型名称和标签
4. 后端正确将参数注入 LLM 调用

**Non-Goals：**
- 不实现"按会话隔离参数"（全局统一配置即可）
- 不实现参数预设/模板功能
- 不修改 Factory 缓存策略（温度等参数在每次请求时动态传递，不参与缓存 key）

## Decisions

### D1: 前端状态管理 — App 级 provide/inject + localStorage 持久化

**选择：** 在 `App.vue` 中创建 `chatParams` reactive 对象，通过 provide 注入全局。StudioPanel inject 并双向绑定，Composer inject 读取。

**理由：**
- provide/inject 是 Vue3 跨组件通信的标准方式，避免 props drilling
- reactive 对象确保任意组件修改后所有消费者同步更新
- localStorage 持久化保证刷新不丢失

**替代方案（否决）：**
- Pinia store：过重，当前仅此一处需要全局状态
- EventBus：Vue3 不推荐，缺乏类型安全

### D2: 后端 LLM 参数注入 — 请求级覆盖

**选择：** temperature / maxTokens / topP 通过 ChatService → AiCodeHelperService 透传，在调用 `chatWithStream()` 时使用 LangChain4j 的 `ChatRequestParameters` 或重新构建带参数的 model 实例。

**具体方案：**
- `OpenAiStreamingChatModel` 的 temperature/maxTokens/topP 可在 builder 设置，但为了支持请求级动态参数：
  - 方案 A（推荐）：在 `ChatService.streamChat()` 中创建临时的参数化 model 实例用于本次请求
  - 方案 B：扩展 `AiCodeHelperService` 接口增加 `chatWithStream(ChatRequestParams params)` 重载

**System Prompt 注入：** 使用 LangChain4j 的 `Message` 构造 `SystemMessage`，在调用 `chatWithStream()` 前作为额外消息注入，或通过 AiServices 的 `systemMessageProvider` 回调动态提供。

### D3: 模型卡片联动 — 复用已有 selectedModel provide

**选择：** StudioPanel inject `selectedModel`（已在 App.vue 中 provide），根据模型 value 映射显示名称和标签。移除硬编码的 "Astra Sage 4" 和静态 tags。

**模型元数据映射表：** 在前端维护一个 modelMeta Map，key 为 model value，包含 { name, tags[] }。

### D4: 缓存策略 — 参数不参与缓存 key

**选择：** Factory 缓存 key 保持不变 `(deepThink, webSearch, modelName, knowledgeBase)`。temperature 等参数在 ChatService 层面作为请求级参数传递给 LLM 调用，不影响 Service 实例本身。

**理由：** 温度变化极频繁（用户拖动滑块），若纳入缓存 key 会导致大量实例创建和内存浪费。

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| OpenAiStreamingChatModel 每次 request 重建 | 微性能开销（~ms级） | 仅当参数与默认值不同时重建；可后续优化为对象池 |
| System Prompt 通过消息注入 vs provider | 消息注入更简单但语义不够正式 | 先用消息注入快速落地，后续迁移到 systemMessageProvider |
| 前后端参数默认值不一致 | 用户困惑 | 文档明确标注前后端默认值；前端滑块初始值对齐后端默认值 |
| localStorage 存储大小 | 可忽略（4 个字段 < 1KB） | 无需特殊处理 |
