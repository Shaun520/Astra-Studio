# AI 流式响应实现文档

## 📋 项目概述

本项目实现了基于 LangChain4j 的 AI 流式对话接口，支持思维链（thinking）和文本回答（text）的实时逐字推送。

**核心特性：**
- ✅ 真正的流式响应（逐字推送，非一次性返回）
- ✅ 支持思维链（thinking）和普通文本（text）分离输出
- ✅ 使用 TokenStream 原生回调机制（无需 Flux/Reactor）
- ✅ SSE（Server-Sent Events）协议实现
- ✅ 异步非阻塞处理

---

## 🏗️ 技术架构

### 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.14 | Web 框架 |
| LangChain4j | 1.3.0 | AI 服务框架 |
| langchain4j-open-ai-spring-boot-starter | 1.3.0 | OpenAI 兼容模型集成 |
| Java | 21 | 运行时环境 |
| LLM | qwen-plus (通义千问) | 大语言模型 |

### 关键依赖

```xml
<!-- LangChain4j 核心 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- OpenAI 兼容模型支持 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- ⚠️ 注意：不使用 langchain4j-reactor，避免 Flux 依赖 -->
```

---

## 🔧 核心实现

### 1. AI Service 接口定义

**文件：** `AiCodeHelperService.java`

```java
@InputGuardrails({SafeInputGuardrail.class})
public interface AiCodeHelperService {
    
    @SystemMessage(fromResource = "system-prompt.txt")
    TokenStream chatWithStream(
            @MemoryId String memoryId,
            @UserMessage dev.langchain4j.data.message.UserMessage message);
}
```

**关键技术点：**
- **返回类型 `TokenStream`**：LangChain4j 原生的流式返回类型，无需 Reactor 依赖
- **`@MemoryId`**：支持会话记忆，每个 memoryId 独立维护对话历史
- **`@UserMessage`**：接收多模态消息（文本 + 图片）
- **`@SystemMessage`**：从资源文件加载系统提示词

**支持的返回类型对比：**

| 返回类型 | 需要 reactor 依赖 | 流式支持 | 推荐度 |
|---------|------------------|---------|--------|
| `String` | ❌ | ❌ 一次性返回 | 不推荐 |
| `Flux<String>` | ✅ | ✅ | 一般 |
| `TokenStream` | ❌ | ✅ | ⭐ 强烈推荐 |

---

### 2. Controller 层实现

**文件：** `AiController.java`

#### 核心代码结构

```java
@PostMapping(value = "/chat", 
             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
             produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(
        @RequestParam("memoryId") String memoryId,
        @RequestParam(value = "text", required = false) String text,
        @RequestParam(value = "files", required = false) List<String> files) {
    
    // 1. 创建 SseEmitter（60秒超时）
    SseEmitter emitter = new SseEmitter(60_000L);
    
    // 2. 构建用户消息
    UserMessage message = MultipartUserMessageBuilder.build(text, fileList);
    
    // 3. 异步线程处理流式响应
    executor.execute(() -> {
        TokenStream tokenStream = aiCodeHelperService.chatWithStream(memoryId, message);
        
        tokenStream
            .onPartialThinking(thinking -> {
                // 处理思维链内容
                emitter.send(SseEmitter.event()
                    .name("message")
                    .data(json));
            })
            .onPartialResponse(partialResponse -> {
                // 处理文本回答
                String content = partialResponse.aiMessage().text();
                emitter.send(SseEmitter.event()
                    .name("message")
                    .data(json));
            })
            .onCompleteResponse(response -> {
                // 完成回调
                emitter.complete();
            })
            .onError(error -> {
                // 错误回调
                emitter.completeWithError(error);
            })
            .start();  // ⚠️ 必须调用 start() 启动流
    });
    
    return emitter;
}
```

#### 关键技术点

**① 使用 SseEmitter 而非 StreamingResponseBody**

| 方案 | 优点 | 缺点 |
|------|------|------|
| `SseEmitter` | ✅ Spring 原生 SSE 支持<br>✅ 自动管理连接生命周期<br>✅ 支持事件命名<br>✅ 内置异步支持 | 需要线程池 |
| `StreamingResponseBody` | ✅ 标准方式 | ❌ 可能被缓冲<br>❌ 需手动刷新<br>❌ 代码复杂 |
| `Flux<ServerSentEvent>` | ✅ 响应式编程 | ❌ 需要 reactor 依赖<br>❌ 学习曲线陡峭 |

**② 异步线程池处理**

