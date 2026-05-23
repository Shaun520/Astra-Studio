# Astra-Studio-Open-Ai

AI 驱动的智能对话系统，基于 langchain4j 框架构建，支持多模型、会话持久化、知识库 RAG 等企业级特性。

## 🎯 核心功能

### 1. 智能对话系统
- **流式 SSE 输出**：基于 SseEmitter + CompletableFuture 的异步流式响应
- **多模型支持**：DeepSeek、GLM-5、Qwen 等主流 LLM
- **自动路由**：基于任务类型智能选择最优模型（Auto-Routing）
- **深度思考**：支持 AI 推理过程可视化展示

### 2. 会话管理系统 ✨ *New*
- **持久化存储**：PostgreSQL + Redis 双层架构
- **自动创建**：用户首次发送消息时自动建立会话记录
- **元数据同步**：消息计数、预览文本实时更新
- **标题生成**：基于 LLM 的智能标题生成（含规则降级方案）
- **软删除**：支持会话回收站机制，30 天后物理删除
- **分页查询**：高效的消息历史分页加载

### 3. 知识库 RAG
- **文档 ETL Pipeline**：支持 PDF/Word/TXT 等格式解析
- **向量检索**：基于 pgvector 的语义搜索
- **增量更新**：文档变更时自动重建向量索引

### 4. 性能监控
- **慢查询检测**：自动识别 >100ms 的数据库查询
- **Redis 缓存**：热门会话列表 Cache-Aside 缓存（TTL: 5 分钟）
- **指标收集**：Prometheus 兼容的 Counter/Histogram 指标

---

## 📡 REST API 文档

### 会话管理 API

#### 创建会话
```http
POST /api/conversation
Content-Type: application/json

{
  "memoryId": "optional-custom-session-id",
  "modelName": "deepseek-v4-flash"
}

Response 201 Created:
{
  "id": 1,
  "memoryId": "uuid-or-custom-id",
  "title": "新对话",
  "modelName": "deepseek-v4-flash",
  "messageCount": 0,
  "status": 1,
  "updatedAt": "2026-05-22T10:00:00"
}
```

#### 获取会话列表
```http
GET /api/conversation?page=0&size=20&keyword=optional

Response 200 OK:
{
  "content": [...],
  "totalElements": 42,
  "totalPages": 3,
  "currentPage": 0,
  "size": 20
}
```

#### 获取会话消息
```http
GET /api/conversation/{memoryId}/messages?page=0&size=50&role=USER

Response 200 OK:
{
  "content": [
    {
      "id": 1,
      "role": "USER",
      "content": "你好",
      "thinkingContent": null,
      "attachments": null,
      "sequenceNum": 1,
      "timestamp": "2026-05-22T10:00:00"
    }
  ],
  "totalElements": 10,
  ...
}
```

#### 更新会话标题
```http
PUT /api/conversation/{memoryId}/title
Content-Type: application/json

{ "title": "新的会话标题" }

Response 204 No Content
```

#### 删除会话（软删除）
```http
DELETE /api/conversation/{memoryId}

Response 204 No Content
```

### 对话 API

#### 流式聊天
```http
POST /api/chat
Content-Type: multipart/form-data

Parameters:
- memoryId: string (required)
- text: string (user message)
- files: List<string> (optional, OSS URLs)
- deepThink: boolean (default: false)
- webSearch: boolean (default: false)
- model: string (default: "glm-5")
- knowledgeBase: boolean (default: false)

Response: text/event-stream (SSE)
Events:
- message: {"type": "text"|"thinking", "content": "..."}
- complete: {"status": "done"}
- error: {"error": "..."}
```

---

## 🗄️ 数据库 Schema

