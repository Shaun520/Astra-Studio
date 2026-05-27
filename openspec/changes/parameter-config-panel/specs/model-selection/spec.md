## MODIFIED Requirements

### Requirement: 模型选择器（扩展至 StudioPanel 联动）
系统 SHALL 在顶部导航栏提供模型下拉选择器，允许用户切换 AI 模型。选中状态 SHALL 同步至 StudioPanel 参数配置面板的模型卡片展示。

#### Scenario: 显示可用模型列表
- **WHEN** 用户点击顶部导航栏的模型选择器下拉按钮
- **THEN** 系统展示所有可用模型选项，每项包含：模型图标、模型名称、简短描述、可选标签（推荐/默认）

#### Scenario: 切换模型
- **WHEN** 用户从下拉列表中选择一个模型
- **THEN** 系统更新 `selectedModel` 为该模型的 value（API 名称），后续所有聊天请求使用新模型；**同时 StudioPanel 参数配置面板中的模型卡片更新为该模型的显示名称和标签**

#### Scenario: 默认模型与智能路由
- **WHEN** 应用初始化或新建会话时
- **THEN** 默认选中 "auto"（智能路由模式），由后端根据任务类型自动选择最优模型；StudioPanel 模型卡片显示 "智能路由" 作为名称

> ⚠️ **变更说明**：原 spec 仅描述 MainHeader 内的选择器行为，现扩展要求选中状态同步至 StudioPanel 模型卡片（provide/inject 联动）。
