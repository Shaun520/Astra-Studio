## ADDED Requirements

### Requirement: LLM 智能标题生成服务
系统 SHALL 基于 DashScope text-generation-v3 API 实现会话标题的智能生成能力。

#### Scenario: 正常生成流程
- **WHEN** 第一条 assistant 回复完成后（onCompleteResponse 回调触发）
- **AND** 配置项 `conversation.title.auto-generate=true`
- **THEN** 系统构造 Prompt：
  ```
  请根据以下对话内容生成一个简洁的会话标题（≤15字）：
  
  用户问题：{userMessage}
  AI回复：{assistantReply}
  
  要求：
  - 提炼核心主题
  - 语言简洁明了
  - 示例格式："快速排序算法"、"Python爬虫入门"
  ```
- **AND** 调用 text-generation-v3 API（temperature=0.3, max_tokens=20）
- **AND** 将生成的标题保存到 conversations.title 字段

#### Scenario: 超时降级处理
- **WHEN** LLM API 响应时间超过 2 秒
- **THEN** 中止等待，切换到规则提取模式（见下方 Requirement）

#### Scenario: API 异常降级
- **WHEN** LLM API 返回错误（4xx/5xx）或网络不可达
- **THEN** 记录 WARN 日志并降级到规则提取模式

#### Scenario: 生成结果长度校验
- **WHEN** LLM 返回的标题长度 > 30 字符
- **THEN** 截断至 30 字符并添加省略号（如"深入理解机器学习..."）

---

### Requirement: 规则提取降级方案
系统 SHALL 实现基于规则的标题生成作为 LLM 失败时的兜底策略。

#### Scenario: 提取用户首条消息前 20 字
- **WHEN** 触发规则提取模式（LLM 超时/失败 或 auto-generate=false）
- **THEN** 从 userMessage 中提取前 20 个中文字符（或 40 个英文字符）
- **AND** 去除首尾空白、换行符、特殊符号（保留中文标点）
- **AND** 作为默认标题保存

#### Scenario: 默认标题兜底
- **WHEN** userMessage 为空或仅包含空白字符
- **THEN** 使用硬编码默认值"新对话"作为标题

#### Scenario: 特殊字符清洗
- **WHEN** 提取的文本包含 emoji、HTML 标签、控制字符
- **THEN** 通过正则表达式移除：`[^\u4e00-\u9fa5a-zA-Z0-9\s，。！？、：；""''（）]`

---

### Requirement: 标题生成异步化配置
系统 SHALL 支持同步/异步两种标题生成模式，通过配置文件灵活切换。

#### Scenario: 同步模式（默认）
- **WHEN** `conversation.title.async=false`（默认值）
- **THEN** 在 ChatService.subscribeTokenStream() 的 onComplete 回调中**同步**调用标题生成
- **AND** 标题生成完成后再发送 SSE complete 事件给前端

#### Scenario: 异步模式（性能优先）
- **WHEN** `conversation.title.async=true`
- **THEN** 通过 @Async("titleGeneratorExecutor") 线程池异步执行标题生成
- **AND** SSE complete 事件先于标题生成返回（前端显示"新对话"，后台静默替换）

#### Scenario: 异步线程池隔离
- **WHEN** 启用异步模式
- **THEN** 使用独立的线程池（corePoolSize=2, maxPoolSize=5, queueCapacity=50）
- **AND** 与主 streamExecutor 线程池隔离，避免互相影响