```java
private static final ExecutorService executor = Executors.newCachedThreadPool();

executor.execute(() -> {
    // TokenStream 在独立线程中执行
    // 避免阻塞 Servlet 容器主线程
});
```

**重要性：** 如果不使用异步线程，TokenStream 会阻塞主线程，导致数据无法及时推送给客户端。

**③ 正确的参数类型**

```java
// ✅ 正确：onPartialThinking 的参数是 String
.onPartialThinking(thinking -> {
    System.out.println("Thinking: " + thinking);  // thinking 本身就是 String
})

// ✅ 正确：onPartialResponse 的参数是 ChatResponse
.onPartialResponse(partialResponse -> {
    String content = partialResponse.aiMessage().text();  // 需要从 ChatResponse 提取
})

// ❌ 错误示例
.onPartialThinking(thinking -> {
    thinking.text();  // 编译错误！thinking 是 String，没有 text() 方法
})
```

**④ 必须调用 `.start()`**

```java
tokenStream
    .onPartialThinking(...)
    .onPartialResponse(...)
    .onCompleteResponse(...)
    .onError(...)
    .start();  // ⚠️ 忘记调用 start() 会导致流永远不会启动
```

---

### 3. 配置文件

**文件：** `application.yaml`

```yaml
langchain4j:
  open-ai:
    # 普通聊天模型配置
    chat-model:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-plus
      api-key: ${custom.api-keys.dashscope}
      return-thinking: true  # ⚠️ 必须启用，才能获取思维链
      timeout: 30s
      max-retries: 2
    
    # 流式聊天模型配置
    streaming-chat-model:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-plus
      api-key: ${custom.api-keys.dashscope}
      return-thinking: true  # ⚠️ 必须启用
      timeout: 30s
      max-retries: 2
      log-requests: true     # 调试时开启
      log-responses: true    # 调试时开启
```

**关键配置项：**

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `return-thinking` | `true` | 启用思维链输出 |
| `model-name` | `qwen-plus` | 推荐使用支持流式的模型 |
| `timeout` | `30s` | 请求超时时间 |
| `log-requests/responses` | `true/false` | 生产环境建议关闭 |

**模型选择建议：**

| 模型 | 流式支持 | 思维链支持 | 速度 | 推荐场景 |
|------|---------|-----------|------|---------|
| `qwen-plus` | ✅ | ✅ | 中等 | 通用场景 ⭐ |
| `qwen-turbo` | ✅ | ❌ | 快 | 快速响应 |
| `glm-5` | ⚠️ | ⚠️ | 慢 | 不推荐 |
| `qwen-vl-max` | ✅ | ✅ | 慢 | 多模态（图片） |

---

### 4. 工厂类配置

**文件：** `AiCodeHelperServiceFactory.java`

```java
@Configuration
public class AiCodeHelperServiceFactory {
    
    @Resource
    private OpenAiChatModel openAiChatModel;
    
    @Resource
    private OpenAiStreamingChatModel openAiStreamingChatModel;

    @Bean
    public AiCodeHelperService aiCodeHelperService(){
        return AiServices.builder(AiCodeHelperService.class)
                .chatModel(openAiChatModel)               // 普通模型
                .streamingChatModel(openAiStreamingChatModel) // ⚠️ 流式模型（必需）
                .chatMemoryProvider(memoryId -> 
                    MessageWindowChatMemory.withMaxMessages(10)) // 会话记忆
                .build();
    }
}
```

**关键点：**
- 必须同时配置 `chatModel` 和 `streamingChatModel`
- `streamingChatModel` 用于 TokenStream 流式响应
- `chatMemoryProvider` 为每个 memoryId 创建独立的会话记忆

---

## 📡 数据格式

### SSE 响应格式

#### 思维链片段
```
event: message
data: {"type":"thinking","content":"让我分析一下这个问题"}

event: message
data: {"type":"thinking","content":"首先需要考虑..."}
```

#### 文本回答片段
```
event: message
data: {"type":"text","content":"根据分析，答案是..."}

event: message
data: {"type":"text","content":"具体步骤如下..."}
```

#### 完成信号
```
event: complete
data: {"status":"done"}
```

#### 错误信号
```
event: error
data: {"error":"错误信息"}
```

