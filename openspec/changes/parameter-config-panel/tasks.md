## 1. 前端全局状态与持久化

- [x] 1.1 在 `App.vue` 中创建 `chatParams` reactive 对象（temperature/maxOutput/topP/systemPrompt），通过 `provide('chatParams', chatParams)` 注入全局；初始值从 localStorage 恢复，失败则使用默认值
- [x] 1.2 添加 `watch(chatParams, () => { localStorage.setItem('chat-params', JSON.stringify(chatParams)) }, { deep: true })` 实现参数变更自动持久化
- [x] 1.3 扩展 `src/services/chat/types.ts` 中 `SendChatMessageOptions` 接口，新增 `temperature?`, `maxTokens?`, `topP?`, `systemPrompt?` 可选字段

## 2. StudioPanel 参数面板改造

- [x] 2.1 移除 `StudioPanel.vue` 中的局部 `ref`（temperature/maxOutput/topP/systemPrompt/modelTags），改为 `inject('chatParams')` 和 `inject('selectedModel')`
- [x] 2.2 模板中滑块/文本域绑定改为 `chatParams.temperature` / `chatParams.maxOutput` / `chatParams.topP` / `chatParams.systemPrompt`
- [x] 2.3 模型卡片联动：根据 `selectedModel.value` 动态渲染模型名称和标签（维护 modelMeta 映射表：value → { name, tags[] }）；移除硬编码 "Astra Sage 4" 和静态 tags

## 3. Composer 发送参数传递

- [x] 3.1 在 `Composer.vue` 中 `inject('chatParams')` 获取全局参数
- [x] 3.2 修改发送逻辑：在构建 FormData 时追加 `temperature` / `maxTokens` / `topP` / `systemPrompt` 字段（仅当值非默认时传递或始终传递）
- [x] 3.3 同步修改 `src/services/api.ts` 和 `src/services/chat/api.ts` 中 `sendChatMessage` 方法，将新字段写入 FormData

## 4. 后端接口扩展

- [x] 4.1 `ChatController.chat()` 新增 4 个 `@RequestParam`：`temperature`(defaultValue="0.7")、`maxTokens`(defaultValue="4096")、`topP`(defaultValue="0.95")、`systemPrompt`(required=false)
- [x] 4.2 将新增参数透传给 `ChatService.streamChat()` 方法（扩展方法签名）

## 5. 后端 LLM 参数注入

- [x] 5.1 `ChatService.streamChat()` 接收 temperature/maxTokens/topP/systemPrompt 参数
- [x] 5.2 当 temperature/maxTokens/topP 与默认值不同时，调用 `AiCodeHelperServiceFactory` 创建带参数的临时 `OpenAiStreamingChatModel` 实例用于本次请求
- [x] 5.3 当 systemPrompt 非空时，构造 `SystemMessage` 并注入到对话消息列表首位

## 6. 验证与联调

- [ ] 6.1 前端验证：StudioPanel 调整温度 → Composer 发送消息 → Network 面板确认 FormData 包含 temperature 字段
- [ ] 6.2 后端验证：发送带自定义参数的请求 → 确认日志输出参数值 → 确认 LLM 响应体现参数效果
- [ ] 6.3 持久化验证：调整参数 → 刷新页面 → 确认参数值恢复
- [ ] 6.4 模型联动验证：MainHeader 切换模型 → 确认 StudioPanel 模型卡片同步更新
