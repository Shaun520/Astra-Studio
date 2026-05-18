## 1. 基础设施与配置

- [x] 1.1 创建 `src/main/resources/intent-rules.yaml` 配置文件，定义三类意图规则：
  - CODE_GENERATION：关键词（写代码、编程、debug、Python、Java、算法等）+ 模式（帮我写.*函数）+ 权重0.9 + 目标模型 deepseek-v4-flash
  - CHINESE_QA：关键词（解释、翻译、总结、什么是、为什么）+ 模式（解释.*是什么）+ 权重0.85 + 目标模型 qwen3.6-flash
  - GENERAL_CHAT：空关键词列表 + 权重0.7 + 目标模型 glm-5（兜底）

- [x] 1.2 更新 `application.yaml`，添加 auto-routing 配置段：
  ```yaml
  auto-routing:
    enabled: true
    confidence-threshold: 0.6
    default-model: glm-5
    rules-file: classpath:intent-rules.yaml
    rules-hot-reload: false
  ```

- [x] 1.3 创建 `ClassificationResult` 记录类（位于 ai 包或新建 routing 包），包含字段：intent（String）、confidence（double）、reason（String）

- [x] 1.4 创建 `RoutingDecision` 记录类，包含字段：selectedModel（String）、confidence（double）、reason（String）

- [x] 1.5 创建 `AutoRouteResult` 记录类，包含字段：modelName（String）、isAutoRouted（boolean）、confidence（double）、reason（String），并提供工厂方法 manual()、auto()、fallback()

## 2. 意图分类器实现

- [x] 2.1 创建 `IntentClassifier.java` 服务类，使用 @Service 注解，注入 @Value 获取 rules-file 路径

- [x] 2.2 实现 YAML 规则加载逻辑：使用 SnakeYAML 或 Spring 的 YamlMapFactoryBean 解析 intent-rules.yaml，构建内部数据结构（List<IntentConfig>）

- [x] 2.3 实现关键词匹配算法：
  - 遍历每个意图的关键词组列表
  - 对每组关键词检查用户文本是否包含任一关键词（String.contains()）
  - 计算关键词得分 = 命中组数 / 总组数（0-1之间）

- [x] 2.4 实现正则表达式模式匹配：
  - 在启动时预编译所有 Pattern 对象并存入 Map<String, List<Pattern>>
  - 对用户文本执行 matcher.matches() 检查
  - 返回布尔值（是否命中任一模式）

- [x] 2.5 实现综合评分与选择逻辑：
  - 对每个意图计算 finalScore = max(keywordScore, patternScore) × weight
  - 选择 finalScore 最高的意图作为分类结果
  - 生成 reason 字符串（如"检测到关键词：写代码, Python"或"模式匹配：解释.*是什么"）

- [x] 2.6 实现 classify(String text) 公开方法，返回 ClassificationResult

## 3. 模型路由器实现

- [x] 3.1 创建 `ModelRouter.java` 服务类，使用 @Service 注解

- [x] 3.2 实现 route(ClassificationResult classification) 方法：
  - 使用 switch 表达式或 if-else 映射意图到模型：
    - CODE_GENERATION → "deepseek-v4-flash"
    - CHINESE_QA → "qwen3.6-flash-2026-04-16"
    - GENERAL_CHAT 或其他 → "glm-5"
  - 返回 RoutingDecision 对象（包含 selectedModel、confidence、reason）

## 4. 自动路由编排服务

- [x] 4.1 创建 `AutoRoutingService.java` 服务类，使用 @Service 注解，注入 IntentClassifier 和 ModelRouter

- [x] 4.2 注入配置值：@Value("${auto-routing.confidence-threshold}") double threshold 和 @Value("${auto-routing.default-model}") String defaultModel

