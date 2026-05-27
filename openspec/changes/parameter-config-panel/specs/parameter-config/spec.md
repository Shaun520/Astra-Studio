## ADDED Requirements

### Requirement: 前端参数全局状态管理
系统 SHALL 在 App 级别通过 Vue provide/inject 机制提供全局聊天参数状态，使 StudioPanel 和 Composer 共享同一份参数配置。

#### Scenario: App.vue 提供 chatParams
- **WHEN** 应用初始化
- **THEN** `App.vue` 创建 reactive 对象 `chatParams = { temperature: 0.72, maxOutput: 4096, topP: 0.95, systemPrompt: '...' }` 并通过 `provide('chatParams', chatParams)` 注入；初始值优先从 localStorage 恢复，若无则使用默认值

#### Scenario: StudioPanel 绑定全局参数
- **WHEN** StudioPanel 渲染参数配置面板
- **THEN** 通过 `inject('chatParams')` 获取全局状态，滑块和文本域双向绑定到 `chatParams.temperature/maxOutput/topP/systemPrompt`（替代原有局部 ref）

#### Scenario: Composer 读取全局参数发送消息
- **WHEN** 用户在 Composer 中点击发送按钮
- **THEN** Composer 通过 `inject('chatParams')` 读取当前参数值，将其包含在 `SendChatMessageOptions` 中传递给 API

#### Scenario: 参数变更实时同步
- **WHEN** 用户在 StudioPanel 中拖动温度滑块从 0.72 改为 1.0
- **THEN** 所有 inject 了 `chatParams` 的组件立即感知到新值（reactive 响应式），下次发送消息自动使用新参数

---

### Requirement: 参数持久化（localStorage）
系统 SHALL 将用户调整的参数持久化到 localStorage，确保页面刷新后恢复上次配置。

#### Scenario: 保存参数到 localStorage
- **WHEN** 用户修改任意参数（温度/最大输出/Top-p/系统提示）
- **THEN** 系统将完整的 `chatParams` 对象序列化为 JSON 写入 localStorage key `'chat-params'`

#### Scenario: 页面刷新恢复参数
- **WHEN** 用户刷新页面或重新打开应用
- **THEN** 系统从 localStorage 读取 `'chat-params'`，若存在且格式合法则恢复为 `chatParams` 初始值；若不存在或解析失败则使用硬编码默认值

#### Scenario: 清除参数重置默认
- **WHEN** localStorage 中的数据损坏或被手动清除
- **THEN** 系统降级使用默认值：temperature=0.72, maxOutput=4096, topP=0.95, systemPrompt=空字符串

---

### Requirement: 聊天 API 参数传输
系统 SHALL 扩展前端聊天 API 接口，支持将 temperature / maxTokens / topP / systemPrompt 传递给后端。

#### Scenario: 发送消息携带 LLM 参数
- **WHEN** 前端调用 `POST /api/chat` 发送消息
- **THEN** FormData 中追加以下字段（均为可选）：
  - `temperature`: 数字，范围 [0, 2]，步长 0.01
  - `maxTokens`: 数字，范围 [256, 8192]，步长 128
  - `topP`: 数字，范围 [0, 1]，步长 0.01
  - `systemPrompt`: 字符串，用户自定义系统提示词

#### Scenario: 使用默认参数（向后兼容）
- **WHEN** 前端调用 `POST /api/chat` 但未传递上述任一参数
- **THEN** 后端使用内置默认值处理请求，行为与升级前完全一致

---

### Requirement: 后端 LLM 参数接收与注入
系统 SHALL 在 ChatController 层面接收 LLM 参数，并在 ChatService 中注入到 OpenAI 模型调用。

#### Scenario: Controller 接收新增参数
- **WHEN** 后端收到 `POST /api/chat` 请求且 FormData 包含 temperature / maxTokens / topP / systemPrompt
- **THEN** `ChatController.chat()` 通过 `@RequestParam(defaultValue="...")` 接收各参数并透传给 `ChatService.streamChat()`

#### Scenario: Temperature 注入 OpenAI 模型
- **WHEN** ChatService 收到 temperature 参数（如 1.0）
- **THEN** 在构建本次请求的 `OpenAiStreamingChatModel` 时设置 `.temperature(1.0)`；若未传则不设置（使用模型默认值）

#### Scenario: MaxTokens 注入 OpenAI 模型
- **WHEN** ChatService 收到 maxTokens 参数（如 8192）
- **THEN** 在构建本次请求的 model 时设置 `.maxTokens(8192)`；若未传则不设置

#### Scenario: TopP 注入 OpenAI 模型
- **WHEN** ChatService 收到 topP 参数（如 0.95）
- **THEN** 在构建本次请求的 model 时设置 `.topP(0.95)`；若未传则不设置

#### Scenario: System Prompt 注入对话上下文
- **WHEN** ChatService 收到非空的 systemPrompt 参数
- **THEN** 将其作为 `SystemMessage` 注入到对话消息列表的首位（在 UserMessage 之前）；若为空字符串或不传则不注入

---

### Requirement: StudioPanel 模型卡片联动
系统 SHALL 使 StudioPanel 的"当前模型"卡片动态显示 MainHeader 模型选择器中选中的模型信息。

#### Scenario: 模型名称联动显示
- **WHEN** 用户在 MainHeader 中切换模型（如从 "auto" 切换到 "deepseek-v4-flash"）
- **THEN** StudioPanel 的模型卡片名称立即更新为对应模型的显示名（如 "DeepSeek V4 Flash"）

#### Scenario: 模型标签动态渲染
- **WHEN** 当前选中模型有预定义标签（如 Qwen 3.7 Max 有标签"复杂推理"、"长文本处理"）
- **THEN** 模型卡片的 tags 区域渲染对应的标签 badge；无标签模型不显示 tag 区域

#### Scenario: 默认模型展示
- **WHEN** 应用首次加载且用户未手动选择模型
- **THEN** 模型卡片显示当前 selectedModel 对应的信息（默认为 auto → "智能路由" 或首个可用模型）
