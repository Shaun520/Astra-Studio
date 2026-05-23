# Astra-Studio 部署清单

## 📋 部署前检查清单

### ✅ 环境准备
- [ ] PostgreSQL 14+ 已安装并运行
- [ ] Redis 7+ 已安装并运行
- [ ] JDK 21+ 已安装
- [ ] Maven 3.8+ 已安装
- [ ] Node.js 18+ / pnpm 已安装（前端构建）

### ✅ 配置文件检查
- [ ] `application.yaml` 数据库连接配置正确
- [ ] Redis 连接配置正确
- [ ] API Keys 已配置（DASHSCOPE_API_KEY, CUSTOM_API_KEYS_BIGMODEL）
- [ ] OSS 配置正确（如使用文件上传功能）
- [ ] 日志级别配置合理

### ✅ 数据库准备
- [ ] 创建数据库：`CREATE DATABASE astra_studio;`
- [ ] 启用 pgvector 扩展：`CREATE EXTENSION IF NOT EXISTS vector;`
- [ ] 执行 Flyway 迁移脚本（V1-V7）
- [ ] 验证迁移结果：执行 `V7__validate_metadata.sql`
- [ ] 备份数据库（生产环境必须）

---

## 🔄 部署步骤

### 阶段 1: 数据库迁移（⚠️ 关键步骤）

```bash
# 1.1 备份数据库（生产环境）
pg_dump -h localhost -U postgres -d astra_studio > backup_$(date +%Y%m%d_%H%M%S).sql

# 1.2 执行 V7 迁移（如果尚未执行）
psql -h localhost -U postgres -d astra_studio -f src/main/resources/db/migration/V7__add_conversation_metadata.sql

# 1.3 验证迁移结果
psql -h localhost -U postgres -d astra_studio -f src/main/resources/db/migration/V7__validate_metadata.sql
```

**回滚方案**：
```bash
# 如果迁移失败，立即回滚
psql -h localhost -U postgres -d astra_studio -f src/main/resources/db/migration/V7__rollback_metadata.sql
```

---

### 阶段 2: 后端部署

```bash
cd Astra-Studio-Open-Ai

# 2.1 编译项目（跳过测试以加快速度）
./mvnw clean package -DskipTests

# 2.2 检查构建产物
ls -lh target/*.jar

# 2.3 停止旧服务（如果存在）
pkill -f "spring-boot:run" || true
sleep 5

# 2.4 启动新服务
java -jar target/astra-studio-open-ai-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=prod \
    > app.log 2>&1 &

# 2.5 等待服务启动（约 30 秒）
echo "Waiting for service to start..."
sleep 30

# 2.6 检查服务状态
curl -s http://localhost:8089/api/actuator/health | jq .
```

**生产环境建议**：
```bash
# 使用 systemd 管理服务
sudo systemctl start astra-studio
sudo systemctl enable astra-studio  # 开机自启
```

---

### 阶段 3: 前端部署

```bash
cd Astra-Studio

# 3.1 安装依赖
pnpm install

# 3.2 构建生产版本
pnpm build

# 3.3 检查构建产物
ls -lh dist/

# 3.4 部署到 Nginx/Apache
cp -r dist/* /var/www/html/astra-studio/
```

**Nginx 配置示例**：
```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态资源
    location / {
        root /var/www/html/astra-studio;
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api {
        proxy_pass http://localhost:8089;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # SSE 流式响应支持
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
        chunked_transfer_encoding on;
    }
}
```

---

## 🔍 部署后验证

### 功能测试清单

#### 会话管理功能
- [ ] POST `/api/conversation` - 创建会话成功
- [ ] GET `/api/conversation?page=0&size=20` - 获取会话列表
- [ ] GET `/api/conversation/{memoryId}/messages` - 获取消息历史
- [ ] PUT `/api/conversation/{memoryId}/title` - 更新标题
- [ ] DELETE `/api/conversation/{memoryId}` - 软删除会话

#### 对话功能
- [ ] POST `/api/chat` (SSE) - 发送消息并接收流式响应
- [ ] 验证自动创建会话逻辑
- [ ] 验证标题自动生成功能
- [ ] 验证消息计数更新

#### 性能监控
- [ ] Redis 缓存正常工作（查看日志中的 Cache HIT/MISS）
- [ ] 慢查询日志输出正常（>100ms 的查询）
- [ ] 监控指标收集器工作正常

### 健康检查端点

```bash
# Spring Boot Actuator
curl http://localhost:8089/api/actuator/health

# 自定义健康检查
curl http://localhost:8089/api/health/check
```

预期响应：
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

---

## 📊 监控配置

### 应用层监控

#### 1. 日志收集
```yaml
# application-prod.yaml
logging:
  level:
    com.example.astrastudioopenai: INFO
    org.hibernate.SQL: WARN  # 生产环境关闭 SQL 日志
  file:
    name: /var/log/astra-studio/app.log
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
      total-size-cap: 3GB
```

#### 2. 性能指标监控
系统内置 `MetricsCollector` 组件，可通过以下方式暴露指标：

