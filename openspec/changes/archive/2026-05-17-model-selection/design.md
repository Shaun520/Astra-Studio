## Context

### 当前状态
系统已实现深度思考（v1.0）和联网搜索（v2.0）功能，采用动态构建器架构管理 AI 服务实例。当前 AiCodeHelperServiceFactory 通过 `ConcurrentHashMap` 缓存服务实例，缓存 key 为 `(deepThink, webSearch)` 组合。模型名称硬编码在 `application.yaml` 的 `langchain4j.open-ai.streaming-chat-model.model-name` 配置项中，Factory 类通过 `@Value` 注入该字段并在 `createModel()` 方法中使用。

### 约束条件
- **向后兼容**：不传 model 参数时必须使用默认模型（glm-5），不影响现有功能
- **参数正交性**：model 参数与 deepThink、webSearch 参数完全独立，支持任意组合
- **性能要求**：新增模型维度不应显著增加缓存内存占用或降低响应速度
- **UI 限制**：模型选择器应位于 MainHeader.vue 组件的右上角区域（在"+"按钮和分享按钮之间），复用现有的下拉框交互模式，保持视觉一致性
- **LangChain4j API**：必须使用 OpenAI 兼容接口，所有目标模型均通过 DashScope API 网关访问

### 利益相关者
- **终端用户**：需要在不同场景下选择合适的 AI 模型
- **前端开发**：负责 UI 组件实现和参数传递
- **后端开发**：负责 Factory 扩展和动态模型路由
- **运维人员**：可能需要监控不同模型的使用情况和性能指标

## Goals / Non-Goals

**Goals:**
- 用户可以在对话前从预定义列表中选择 AI 模型
- 选中的模型在后续对话请求中被正确传递和使用
- 支持三个模型：glm-5（默认）、deepseek-v4-flash、qwen3.6-flash-2026-04-16
- 模型选择与 deepThink、webSearch 功能正交组合工作
- 前端 UI 直观易用，位于会话窗口右上角 Header 区域，采用下拉框样式（带模型名称+状态指示点）
- 后端利用现有动态构建器架构，最小化代码改动

**Non-Goals:**
- 不实现模型的运行时热加载/配置刷新（需重启生效）
- 不实现用户级别的模型偏好持久化（本次仅会话级选择）
- 不实现模型对比/A/B 测试框架
- 不实现基于内容自动推荐模型的功能
- 不修改 SSE 流式响应的数据结构（model 信息不在返回值中体现）
- 不实现模型使用量统计或计费功能

## Decisions

### 决策 1：前端 UI 实现方案 - 下拉选择框 vs 按钮组

**选择**：下拉选择框（Select Dropdown）

**理由**：

- ✅ 节省空间：3个模型如果用按钮组会占用较多水平空间，可能挤压输入框
- ✅ 可扩展：未来添加新模型只需更新列表数据，无需调整布局
- ✅ 符合惯例：模型选择是标准配置类操作，下拉框是常见模式
- ✅ 可显示更多信息：如模型名称 + 适用场景提示

**替代方案考虑**：
- ❌ 按钮组（类似 deepThink/webSearch 开关）：占用空间大，3个以上选项时不适用
- ❌ 弹窗/侧边栏：交互成本高，频繁切换不便
- ❌ 设置页面：脱离对话上下文，用户体验差

**实现细节**：

- **位置**：MainHeader.vue 组件的右上角 Header 区域，位于"新会话"(+)按钮和"分享"按钮之间
- **UI组件**：复用现有的 `pill-btn` 样式（带边框的圆角按钮），包含三部分：
  - 左侧：绿色状态指示点（表示模型在线可用）
  - 中间：当前模型显示名称（如 "GLM-5" 或 "DeepSeek V4 Flash"）
  - 右侧：ChevronDown 下拉箭头图标（点击时旋转180度）
- **下拉面板**：点击按钮后展开的下拉菜单（宽度260px），包含：
  - 模型列表（每个选项包含：模型名称、描述文字、可选标签如"默认"）
  - 当前选中项高亮显示（浅蓝色背景 + 右侧 Check 图标）
  - 底部信息栏（模型版本号 + "查看全部"链接）
- **交互模式**：
  - 点击按钮切换下拉面板显隐（带过渡动画）
  - 点击外部区域自动关闭所有下拉面板
  - 选择模型后立即关闭面板并更新显示名称
- **数据流设计**：
  - MainHeader.vue 维护 `currentModel` 响应式状态
  - 选择变化时通过 `emit('update:model', modelName)` 通知父组件 App.vue
  - App.vue 接收后将 model 值传递给 Composer.vue 的 handleSend() 和 api.ts

**已有代码基础**：
MainHeader.vue 已实现完整的 UI 框架（第10-30行定义了 models 数组和 selectModel 函数），但当前使用的是示例数据（Astra Sage 4等），需要替换为实际的3个目标模型并连接到后端 API。