- [x] 4.3 实现核心方法 autoRoute(String userText, String explicitModel):
  - **分支1**：如果 explicitModel 不为 null 且不为 "auto"，返回 AutoRouteResult.manual(explicitModel)，记录 DEBUG 日志
  - **分支2**：调用 intentClassifier.classify(userMessageText) 获取 ClassificationResult
  - **分支3**：检查 confidence >= threshold？
    - 是：调用 modelRouter.route(classification)，返回 AutoRouteResult.auto(decision)
    - 否：记录 WARN 日志（包含原文前50字符），返回 AutoRouteResult.fallback(defaultModel, classification)

- [x] 4.4 实现日志输出规范：
  - 成功路由：`log.info("[AutoRouting] text=\"{}\" | intent={} | conf={} | model={} | reason={}", ...)`
  - 回退：`log.warn("[AutoRouting][FALLBACK] text=\"{}\" | bestIntent={} | conf={}", ...)`
  - 手动模式：`log.debug("[AutoRouting] mode=manual | model={}", ...)`

## 5. Controller 层集成

- [x] 5.1 在 AiController.java 中注入 AutoRoutingService：@Resource private AutoRoutingService autoRoutingService

- [x] 5.2 修改 chat() 方法的模型选择逻辑（在现有校验代码之后、Factory 调用之前）：
  ```java
  // 现有代码：校验白名单（需要扩展支持 "auto"）
  String resolvedModelName;
  if ("auto".equals(modelName)) {
      AutoRouteResult routeResult = autoRoutingService.autoRoute(userMessageText, modelName);
      resolvedModelName = routeResult.getModelName();
      log.info("🤖 Auto-routed to '{}' (confidence={}, reason={})",
          resolvedModelName, routeResult.getConfidence(), routeResult.getReason());
  } else {
      resolvedModelName = modelName; // 保持原有逻辑
  }
  ```

- [x] 5.3 扩展模型白名单校验逻辑，在 IllegalArgumentException 抛出前增加对 "auto" 值的放行判断

- [x] 5.4 在 SSE 流式响应开始前（executor.execute() 内部，tokenStream 订阅之前）添加 routing_info 事件推送：
  ```java
  if ("auto".equals(modelName)) {
      AiStreamChunk routingChunk = new AiStreamChunk("routing_info", null,
          new AiStreamChunk.UsageInfo(resolvedModelName, 0, 0, 0, 0, 0,
              routeResult.isAutoRouted() ? 1 : 0, routeResult.getConfidence()));
      emitter.send(SseEmitter.event().name("message").data(objectMapper.writeValueAsString(routingChunk)));
  }
  ```
  （注意：复用 UsageInfo 结构体或创建新的 RoutingInfo 结构体）

- [x] 5.5 更新 chat() 方法的日志输出，在现有的 `deepThink={}, webSearch={}, model={}` 基础上追加 `autoRoute={}` 信息（显示是否触发了自动路由）

## 6. 路由统计服务

- [x] 6.1 创建 `RoutingStatsService.java` 服务类，使用 @Service 注解

- [x] 6.2 定义统计字段（全部使用线程安全的原子类型）：
  - AtomicLong totalRequests, autoRoutedCount, manualOverrideCount, fallbackCount
  - ConcurrentHashMap<String, AtomicLong> intentDistribution（key为意图名）
  - ConcurrentHashMap<String, AtomicLong> modelUsage（key为模型名）
  - AtomicReference<Double> totalConfidenceSum（用于计算平均值，替代 DoubleAdder 以兼容低版本 Java）

- [x] 6.3 实现 recordRouting(AutoRouteResult result, ClassificationResult classification) 方法：
  - totalRequests.incrementAndGet()
  - 根据 result.isAutoRouted() 分别递增 autoRoutedCount / manualOverrideCount / fallbackCount
  - 如果有 classification，更新 intentDistribution 和 modelUsage
  - totalConfidenceSum.set(totalConfidenceSum.get() + result.getConfidence())

- [x] 6.4 实现 getStats() 方法，返回 RoutingStatsRecord（包含所有统计快照 + 平均置信度计算 + 时间戳）

- [x] 6.5 在 AiController 的 autoRoute() 调用之后，立即调用 statsService.recordRouting(routeResult, classification)