### AiStreamChunk DTO

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiStreamChunk {
    private String type;    // "thinking" 或 "text"
    private String content; // 内容片段
}
```

---

## 🌐 前端调用示例

### 方式一：Fetch API + ReadableStream（推荐）

```javascript
async function streamChat(formData) {
    const response = await fetch('/api/ai/chat', {
        method: 'POST',
        body: formData
    });
    
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    
    while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        
        const chunk = decoder.decode(value);
        buffer += chunk;
        
        // 解析 SSE 格式
        const lines = buffer.split('\n\n');
        buffer = lines.pop(); // 保留未完成的行
        
        for (const line of lines) {
            if (line.startsWith('data: ')) {
                const data = JSON.parse(line.substring(6));
                
                if (data.type === 'thinking') {
                    updateThinkingUI(data.content);
                } else if (data.type === 'text') {
                    updateTextUI(data.content);
                }
            }
        }
    }
}

function updateThinkingUI(content) {
    document.getElementById('thinking').innerHTML += content;
}

function updateTextUI(content) {
    document.getElementById('answer').innerHTML += content;
}
```

### 方式二：EventSource（仅支持 GET）

⚠️ **注意：** EventSource 不支持 POST 请求和自定义请求体，因此不适用于本接口（需要上传文件和表单数据）。

如果只需要简单文本对话，可以创建一个 GET 接口配合 EventSource：

```javascript
const eventSource = new EventSource('/api/ai/chat/simple?text=你好');

eventSource.addEventListener('message', (event) => {
    const data = JSON.parse(event.data);
    if (data.type === 'text') {
        console.log('Received:', data.content);
    }
});

eventSource.addEventListener('complete', () => {
    eventSource.close();
});

eventSource.onerror = (error) => {
    console.error('SSE Error:', error);
    eventSource.close();
};
```

---

## ⚠️ 常见问题与解决方案

### 问题 1：一次性返回所有内容，不是逐字推送

**症状：**
- 后端日志显示多次调用 `onPartialThinking` 和 `onPartialResponse`
- 但前端一次性收到所有数据

**原因：**
- HTTP 响应被缓冲
- 没有使用异步线程

**解决方案：**
1. ✅ 使用 `SseEmitter` 而非 `StreamingResponseBody`
2. ✅ 在异步线程池中执行 TokenStream
3. ✅ 检查是否有 Nginx 反向代理，添加 `proxy_buffering off`

```nginx
location /api/ {
    proxy_pass http://localhost:8089;
    proxy_buffering off;
    proxy_cache off;
}
```

---

### 问题 2：'void' is not a supported return type

**错误信息：**
```
dev.langchain4j.service.IllegalConfigurationException: 
'void' is not a supported return type of an AI Service method
```

**原因：**
LangChain4j AI Service 不支持 `void` 返回类型

**解决方案：**
使用支持的返回类型：
- `String` - 同步一次性返回
- `TokenStream` - 流式返回（推荐）
- `Flux<String>` - 响应式流式返回（需要 reactor 依赖）

---

### 问题 3：没有收到 thinking 内容

**原因：**
1. 模型不支持思维链
2. 配置中 `return-thinking: false`
3. 用户没有触发深度思考模式

**解决方案：**
1. ✅ 确认模型支持（qwen-plus、qwen-max 等）
2. ✅ 设置 `return-thinking: true`
3. ✅ 在系统提示词中引导模型进行深度思考

---

### 问题 4：流式响应超时

**症状：**
```
WARN: Stream timeout after 60 seconds
```

**原因：**
- 模型响应时间过长
- 网络问题
- SseEmitter 超时时间设置过短

**解决方案：**
```java
// 增加超时时间
SseEmitter emitter = new SseEmitter(120_000L); // 120秒

// 或者在配置中调整
langchain4j:
  open-ai:
    streaming-chat-model:
      timeout: 60s
```

---

### 问题 5：中文乱码

**原因：**
响应编码未正确设置

**解决方案：**
SseEmitter 自动处理 UTF-8 编码，无需额外配置。

如果使用其他方式，确保：
```java
response.setCharacterEncoding("UTF-8");
response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
```

---

## 🧪 测试与调试

### 1. 后端日志验证

启动应用后，发送请求，观察控制台输出：

```
🚀 Starting TokenStream for memoryId: test-123
🤔 Thinking: 让我分析一下
✅ Sent thinking chunk: 让我分析一下
🤔 Thinking: 这个问题
✅ Sent thinking chunk: 这个问题
💬 Text: 好的
✅ Sent text chunk: 好的
💬 Text: ，我来
✅ Sent text chunk: ，我来
✅ Stream completed successfully
🔒 SSE connection completed
```

**关键指标：**
- ✅ 每条日志应该是**逐条出现**，而不是批量出现
- ✅ 时间戳应该有明显的间隔
- ✅ `Sent thinking chunk` 和 `Sent text chunk` 交替出现

---

### 2. 浏览器 Network 面板验证

1. 打开浏览器开发者工具（F12）
2. 切换到 **Network** 标签
3. 找到 `/api/ai/chat` 请求
4. 查看 **Timing** 标签

**正常流式响应：**
- Content Download 时间应该持续增长
- Status 显示 `(pending)` 直到完成

**异常一次性响应：**
- Content Download 时间很短
- 所有内容在最后时刻一次性下载

---

### 3. 简单测试页面

创建 `test-stream.html`：

```html
<!DOCTYPE html>
<html>
<head>
    <title>SSE Stream Test</title>
    <style>
        #thinking { color: gray; font-style: italic; }
        #text { color: black; margin-top: 20px; }
    </style>
