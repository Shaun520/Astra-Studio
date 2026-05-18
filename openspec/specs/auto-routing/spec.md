## ADDED Requirements

### Requirement: 自动路由触发机制
系统 SHALL 在 `/ai/chat` 接口中支持自动路由模式，当且仅当请求参数 `model` 的值为 `"auto"` 时启用意图识别和智能模型选择。

#### Scenario: 显式启用自动路由
- **WHEN** 前端发送请求时 `model` 参数值为 `"auto"`
- **THEN** 系统调用 AutoRoutingService 执行意图分类和路由决策，返回的模型名称由路由算法决定

#### Scenario: 手动指定模型时不触发自动路由
- **WHEN** 前端发送请求时 `model` 参数值为具体模型名（如 `"deepseek-v4-flash"`）
- **THEN** 系统跳过自动路由逻辑，直接使用用户指定的模型（行为与当前版本完全一致）

#### Scenario: 不传 model 参数时的兼容性处理
- **WHEN** 前端发送请求时不包含 `model` 参数（旧版客户端或未集成新功能）
- **THEN** 系统使用默认值 `"glm-5"` 作为模型名称，不触发自动路由，确保向后兼容

---

### Requirement: 意图分类器核心功能
系统 SHALL 实现 IntentClassifier 组件，基于关键词匹配和正则表达式模式识别用户输入的任务类型。

#### Scenario: 代码生成意图识别
- **WHEN** 用户输入包含代码相关关键词（如"写代码"、"编程"、"debug"、"Python"、"算法"、"帮我写个函数"等）
- **THEN** 分类器返回意图类型为 `CODE_GENERATION`，置信度 ≥ 0.8

#### Scenario: 中文问答意图识别
- **WHEN** 用户输入包含中文理解相关关键词（如"解释"、"什么是"、"翻译"、"总结"、"历史"、"文化"等）或匹配解释类句式（如"解释一下XXX是什么"）
- **THEN** 分类器返回意图类型为 `CHINESE_QA`，置信度 ≥ 0.8

#### Scenario: 通用对话兜底分类
- **WHEN** 用户输入无法匹配任何特定意图的关键词或模式（如闲聊"你好"、模糊查询"今天天气怎么样"）
- **THEN** 分类器返回意图类型为 `GENERAL_CHAT`，置信度为默认值（通常较低）

#### Scenario: 多信号综合评分
- **WHEN** 用户输入同时命中多个意图的部分特征（如"用Python解释快速排序算法"同时包含编程语言+概念解释）
- **THEN** 分类器计算各意图的综合得分（关键词得分 × 权重），选择得分最高的意图作为最终结果

---

### Requirement: 置信度阈值与安全回退
系统 SHALL 对分类结果进行置信度评估，当置信度低于配置阈值时回退到默认模型以确保服务质量。

#### Scenario: 高置信度正常路由
- **WHEN** 分类器对某意图的置信度 ≥ 配置的阈值（默认 0.6）
- **THEN** 系统按照该意图对应的目标模型进行路由（如 CODE_GENERATION → deepseek-v4-flash）

#### Scenario: 低置信度安全回退
- **WHEN** 分类器对所有意图的置信度都 < 配置阈值（如输入过于简短"嗯"或含糊不清"那个东西"）
- **THEN** 系统放弃自动路由结果，使用默认模型 `glm-5` 处理请求，并在日志中记录 WARN 级别的回退信息

#### Scenario: 可配置的阈值调整
- **WHEN** 运维人员修改 `application.yaml` 中的 `auto-routing.confidence-threshold` 配置项（如从 0.6 调整为 0.7）
- **THEN** 系统在下次请求时立即生效（无需重启），使用新的阈值进行置信度判断

---

### Requirement: 模型路由映射规则
系统 SHALL 根据意图分类结果映射到预定义的目标模型，遵循"专业任务用专业模型"的原则。

#### Scenario: 代码生成任务路由到 DeepSeek
- **WHEN** 意图分类结果为 `CODE_GENERATION`
- **THEN** 系统选择 `deepseek-v4-flash` 作为目标模型（理由：DeepSeek 在代码生成、逻辑推理、数学计算方面表现最优）

#### Scenario: 中文问答任务路由到 Qwen
- **WHEN** 意图分类结果为 `CHINESE_QA`
- **THEN** 系统选择 `qwen3.6-flash-2026-04-16` 作为目标模型（理由：Qwen 在中文理解、文本生成、知识问答方面有优化）

#### Scenario: 通用对话任务路由到 GLM-5
- **WHEN** 意图分类结果为 `GENERAL_CHAT`
- **THEN** 系统选择 `glm-5` 作为目标模型（理由：GLM-5 是通用对话的默认最佳选择）

#### Scenario: 路由决策完整性记录
- **WHEN** 系统完成一次路由决策（无论成功还是回退）
- **THEN** 记录完整的决策元数据：选中的模型名称、是否自动路由、置信度数值、路由原因说明（如"检测到关键词：写代码"）

---

