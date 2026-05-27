## Why

当前 `StudioPanel.vue` 已有参数配置面板的 UI 骨架（温度/最大输出/Top-p 滑块、系统提示文本域、模型选择卡片），但所有参数均为**组件内部局部状态**（`ref`），未与聊天流程联动。用户调整参数后发送消息，参数值不会传递给后端 LLM，导致配置形同虚设。需要将参数从"展示态"升级为"功能态"，实现**前端参数 → 后端 LLM 配置**的全链路打通。

## What Changes

- **前端参数全局化**：将 StudioPanel 中的 temperature / maxOutput / topP / systemPrompt 提升为 App 级别的 provide/inject 状态，使 Composer 发送消息时可读取
- **前端 API 扩展**：`SendChatMessageOptions` 新增 temperature / maxTokens / topP / systemPrompt 字段，`sendChatMessage` 将这些参数追加到 FormData
- **后端接口扩展**：`ChatController.chat()` 新增 `@RequestParam` 接收 temperature / maxTokens / topP / systemPrompt，透传给 `ChatService`
- **后端 LLM 参数注入**：`ChatService.streamChat()` 将参数传递给 LangChain4j 的 `ChatLanguageModel` 构建逻辑（通过动态参数覆盖默认配置）
- **模型选择器联动**：StudioPanel 的模型卡片与 MainHeader 模型选择器共享 `selectedModel` 状态，显示当前选中模型的名称和标签
- **参数持久化**：参数值持久化到 localStorage，页面刷新后恢复上次配置

## Capabilities

### New Capabilities
- `parameter-config`: 聊天参数配置能力，涵盖温度/最大输出/Top-p/systemPrompt 的前端展示、全局状态管理、API 传输及后端 LLM 参数注入

### Modified Capabilities
- `model-selection`: 现有模型选择能力需扩展——StudioPanel 模型卡片需显示当前选中模型名称和标签（而非硬编码 "Astra Sage 4"）

## Impact

**前端影响文件：**
- `src/App.vue` — 新增 provide（chatParams: reactive object）
- `src/components/panels/StudioPanel.vue` — 从局部 ref 改为 inject + watch；模型卡片联动 selectedModel
- `src/components/chat/Composer.vue` — 注入 chatParams，发送时传递参数
- `src/services/api.ts` / `src/services/chat/types.ts` — SendChatMessageOptions 扩展字段
- `src/services/chat/api.ts` — FormData 追加参数字段

**后端影响文件：**
- `controller/ChatController.java` — 新增 @RequestParam 参数
- `service/chat/ChatService.java` — streamChat 方法签名扩展，LLM 参数注入
- `service/factory/AiCodeHelperServiceFactory.java`（可能）— 若 LLM 构建需要感知参数

**无破坏性变更**：所有新增参数均为可选（defaultValue），不影响现有调用方
