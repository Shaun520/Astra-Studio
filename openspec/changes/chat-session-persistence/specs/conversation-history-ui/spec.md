## ADDED Requirements

### Requirement: 侧边栏会话列表组件
系统 SHALL 在 AppSidebar.vue 的"最近"Tab 中展示真实的历史会话列表。

#### Scenario: 初始加载展示
- **WHEN** 用户打开应用且 AppSidebar 组件挂载（onMounted）
- **THEN** 组件调用 `getConversations()` API 获取最近 20 条会话
- **AND** 渲染为垂直列表，每项包含：
  - 左侧图标：根据 modelName 显示对应模型标识（如 DeepSeek/Qwen/GLM 图标）
  - 主标题：conversations.title（单行截断，超出显示省略号）
  - 副标题：last_message_preview（灰色小字，双行截断）
  - 右侧时间：相对时间格式（如"2小时前"、"昨天"）
- **AND** 当前活跃会话高亮显示（背景色 + 左边框强调）

#### Scenario: 点击切换会话
- **WHEN** 用户点击某个历史会话项
- **THEN** 调用 `restoreConversation(memoryId)` 接口恢复上下文
- **AND** 清空当前聊天区域消息列表
- **AND** 重新渲染恢复的消息（支持 Markdown + 思维链折叠）
- **AND** 更新 App.vue 的 currentSessionId 状态

#### Scenario: 空状态引导
- **WHEN** 会话列表为空（新用户或全部已删除）
- **THEN** 显示空状态占位组件：
  - 图标：MessageSquare + 渐变透明度效果
  - 提示文案："还没有对话记录"
  - 操作按钮："开始第一个对话"（点击聚焦输入框）

#### Scenario: 加载状态反馈
- **WHEN** API 请求进行中（isLoading=true）
- **THEN** 显示骨架屏加载动画（3 行 placeholder shimmer 效果）
- **AND** 阻止重复点击

---

### Requirement: 会话搜索与过滤功能
系统 SHALL 在会话列表顶部提供搜索框，支持实时关键词过滤。

#### Scenario: 输入关键词即时过滤
- **WHEN** 用户在搜索框输入"排序"
- **THEN** 使用防抖（debounce=300ms）避免频繁请求
- **AND** 调用 `getConversations(keyword="排序")` 后端接口
- **AND** 列表实时更新为匹配结果

#### Scenario: 清空搜索框
- **WHEN** 用户清空搜索框内容
- **THEN** 重置为完整会话列表（重新调用无 keyword 参数的接口）

#### Scenario: 无搜索结果提示
- **WHEN** 关键词匹配结果为空
- **THEN** 显示"未找到相关对话"提示文案
- **AND** 提供"清除筛选"按钮

---

### Requirement: 会话右键上下文菜单
系统 SHALL 为每个会话项提供右键菜单，支持快捷操作。

#### Scenario: 右键打开菜单
- **WHEN** 用户右键点击某个会话项
- **THEN** 显示浮动菜单（绝对定位，z-index 最高层），包含：
  - ✏️ 重命名：弹出 inline input 编辑标题
  - 📌 固定到收藏（灰显，标注"即将上线"）
  - 🗑️ 删除：弹出确认对话框

#### Scenario: 内联编辑标题
- **WHEN** 用户选择"重命名"选项
- **THEN** 该会话项的标题变为可编辑 input（auto-focus）
- **AND** 按 Enter 键确认调用 updateConversationTitle() API
- **AND** 按 Escape 取消编辑，恢复原标题

#### Scenario: 删除确认对话框
- **WHEN** 用户选择"删除"选项
- **THEN** 弹出模态对话框：
  - 标题："确认删除？"
  - 内容："删除后的对话将在 30 天后彻底清除，期间可联系管理员恢复。"
  - 按钮："取消"（次要样式）/ "确认删除"（危险色红色）
- **AND** 点击"确认删除"调用 deleteConversation() API
- **AND** 删除成功后从列表中移除该项（前端乐观删除 + 后端确认）

---

### Requirement: 新建会话与标题实时更新
系统 SHALL 在新建对话时正确初始化 UI 状态，并在标题生成后无缝刷新。

#### Scenario: 点击"新建对话"按钮
- **WHEN** 用户点击 Composer 区域的"新建对话"按钮
- **THEN** 清空当前聊天消息列表
- **AND** 生成新的 session ID（UUID v4 格式）
- **AND** 侧边栏列表顶部插入一项：
  - 标题："新对话..."（闪烁光标动画表示正在生成标题）
  - 时间："刚刚"

#### Scenario: 标题生成完成刷新
- **WHEN** 后台异步完成标题生成（LLM 或规则提取）
- **THEN** 通过事件总线（emit/inject）通知侧边栏更新
- **AND** "新对话..." 替换为真实标题
- **AND** 移除闪烁动画

#### Scenario: 页面刷新后恢复当前会话
- **WHEN** 用户按 F5 刷新页面
- **THEN** 应用启动时检查 localStorage 中的 lastSessionId
- **AND** 若存在则调用 restoreConversation(lastSessionId) 恢复
- **AND** 高亮对应的侧边栏会话项