**参考截图位置**：
![image-20260517163136481](C:\Users\23165\AppData\Roaming\Typora\typora-user-images\image-20260517163136481.png)

---

### 决策 2：后端模型路由策略 - 扩展缓存 key vs 多工厂实例

**选择**：扩展缓存 key 维度（在现有 `(deepThink, webSearch)` 基础上追加 model）

**理由**：

- ✅ 复用现有架构：无需引入新的抽象层或设计模式
- ✅ 自动支持组合：`(deepThink, webSearch, model)` 三维组合自动生成 2×2×3=12 种缓存实例
- ✅ 代码改动最小：只需修改 `getService()` 和 `buildService()` 方法签名
- ✅ 一致性：与 v2.0 架构理念完全契合（动态构建器+缓存）

**替代方案考虑**：
- ❌ 为每个模型创建独立 Factory Bean：回归 v1.0 的静态实例模式，违反开闭原则
- ❌ 在 Controller 层做模型字符串替换：绕过 Factory 缓存机制，每次都创建新实例（性能差）
- ❌ 使用 Map<modelName, Factory> 映射：过度设计，增加不必要的复杂度

**缓存 key 格式**：
```
旧: "deepThink:false,webSearch:true"
新: "deepThink:false,webSearch:true,model:deepseek-v4-flash"
```

---

### 决策 3：模型名称传递方式 - 路径参数 vs 查询参数 vs 表单字段

**选择**：表单字段（@RequestParam），与 deepThink/webSearch 保持一致

**理由**：
- ✅ 一致性：现有功能都使用 FormData + @RequestParam，保持统一风格
- ✅ 安全性：模型名称在 request body 中，不会出现在日志/代理的 URL 中
- ✅ 兼容 multipart/form-data：接口已是该 content-type，自然扩展

**替代方案考虑**：
- ❌ URL 查询参数 (?model=xxx)：暴露在日志中，且不符合 RESTful 资源定位语义
- ❌ Path variable (/ai/chat/{model})：模型不是资源标识符，而是请求配置
- ❌ Request Header (X-Model-Name)：非标准用法，增加前端复杂度

---

### 决策 4：默认模型处理策略 - 前端必传 vs 后端兜底

**选择**：双重保障 - 前端默认选中 glm-5，后端 @RequestParam defaultValue="glm-5"

**理由**：

- ✅ 防御性编程：即使前端漏传参数，后端也能正常工作
- ✅ 向后兼容：旧版客户端（不传 model 字段）仍可正常调用
- ✅ 明确意图：前端显式发送默认值，日志清晰可见

**实现细节**：
```java
@RequestParam(value = "model", defaultValue = "glm-5") String modelName
```
```typescript
const selectedModel = ref('glm-5')  // 前端默认值
```

---

### 决策 5：模型配置来源 - 硬编码 vs 配置文件 vs 数据库

**选择**：前端硬编码列表 + 后端验证白名单（本次迭代）

**理由**：
- ✅ 简单直接：只有3个固定模型，无需动态加载机制
- ✅ 类型安全：TypeScript 编译期检查拼写错误
- ✅ 易于维护：修改模型列表只需改一处代码

**未来演进路径**（如果需要动态模型列表）：
- Phase 2: 后端提供 `/api/models` 接口，前端动态获取
- Phase 3: 数据库存储模型配置，支持运行时启用/禁用

**安全措施**：
- 后端对 model 参数进行白名单校验，防止传入非法模型名导致异常
- 白名单定义在常量类或枚举中，便于集中管理

---

### 决策 6：超时时间是否按模型差异化

**选择**：本次不做差异化（统一使用现有的 calculateTimeout 逻辑）

**理由**：
- ✅ 避免过度设计：三种模型的响应时间差异不大（都是 flash 版本）
- ✅ 保持简单：现有 timeout 计算逻辑已经足够合理
- ✅ 降低风险：减少变量因素，便于问题排查

**未来优化方向**：
- 如果发现某模型明显慢于其他模型，可以在 `calculateTimeout()` 中添加模型维度的权重
- 例如：`if ("qwen3.6".equals(model)) baseTimeout += 10;`

---

## Risks / Trade-offs

### 风险 1：缓存实例数量膨胀

**描述**：从 2×2=4 个缓存实例增长到 2×2×3=12 个，内存占用增加约 3 倍

**影响评估**：

- 每个 AiCodeHelperService 实例约占用几 MB（包含 ChatModel、Memory 等）
- 12 个实例总计约几十 MB，对于 JVM 堆内存（通常 512MB+）可忽略不计
- 实际场景下不会所有组合都被用到（例如 deepThink+webSearch 可能较少使用）

**缓解措施**：
- ✅ ConcurrentHashMap 是懒加载的，只在首次请求时创建
- ✅ 可添加 LRU 淘汰策略（如果未来模型数 > 10）
- ✅ 监控缓存大小，设置告警阈值