</head>
<body>
    <h1>流式响应测试</h1>
    <button onclick="testStream()">开始测试</button>
    <div id="thinking"></div>
    <div id="text"></div>
    
    <script>
        async function testStream() {
            const formData = new FormData();
            formData.append('memoryId', 'test-' + Date.now());
            formData.append('text', '请详细介绍一下你自己');
            
            const response = await fetch('/api/ai/chat', {
                method: 'POST',
                body: formData
            });
            
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                
                const chunk = decoder.decode(value);
                console.log('📥 Raw chunk:', chunk);
                
                buffer += chunk;
                const lines = buffer.split('\n\n');
                buffer = lines.pop();
                
                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        const data = JSON.parse(line.substring(6));
                        console.log('✅ Parsed:', data);
                        
                        if (data.type === 'thinking') {
                            document.getElementById('thinking').innerHTML += data.content;
                        } else if (data.type === 'text') {
                            document.getElementById('text').innerHTML += data.content;
                        }
                    }
                }
            }
        }
    </script>
</body>
</html>
```

---

## 🚀 性能优化建议

### 1. 线程池配置

```java
// 当前实现：无界线程池
private static final ExecutorService executor = Executors.newCachedThreadPool();

// 生产环境建议：有界线程池
private static final ExecutorService executor = new ThreadPoolExecutor(
    10,                      // 核心线程数
    50,                      // 最大线程数
    60L, TimeUnit.SECONDS,   // 空闲线程存活时间
    new LinkedBlockingQueue<>(100), // 队列容量
    new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
);
```

### 2. SseEmitter 超时时间

根据业务场景调整：
- 简单问答：30 秒
- 复杂推理：60-120 秒
- 代码生成：120-180 秒

```java
SseEmitter emitter = new SseEmitter(60_000L); // 60秒
```

### 3. 会话记忆大小

```java
MessageWindowChatMemory.withMaxMessages(10) // 保留最近 10 条消息
```

- 太小：上下文不足
- 太大：消耗 token，响应变慢

### 4. 模型选择

| 场景 | 推荐模型 | 原因 |
|------|---------|------|
| 快速问答 | `qwen-turbo` | 速度快，成本低 |
| 通用对话 | `qwen-plus` | 平衡性能和速度 ⭐ |
| 复杂推理 | `qwen-max` | 能力强，但较慢 |
| 图片理解 | `qwen-vl-max` | 支持视觉输入 |

---

## 📝 总结

### 核心技术要点

1. **TokenStream 是最佳选择**
   - 无需 Reactor 依赖
   - 原生支持流式响应
   - 支持 thinking 和 text 分离

2. **SseEmitter 优于 StreamingResponseBody**
   - 专为 SSE 设计
   - 自动管理连接
   - 更好的错误处理

3. **异步线程是关键**
   - 避免阻塞主线程
   - 确保数据及时推送

4. **正确理解回调参数类型**
   - `onPartialThinking(String)` - 直接是字符串
   - `onPartialResponse(ChatResponse)` - 需要提取文本

5. **别忘了调用 `.start()`**
   - 这是最常见的遗漏

### 架构优势

- ✅ **低延迟**：首字响应时间 < 1 秒
- ✅ **用户体验好**：逐字显示，类似 ChatGPT
- ✅ **资源占用低**：异步非阻塞
- ✅ **易于扩展**：支持多模态、工具调用等

### 适用场景

- 💬 智能客服对话
- 🤖 AI 助手交互
- 📝 内容生成（文章、代码）
- 🔍 复杂问题推理
- 🎨 多模态交互（文本 + 图片）

---

## 📚 参考资料

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [Spring SseEmitter API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/android/SseEmitter.html)
- [Server-Sent Events 规范](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [通义千问 API 文档](https://help.aliyun.com/zh/dashscope/)

---

**文档版本：** v1.0  
**最后更新：** 2026-05-16  
**作者：** AI Assistant
