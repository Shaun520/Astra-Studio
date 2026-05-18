## MODIFIED Requirements

### Requirement: 后端模型参数接收与验证
系统 SHALL 在 `/ai/chat` 接口接收 model 参数并对模型名称进行白名单校验，扩展支持 `"auto"` 值以启用自动路由功能。

#### Scenario: 接收合法模型名称
- **WHEN** 后端收到请求且 `model` 参数值为 `glm-5` 或 `deepseek-v4-flash` 或 `qwen3.6-flash-2026-04-16`
- **THEN** 系统接受该参数并继续处理请求（行为不变）

#### Scenario: 接收 auto 模式触发自动路由
- **WHEN** 后端收到请求且 `model` 参数值为 `"auto"`
- **THEN** 系统识别该值为自动路由模式标记，不进行传统的白名单校验，而是将控制权转交给 AutoRoutingService 进行意图分类和模型选择

#### Scenario: 使用默认值处理缺失参数
- **WHEN** 后端收到请求但未包含 `model` 参数（旧版客户端或测试工具）
- **THEN** 系统使用默认值 `glm-5` 作为模型名称，不影响正常处理（行为不变）

#### Scenario: 拒绝非法模型名称（扩展）
- **WHEN** 后端收到请求且 `model` 参数值不在白名单中且不为 `"auto"`（如 `gpt-4` 或 `unknown-model`）
- **THEN** 系统立即返回 HTTP 400 Bad Request 错误，错误信息明确提示支持的模型列表和 auto 选项

---

### Requirement: 前端模型参数传递
系统 SHALL 在用户发送消息时将当前选中的模型名称作为 model 参数传递给后端 API，扩展支持传递 "auto" 值。

#### Scenario: 发送消息时包含模型参数（手动模式）
- **WHEN** 用户在已选择具体模型（如 "DeepSeek V4 Flash"）的情况下点击发送按钮
- **THEN** 前端构建 FormData 时包含 `model` 字段，值为 `deepseek-v4-flash`（行为不变）

#### Scenario: 发送消息时包含 auto 参数（自动路由模式）
- **WHEN** 用户在已选择"🤖 自动"选项的情况下点击发送按钮
- **THEN** 前端构建 FormData 时包含 `model` 字段，值为 `auto`

#### Scenario: 使用默认模型发送
- **WHEN** 用户未手动切换模型（保持默认 "GLM-5"）并发送消息
- **THEN** 前端仍然显式传递 `model` 字段，值为 `glm-5`（行为不变）