**方式 A: HTTP 端点（简单）**
```java
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    @Autowired
    private MetricsCollector metricsCollector;

    @GetMapping
    public Map<String, Object> getMetrics() {
        Map<String, Object> result = new HashMap<>();
        
        // Counter 指标
        result.put("conversation_list_queries", 
            metricsCollector.getCounterValue("conversation_list_queries_total"));
        result.put("cache_hits",
            metricsCollector.getCounterValue("conversation_list_cache_hits"));
        
        // Histogram 指标
        MetricsCollector.HistogramStats stats = 
            metricsCollector.getHistogramStats("conversation_query_duration_ms");
        result.put("query_latency", stats);
        
        return result;
    }
}
```

**方式 B: Prometheus 对接（推荐）**
```xml
<!-- pom.xml 添加依赖 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
# application.yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

访问 Prometheus 指标：`http://localhost:8089/api/actuator/prometheus`

#### 3. Grafana Dashboard（可选）

创建 Grafana Dashboard 展示关键指标：

**面板 1: QPS 监控**
- 指标: `conversation_list_queries_total`
- 类型: Counter → Rate
- 刷新间隔: 10s

**面板 2: 缓存命中率**
- 计算: `cache_hits / queries_total * 100%`
- 目标: > 80%

**面板 3: P99 延迟**
- 指标: `conversation_query_duration_ms` (Histogram)
- 百分位: 99th
- 目标: < 200ms

**面板 4: 错误率**
- 指标: `conversation_list_errors`
- 目标: < 1%

---

## 🚨 故障排查指南

### 常见问题

#### 问题 1: 服务启动失败
**症状**: `Failed to configure DataSource`
**解决方案**:
```bash
# 检查数据库是否运行
pg_isready -h localhost -p 5432

# 检查连接配置
psql -h localhost -U postgres -d astra_studio -c "SELECT 1;"
```

#### 问题 2: Redis 连接失败
**症状**: `Cannot connect to Redis`
**解决方案**:
```bash
# 检查 Redis 是否运行
redis-cli ping

# 检查配置
redis-cli info server | grep redis_version
```

#### 问题 3: 迁移脚本失败
**症状**: `Flyway migration failed`
**解决方案**:
```bash
# 1. 查看错误日志
tail -n 100 app.log | grep ERROR

# 2. 手动执行 SQL 检查语法错误
psql -h localhost -U postgres -d astra_studio -f V7__add_conversation_metadata.sql

# 3. 如果需要回滚
psql -h localhost -U postgres -d astra_studio -f V7__rollback_metadata.sql

# 4. 清理 Flyway 历史记录（谨慎操作）
psql -c "DELETE FROM flyway_schema_history WHERE version = '7';"
```

#### 问题 4: 性能问题
**症状**: 响应时间 > 500ms
**排查步骤**:
1. 检查慢查询日志：`grep "SLOW QUERY" app.log`
2. 检查缓存命中率：访问 `/api/metrics` 端点
3. 分析数据库查询计划：`EXPLAIN ANALYZE SELECT ...`
4. 检查连接池状态：访问 `/api/actuator/metrics/hikaricp.connections.active`

---

## 📞 回滚方案

如果新版本出现严重问题，按以下步骤快速回滚：

### 快速回滚流程
```bash
# 1. 停止当前服务
sudo systemctl stop astra-studio

# 2. 回滚数据库（如果执行了 V7 迁移）
psql -h localhost -U postgres -d astra_studio -f V7__rollback_metadata.sql

# 3. 恢复备份的 JAR 包
cp backup/astra-studio-old.jar target/

# 4. 重启服务
sudo systemctl start astra-studio

# 5. 验证服务恢复
curl -s http://localhost:8089/api/actuator/health
```

**预计回滚时间**: < 5 分钟

---

## ✅ 部署完成确认

当所有以下条件满足时，标记部署成功：

- [ ] 所有迁移脚本执行成功且验证通过
- [ ] 后端服务启动无异常日志
- [ ] 所有 API 端点返回预期结果
- [ ] 前端页面正常加载且功能可用
- [ ] Redis 缓存正常工作
- [ ] 监控指标收集正常
- [ ] 健康检查端点返回 UP
- [ ] 无明显性能退化（P99 < 500ms）

---

## 📝 部署记录模板

| 项目 | 内容 |
|------|------|
| **部署日期** | YYYY-MM-DD HH:mm |
| **部署人员** | 姓名 |
| **版本号** | v0.2.0 |
| **环境** | 生产/预发布/测试 |
| **数据库名称** | astra_studio |
| **迁移脚本** | V7__add_conversation_metadata.sql |
| **备份文件** | backup_YYYYMMDD_HHMMSS.sql |
| **部署耗时** | XX 分钟 |
| **验证结果** | ✅ 通过 / ❌ 失败 |
| **问题记录** | （如有） |
| **备注** | （如有） |

---

## 🎯 后续优化方向

- [ ] 接入 Prometheus + Grafana 完整监控体系
- [ ] 配置 ELK 日志收集和分析
- [ ] 实现 CI/CD 自动化部署流水线
- [ ] 添加集成测试覆盖核心业务流程
- [ ] 配置告警规则（错误率、延迟、资源使用率）

---

**文档版本**: v1.0  
**最后更新**: 2026-05-22  
**维护者**: Astra Studio Team