- [x] 6.6 在 AiController 中新增 GET 接口：
  ```java
  @GetMapping("/routing-stats")
  public ResponseEntity<RoutingStatsService.RoutingStatsRecord> getRoutingStats() {
      return ResponseEntity.ok(statsService.getStats());
  }
  ```

## 7. 前端适配（可选增强）

- [x] 7.1 更新 MainHeader.vue 的 models 数组常量，在列表顶部新增自动选项：
  ```javascript
  { name: '🤖 自动', desc: '智能选择最优模型', tag: '推荐', value: 'auto' }
  ```

- [x] 7.2 更新 api.ts 的 SendChatMessageOptions 接口注释，说明 model 字段支持 "auto" 值

- [x] 7.3 在 App.vue 或 Composer.vue 的 SSE 消息处理逻辑中，增加对 type="routing_info" 事件的解析和展示（如 Toast 提示："已为您自动选择 DeepSeek V4 Flash"）

## 8. 测试

- [ ] 8.1 编写 IntentClassifierTest 单元测试：
  - testClassifyCodeGeneration(): 输入"帮我写个Python爬虫" → 断言 intent=CODE_GENERATION, conf>=0.8
  - testClassifyChineseQA(): 输入"解释量子纠缠是什么" → 断言 intent=CHINESE_QA, conf>=0.8
  - testClassifyGeneralChat(): 输入"今天天气怎么样" → 断言 intent=GENERAL_CHAT
  - testEmptyInput(): 输入"" → 断言不抛异常，返回 GENERAL_CHAT 且 conf=0
  - testLowConfidenceFallback(): 输入"嗯" → 断言 conf < 0.6

- [ ] 8.2 编写 AutoRoutingServiceTest 单元测试：
  - testManualMode(): 传入 explicitModel="deepseek-v4-flash" → 断言 isAutoRouted=false
  - testAutoModeSuccess(): 传入 explicitModel="auto" + 代码相关文本 → 断言 isAutoRouted=true, model=deepseek-v4-flash
  - testAutoModeFallback(): 传入 explicitModel="auto" + 模糊文本 → 断言 isAutoRouted=false (fallback), model=glm-5

- [ ] 8.3 编写 AiControllerIntegrationTest 集成测试（可选）：
  - 使用 MockMvc 或 TestRestTemplate 发送 POST /ai/chat 请求（model=auto, text="写代码"）
  - 验证响应 SSE 流中包含 routing_info 事件且 model=deepseek-v4-flash

- [ ] 8.4 手动测试清单验证：
  - [ ] 场景A：Postman 发送请求（model=auto, text="帮我写个快速排序算法"）→ 日志显示路由到 deepseek-v4-flash
  - [ ] 场景B：发送请求（model=auto, text="解释一下机器学习"）→ 日志显示路由到 qwen3.6-flash
  - [ ] 场景C：发送请求（model=auto, text="你好"）→ 日志显示 fallback 到 glm-5
  - [ ] 场景D：发送请求（model="glm-5", text="写代码"）→ 日志显示 mode=manual，使用 glm-5
  - [ ] 场景E：访问 GET /api/ai/routing-stats → 返回 JSON 包含累计统计数据

## 9. 文档与清理

- [ ] 9.1 更新 README.md（如果项目有的话），添加"自动路由"功能说明章节：
  - 如何启用（前端选择"🤖 自动"或传 model=auto）
  - 工作原理简述（意图识别 → 模型映射）
  - 如何查看路由统计（/api/ai/routing-stats 接口）
  - 如何调整规则（编辑 intent-rules.yaml）

- [ ] 9.2 为所有新增的公共类和方法添加 Javadoc 注释（IntentClassifier、ModelRouter、AutoRoutingService、RoutingStatsService）

- [ ] 9.3 代码审查 checklist：
  - 无 System.out.println（全部使用 Slf4j 日志）
  - 无硬编码的魔法数字（阈值、权重等全部从配置文件读取）
  - 异常处理完善（AutoRoutingService 的异常不应导致主流程失败）
  - 线程安全验证（所有共享变量都使用了并发容器或原子类型）