**权衡**：接受轻微内存开销换取架构简洁性和扩展性

---

### 风险 2：模型名称拼写错误

**描述**：前端或客户端传入错误的模型名称（如 "deepseek-v4" 漏掉 "-flash"）

**影响**：
- 后端会尝试创建不存在模型的 ChatModel 实例
- LangChain4j 在调用 API 时才报错（404 Model Not Found）
- 错误信息不够友好，用户体验差

**缓解措施**：
- ✅ 后端白名单校验：在 `getService()` 入口处检查 modelName 是否合法
- ✅ 提前失败：非法模型名立即返回 400 错误，而非等到 API 调用时
- ✅ 日志记录：记录非法请求便于排查问题

**示例代码**：
```java
private static final Set<String> ALLOWED_MODELS = Set.of(
    "glm-5", 
    "deepseek-v4-flash", 
    "qwen3.6-flash-2026-04-16"
);

public AiCodeHelperService getService(boolean deepThink, boolean webSearch, String modelName) {
    if (!ALLOWED_MODELS.contains(modelName)) {
        throw new IllegalArgumentException("Unsupported model: " + modelName);
    }
    // ... 正常逻辑
}
```

---

### 风险 3：前端状态同步问题

**描述**：用户选择了模型 A，但在发送消息前又切换到模型 B，最终应该使用哪个？

**影响**：

- 如果读取时机不对，可能导致用户意图与实际使用的模型不一致

**缓解措施**：
- ✅ 在 `handleSend()` 调用时读取 `selectedModel.value`（最新状态）
- ✅ 不使用闭包捕获旧值，确保每次发送都取当前选中值
- ✅ 可选：在输入框上方显示当前选中模型名称作为视觉反馈

**权衡**：这是标准的 Reactivity 问题，Vue 3 的 ref 机制天然解决

---

### 权衡 1：代码简洁 vs 类型安全

**决策**：优先代码简洁（使用字符串而非枚举）

**理由**：

- 模型名称需要序列化传输（FormData → HTTP → Java String）
- 引入枚举需要在前后端分别定义并保持同步，增加维护成本
- TypeScript 的 string literal types 可以提供足够的类型检查

**妥协**：

- 后端使用常量集 `Set<String>` 进行运行时校验
- 前端使用 `as const` 断言提供编译时类型推断

---

## Migration Plan

### 部署步骤

**Phase 1: 后端部署（零停机滚动升级）**

1. 部署包含新代码的后端 JAR 包
2. 新版本启动后，旧的缓存 key 格式失效（包含 model 维度的新 key 生效）
3. 首次请求会触发新的 Service 实例创建（约 1-2 秒初始化时间）
4. 后续请求命中缓存，无额外开销

**影响范围**：
- 已有在线连接不受影响（SSE 连接在请求级别隔离）
- 新连接自动使用新代码路径

**Phase 2: 前端部署**

1. 部署包含模型选择器的新前端 build产物
2. 用户刷新页面后看到新的 UI 组件
3. 旧版本前端不传 model 参数，后端使用默认值兼容

**回滚策略**：

- **后端回滚**：恢复旧 JAR 包，缓存 key 格式还原，无缝回滚
- **前端回滚**：恢复旧 build，模型选择器消失，行为等同于未上线前
- **数据一致性**：无数据库变更，无迁移脚本需求，回滚无数据丢失风险

**验证清单**：

- [ ] 后端编译通过：`mvn compile` exit code 0
- [ ] 后端单元测试通过（如有）
- [ ] 手动测试：不带 model 参数 → 默认 glm-5
- [ ] 手动测试：带 model=deepseek-v4-flash → 使用 DeepSeek 模型
- [ ] 手动测试：带非法 model 名 → 返回 400 错误
- [ ] 前端 UI 显示：下拉框出现，3个选项可选
- [ ] 组合测试：deepThink + webSearch + model 同时启用
- [ ] 性能测试：首次请求延迟 < 3s（冷启动），后续 < 500ms（缓存命中）

---

## Open Questions

1. **模型列表是否需要国际化？**
   - 当前决定：只支持中文显示（目标用户群为中文用户）
   - 未来可选：如果需要英文界面，提取 i18n 配置文件

2. **是否需要记住用户的模型偏好？**
   - 当前决定：否（会话级选择，刷新页面重置为默认）
   - 未来可选：localStorage 持久化或后端 user-preferences 表

3. **是否需要在回复中显示使用了哪个模型？**
   - 当前决定：否（避免信息过载）
   - 未来可选：在消息元数据区域显示小标签 "Powered by DeepSeek"

4. **模型选择器的位置是否最优？**
   - 当前决定：放在工具栏左侧（深度思考按钮左边）
   - 待用户测试反馈：可能需要 A/B 测试不同位置的效果