### conversations 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| memory_id | VARCHAR(64) UNIQUE | 会话 ID |
| title | VARCHAR(255) | 标题（默认："新对话"）|
| model_name | VARCHAR(50) | 使用的模型 |
| message_count | INT DEFAULT 0 | 消息计数 |
| last_message_preview | TEXT | 最后消息预览（前 100 字符）|
| status | SMALLINT DEFAULT 1 | 状态（1: 正常, -1: 已删除）|
| deleted_at | TIMESTAMP | 软删除时间 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### conversation_messages 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| conversation_id | BIGINT FK | 关联会话 |
| role | VARCHAR(20) | 角色（USER/ASSISTANT/SYSTEM）|
| content | TEXT | 消息内容 |
| thinking_content | TEXT | 思维链内容（可选）|
| attachments | JSONB | 附件列表（可选）|
| sequence_num | INT | 会话内序号 |
| timestamp | TIMESTAMP | 时间戳 |

**索引优化**：
- `idx_conversations_status_updated`: (status, updated_at DESC) - 加速列表查询
- `idx_messages_conv_seq`: (conversation_id, sequence_num) - 加速消息分页

---

## ⚙️ 配置说明

### application.yaml 关键配置项

```yaml
# 对话持久化配置
conversation:
  persistence:
    enabled: true
    redis-ttl-hours: 24        # Redis 缓存 TTL
    async-flush: true          # 异步刷写 PostgreSQL
  default-model: deepseek-v4-flash
  title:
    auto-generate: true        # 自动生成标题
    max-length: 30             # 标题最大长度
    model-name: deepseek-v4-flash
    timeout-seconds: 10

# 性能监控配置
performance:
  monitoring:
    enabled: true
    slow-query-threshold-ms: 100  # 慢查询阈值
    log-slow-queries: true

# 数据库连接池（HikariCP）
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      leak-detection-threshold: 60000  # 连接泄漏检测
```

---

## 🚀 快速开始

### 环境要求
- JDK 21+
- PostgreSQL 14+ (需启用 pgvector 扩展)
- Redis 7+
- Maven 3.8+

### 本地启动
```bash
# 1. 配置环境变量
export DASHSCOPE_API_KEY=your-api-key
export CUSTOM_API_KEYS_BIGMODEL=your-bigmodel-key

# 2. 启动后端服务
cd Astra-Studio-Open-Ai
./mvnw spring-boot:run

# 3. 启动前端开发服务器
cd Astra-Studio
pnpm install && pnpm dev
```

### 访问地址
- 前端 UI: http://localhost:5173
- 后端 API: http://localhost:8089/api

---

## 📊 监控指标

系统内置轻量级监控指标收集器（`MetricsCollector`），可通过以下方式查看：

### 内置指标
| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `conversation_list_queries_total` | Counter | 会话列表查询总次数 |
| `conversation_list_cache_hits` | Counter | Redis 缓存命中次数 |
| `conversation_list_errors` | Counter | 查询错误次数 |
| `conversation_query_duration_ms` | Histogram | 查询响应时间分布 |

### Prometheus 对接（可选）
如需对接 Prometheus + Grafana：
1. 添加 Micrometer Prometheus 依赖
2. 在 `MetricsCollector` 中导出为 Prometheus 格式
3. 配置 Grafana Dashboard 展示指标

---

## 🔧 性能优化建议

### 数据库层
- ✅ 已添加复合索引加速查询
- ✅ HikariCP 连接池优化（最大连接数 10）
- ✅ Hibernate 统计信息开启（generate_statistics）

### 缓存层
- ✅ Redis 热缓存（会话列表 TLL: 5 分钟）
- ✅ Cache-Aside 模式（读时缓存，写时失效）

### 应用层
- ✅ 异步线程池处理耗时操作（@Async）
- ✅ 慢查询日志告警（>100ms）

---

## 📝 更新日志

### v0.2.0 (2026-05-22) - 会话管理增强
- ✨ 新增会话 CRUD API（创建、列表、删除、标题修改）
- ✨ 新增消息分页查询 API
- ✨ 新增标题自动生成服务（LLM + 规则降级）
- ✨ 新增定时清理任务（30 天软删除数据物理删除）
- 🔧 性能优化：Redis 缓存、慢查询监控、指标收集
- 🐛 Bug 修复：并发场景下的乐观锁冲突处理

### v0.1.0 (2026-05-20) - 初始版本
- 基础流式对话功能
- 多模型支持（DeepSeek/GLM/Qwen）
- 知识库 RAG 功能
- 自动路由系统

---

## 📄 License

MIT License

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request
