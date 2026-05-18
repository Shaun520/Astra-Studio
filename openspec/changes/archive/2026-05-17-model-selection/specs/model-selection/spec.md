## ADDED Requirements

### Requirement: 前端模型选择器 UI 展示
系统 SHALL 在 MainHeader.vue 组件的右上角 Header 区域提供模型选择下拉按钮，显示预定义的可用模型列表供用户选择。

#### Scenario: 显示默认选中模型
- **WHEN** 用户打开对话页面或刷新浏览器
- **THEN** 模型选择器按钮显示 "GLM-5" 作为当前选中项（带绿色状态指示点）

#### Scenario: 显示完整模型列表
- **WHEN** 用户点击模型选择器按钮
- **THEN** 下拉面板展示所有可用模型选项，包括：
  - "GLM-5" - 通用对话、多轮交互（带"默认"标签）
  - "DeepSeek V4 Flash" - 代码生成、逻辑推理、数学计算
  - "Qwen 3.6 Flash" - 中文理解、文本生成、知识问答

#### Scenario: 切换模型选择
- **WHEN** 用户在下拉面板中点击某个模型选项
- **THEN** 面板关闭并更新按钮显示为新选中的模型名称，该模型成为后续对话的默认模型，同时触发 `update:model` 事件通知父组件

---

### Requirement: 前端模型参数传递
系统 SHALL 在用户发送消息时将当前选中的模型名称作为 model 参数传递给后端 API。

#### Scenario: 发送消息时包含模型参数
- **WHEN** 用户在已选择模型（如 "DeepSeek V4 Flash"）的情况下点击发送按钮
- **THEN** 前端构建 FormData 时包含 `model` 字段，值为 `deepseek-v4-flash`

#### Scenario: 使用默认模型发送
- **WHEN** 用户未手动切换模型（保持默认 "GLM-5"）并发送消息
- **THEN** 前端仍然显式传递 `model` 字段，值为 `glm-5`

#### Scenario: 多次切换后发送取最新值
- **WHEN** 用户依次选择模型 A → 模型 B → 模型 C，然后发送消息
- **THEN** 请求中使用的是最终选中的模型 C 的名称，而非中间状态

---

### Requirement: 后端模型参数接收与验证
系统 SHALL 在 `/ai/chat` 接口接收 model 参数并对模型名称进行白名单校验。

#### Scenario: 接收合法模型名称
- **WHEN** 后端收到请求且 `model` 参数值为 `glm-5` 或 `deepseek-v4-flash` 或 `qwen3.6-flash-2026-04-16`
- **THEN** 系统接受该参数并继续处理请求

#### Scenario: 使用默认值处理缺失参数
- **WHEN** 后端收到请求但未包含 `model` 参数（旧版客户端或测试工具）
- **THEN** 系统使用默认值 `glm-5` 作为模型名称，不影响正常处理

#### Scenario: 拒绝非法模型名称
- **WHEN** 后端收到请求且 `model` 参数值不在白名单中（如 `gpt-4` 或 `unknown-model`）
- **THEN** 系统立即返回 HTTP 400 Bad Request 错误，错误信息明确提示支持的模型列表

---

### Requirement: 动态模型服务实例创建与缓存
系统 SHALL 根据请求的 deepThink、webSearch、model 参数组合动态创建 AI 服务实例，并使用 ConcurrentHashMap 缓存以避免重复创建。

#### Scenario: 首次请求特定组合时创建实例
- **WHEN** 系统首次收到 `(deepThink=false, webSearch=true, model=deepseek-v4-flash)` 组合的请求
- **THEN** Factory 创建新的 AiCodeHelperService 实例（使用 DeepSeek 模型 + 联网搜索工具），并存入缓存，日志记录 "🏭 Creating new AI service with config: ..."

#### Scenario: 相同组合重复请求命中缓存
- **WHEN** 系统再次收到相同参数组合的请求（缓存 key 匹配）
- **THEN** 直接返回缓存的 Service 实例，不执行创建逻辑，无额外日志输出

#### Scenario: 缓存 key 包含三维信息
- **WHEN** 系统构建缓存 key
- **THEN** key 格式为 `"deepThink:X,webSearch:X,model:XXX"`，确保不同模型不会共享同一 Service 实例

---

### Requirement: 模型正交组合支持
系统 SHALL 支持 model 参数与 deepThink、webSearch 参数的正交组合，任意功能开关与模型选择的组合都能正常工作。

#### Scenario: 仅选择模型（不启用其他功能）
- **WHEN** 用户选择 `deepseek-v4-flash` 且 deepThink 和 webSearch 都为 false
- **THEN** 系统使用 DeepSeek 模型的普通模式服务实例进行对话

#### Scenario: 深度思考 + 特定模型
- **WHEN** 用户启用深度思考并选择 `qwen3.6-flash-2026-04-16`
- **THEN** 系统使用 Qwen 模型的深度思考模式服务实例（returnThinking=true），流式返回思维链内容

#### Scenario: 联网搜索 + 特定模型
- **WHEN** 用户启用联网搜索并选择 `glm-5`
- **THEN** 系统使用 GLM-5 模型的联网搜索服务实例（集成 McpToolProvider），AI 可调用搜索工具

#### Scenario: 全部功能 + 特定模型
- **WHEN** 用户同时启用深度思考、联网搜索并选择 `deepseek-v4-flash`
- **THEN** 系统使用 DeepSeek 模型的全功能服务实例（思维链 + 联网工具），超时时间自动调整为 75 秒（30+30+15）

---

### Requirement: 向后兼容性保障
系统 SHALL 保证在不传递 model 参数的情况下行为与上线前完全一致，确保旧版本客户端无缝迁移。

#### Scenario: 不传 model 参数时的默认行为
- **WHEN** 请求中不包含 model 字段（模拟旧版前端）
- **THEN** 系统使用 glm-5 模型，响应格式和内容与升级前完全一致

#### Scenario: 已有功能不受影响
- **WHEN** 只传 deepThink 和 webSearch 参数而不传 model
- **THEN** 深度思考和联网搜索功能正常工作，表现与 v2.0 版本一致

#### Scenario: 缓存 key 变更的冷启动影响
- **WHEN** 系统升级后首次收到任何请求（无论是否带 model 参数）
- **THEN** 由于缓存 key 格式变更（新增 model 维度），会触发新的 Service 实例创建，首次请求可能增加 1-2 秒初始化延迟，后续请求恢复正常速度