### Requirement: SSE 响应中的路由信息推送
系统 SHALL 在流式响应的开始阶段向前端推送路由决策信息，供 UI 展示和用户确认。

#### Scenario: 成功自动路由时的信息推送
- **WHEN** 自动路由成功完成（置信度 ≥ 阈值）并选择了目标模型
- **THEN** 在 TokenStream 开始前，SSE 推送一个 `type: "routing_info"` 的事件，数据包含：
  - `model`: 选中的模型名称（如 "deepseek-v4-flash"）
  - `isAutoRouted`: true
  - `reason`: 路由原因（如 "检测到关键词：写代码, Python"）
  - `confidence`: 置信度数值（如 0.92）

#### Scenario: 手动模式或回退时的信息推送
- **WHEN** 请求使用手动指定的模型或触发了低置信度回退
- **THEN** 同样推送 `routing_info` 事件，但 `isAutoRouted` 为 false，`reason` 说明实际原因（如 "用户手动选择" 或 "置信度过低，使用默认模型"）

#### Scenario: 前端解析路由信息事件
- **WHEN** 前端接收到 `routing_info` 类型的事件
- **THEN** 解析并在聊天界面展示提示信息（如 "🤖 已为您自动选择 DeepSeek V4 Flash（检测到代码相关内容）"），展示时长 3-5 秒后自动消失

---

### Requirement: 路由统计监控接口
系统 SHALL 提供 `GET /api/ai/routing-stats` 接口用于查询自动路由的运行统计数据。

#### Scenario: 查询累计统计数据
- **WHEN** 运维人员或开发人员调用 `GET /api/ai/routing-stats`
- **THEN** 系统返回 JSON 格式的统计快照，包含：
  - `totalRequests`: 总请求数（自上次重启以来）
  - `autoRoutedCount`: 成功自动路由的次数
  - `manualOverrideCount`: 用户手动指定模型的次数
  - `fallbackCount`: 低置信度回退次数
  - `intentDistribution`: 各意图的分布（`{CODE_GENERATION: 300, CHINESE_QA: 150, GENERAL_CHAT: 550}`）
  - `modelUsage`: 各模型被路由到的次数（`{deepseek-v4-flash: 280, qwen3.6-flash: 140, glm-5: 580}`）
  - `averageConfidence`: 所有自动路由的平均置信度（0-1之间的小数）
  - `timestamp`: 数据快照时间戳

#### Scenario: 实时数据更新
- **WHEN** 有新的请求经过路由处理
- **THEN** 统计数据立即更新（内存中的原子操作），下次查询接口时反映最新值

#### Scenario: 重启后数据重置
- **WHEN** 应用服务重启
- **THEN** 所有统计计数器归零（符合内存存储的设计），时间戳更新为重启时间

---

### Requirement: 可配置的分类规则管理
系统 SHALL 支持通过 YAML 配置文件定义和管理意图分类规则，便于非技术人员调整和优化。

#### Scenario: 规则文件加载与解析
- **WHEN** 应用启动时
- **THEN** IntentClassifier 从 classpath 下的 `intent-rules.yaml` 文件加载规则定义，包括各意图的关键词列表、正则模式、权重和目标模型映射

#### Scenario: 规则结构定义
- **WHEN** 编辑 `intent-rules.yaml` 文件
- **THEN** 文件遵循以下结构：
  ```yaml
  intents:
    <INTENT_NAME>:
      keywords:
        - ["关键词组1", "关键词组2"]  # 或运算：命中任一组即算匹配
        - ["关键词组3"]
      patterns:
        - "正则表达式1"
        - "正则表达式2"
      weight: 0.9  # 该意图的权重系数（0-1）
      target_model: "<MODEL_NAME>"  # 路由目标模型
  ```

#### Scenario: 规则热更新（可选增强）
- **WHEN** 运维人员修改了 `intent-rules.yaml` 文件（如新增关键词或调整权重）
- **THEN** 若启用了 `auto-routing.rules-hot-reload: true` 配置，系统在 60 秒内自动检测文件变化并重新加载规则，无需重启应用；若未启用，则需重启后才生效

---

### Requirement: 路由决策日志记录
系统 SHALL 对每次路由决策输出结构化日志，支持问题排查和效果分析。

#### Scenario: 成功自动路由日志
- **WHEN** 自动路由成功完成
- **THEN** 输出 INFO 级别日志，格式为：`[AutoRouting] text="<原文前50字符>" | intent=<意图> | conf=<置信度> | model=<模型> | reason=<原因>`

#### Scenario: 低置信度回退日志
- **WHEN** 触发低置信度回退
- **THEN** 输出 WARN 级别日志，格式同上，但额外标注 `[FALLBACK]` 前缀，并包含完整原文（便于分析误判原因）

#### Scenario: 手动指定模型日志
- **WHEN** 用户手动指定了模型（非 auto 模式）
- **THEN** 输出 DEBUG 级别日志（避免生产环境日志过多），格式为：`[AutoRouting] mode=manual | model=<用户选择的模型>`
