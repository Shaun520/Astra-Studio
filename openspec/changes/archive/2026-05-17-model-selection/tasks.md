## 1. 后端核心 - AiCodeHelperServiceFactory 扩展

- [x] 1.1 在 Factory 类中添加模型白名单常量 `ALLOWED_MODELS`，包含三个合法模型名称：`glm-5`、`deepseek-v4-flash`、`qwen3.6-flash-2026-04-16`
- [x] 1.2 修改 `getService()` 方法签名，新增 `String modelName` 参数（默认值 `"glm-5"`），并在入口处校验模型名是否在白名单中，非法则抛出 `IllegalArgumentException`
- [x] 1.3 更新缓存 key 生成逻辑，在现有格式基础上追加 model 维度：`"deepThink:%s,webSearch:%s,model:%s"`
- [x] 1.4 修改 `buildService()` 方法签名，接收 `modelName` 参数，并将其传递给 `createModel()`
- [x] 1.5 修改 `createModel()` 方法签名，接收 `String modelName` 参数替代使用字段 `this.modelName`，在 `.modelName(modelName)` 中使用传入的参数
- [x] 1.6 更新 `defaultAiService()` Bean 方法调用，传递默认模型名 `"glm-5"` 给 `getService()`

## 2. 后端接口 - AiController 参数扩展

- [x] 2.1 在 `chat()` 方法中添加 `@RequestParam(value = "model", defaultValue = "glm-5") String modelName` 参数
- [x] 2.2 更新日志输出，在现有的 `deepThink={}, webSearch={}` 基础上追加 `model={}` 信息
- [x] 2.3 将 `modelName` 参数传递给 `aiServiceFactory.getService(deepThink, webSearch, modelName)` 调用
- [x] 2.4 （可选）为非法模型名的异常添加全局异常处理器或 try-catch 包装，返回友好的 HTTP 400 响应

## 3. 前端接口层 - api.ts 扩展

- [x] 3.1 在 `SendChatMessageOptions` 接口中添加 `model?: string` 可选字段
- [x] 3.2 在 `sendChatMessage()` 函数的 FormData 构建逻辑中，无条件追加 `formData.append('model', options.model || 'glm-5')` 确保始终传递模型参数

## 4. 前端状态管理 - App.vue 参数透传

- [x] 4.1 修改 `handleSend()` 函数签名，添加 `model: string = 'glm-5'` 参数（放在参数列表末尾）
- [x] 4.2 在 `sendChatMessage()` 调用中将 `model` 选项对象属性设为传入的参数值

## 5. 前端 UI 组件 - MainHeader.vue 模型选择器配置

- [x] 5.1 更新 `models` 数组常量，替换为三个目标模型：`{ name: 'GLM-5', desc: '通用对话、多轮交互', tag: '默认' }`、`{ name: 'DeepSeek V4 Flash', desc: '代码生成、逻辑推理', tag: '' }`、`{ name: 'Qwen 3.6 Flash', desc: '中文理解、文本生成', tag: '' }`
- [x] 5.2 修改 `currentModel` 初始值为 `'GLM-5'`（与后端默认模型名一致）
- [x] 5.3 在 `defineEmits`（如果尚未定义）中添加 `(e: 'update:model', value: string): void` 事件声明
- [x] 5.4 修改 `selectModel()` 函数，在更新 `currentModel.value` 后添加 `emit('update:model', name)` 通知父组件
- [x] 5.5 （可选）调整下拉面板底部的版本号文本为实际版本信息，或移除"查看全部"链接（本次不实现多模型页面）
- [x] 5.6 验证 UI 交互：点击按钮展开/收起、选择选项后关闭、点击外部区域关闭、当前项高亮显示

## 6. 编译验证与测试

- [x] 6.1 运行后端编译命令 `mvn compile`，确认 exit code 为 0，无编译错误
- [ ] 6.2 手动测试场景：使用 Postman 或 curl 发送不带 model 参数的请求 → 验证默认使用 glm-5 且正常返回
- [ ] 6.3 手动测试场景：发送带 `model=deepseek-v4-flash` 的请求 → 验证后端日志显示正确的模型名且响应正常
- [ ] 6.4 手动测试场景：发送带非法 model 名（如 `gpt-4`）的请求 → 验证返回 HTTP 400 错误及友好提示信息
- [ ] 6.5 启动前端开发服务器，验证页面加载时 Header 右上角模型选择器显示 "GLM-5"（带绿色状态点）
- [ ] 6.6 点击模型选择器按钮展开下拉列表，验证三个模型选项正确显示（GLM-5 带"默认"标签、其他两个带描述文字）且可点击切换
- [ ] 6.7 选择非默认模型（如 DeepSeek V4 Flash）后发送消息，打开浏览器 DevTools Network 面板 → 验证 FormData 中包含 `model=deepseek-v4-flash` 字段
- [ ] 6.8 组合功能测试：同时启用深度思考 + 联网搜索 + 选择 Qwen 模型 → 验证对话正常工作且后端日志显示完整的三维配置信息
