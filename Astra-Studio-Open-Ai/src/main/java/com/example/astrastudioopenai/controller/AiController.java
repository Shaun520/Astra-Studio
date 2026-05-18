package com.example.astrastudioopenai.controller;

import com.example.astrastudioopenai.ai.AiCodeHelperService;
import com.example.astrastudioopenai.ai.AiCodeHelperServiceFactory;
import com.example.astrastudioopenai.dto.AiStreamChunk;
import com.example.astrastudioopenai.routing.AutoRouteResult;
import com.example.astrastudioopenai.routing.AutoRoutingService;
import com.example.astrastudioopenai.routing.ClassificationResult;
import com.example.astrastudioopenai.routing.RoutingStatsService;
import com.example.astrastudioopenai.utils.MultipartUserMessageBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequestMapping("/ai")
@RestController
@Slf4j
public class AiController {
    @Resource
    private AiCodeHelperServiceFactory aiServiceFactory;

    @Resource
    private AutoRoutingService autoRoutingService;

    @Resource
    private RoutingStatsService statsService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 流式对话接口 - 使用 SseEmitter 实现真正的逐字流式返回
     *
     * @param memoryId  会话记忆ID（字符串格式）
     * @param text      文本内容
     * @param files     文件URL列表（OSS直传后的可访问URL）
     * @param deepThink 是否启用深度思考（思维链）
     * @param webSearch 是否启用联网搜索工具
     * @param modelName 使用的模型名称（默认 glm-5）
     */
    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestParam("memoryId") String memoryId,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam(value = "deepThink", defaultValue = "false") boolean deepThink,
            @RequestParam(value = "webSearch", defaultValue = "false") boolean webSearch,
            @RequestParam(value = "model", defaultValue = "glm-5") String modelName) {

        log.info("memoryId: {}, text: {}, files: {}, deepThink: {}, webSearch={}, model={}", memoryId, text, files,
                deepThink, webSearch, modelName);

        String resolvedModelName = modelName;
        AutoRouteResult routeResult = null;
        ClassificationResult classificationResult = null;

        try {
            if ("auto".equals(modelName)) {
                // 自动路由模式：调用意图分类器选择最优模型
                routeResult = autoRoutingService.autoRoute(text, modelName);
                resolvedModelName = routeResult.modelName();
                classificationResult = new ClassificationResult(
                        routeResult.reason().contains("意图") ? extractIntentFromReason(routeResult.reason()) : "UNKNOWN",
                        routeResult.confidence(),
                        routeResult.reason());
                log.info("🤖 Auto-routed to '{}' (confidence={}, reason={})",
                        resolvedModelName,
                        String.format("%.2f", routeResult.confidence()),
                        routeResult.reason());
            } else {
                // 手动模式或默认值：校验白名单（放行 "auto" 以外的合法值）
                aiServiceFactory.getService(deepThink, webSearch, modelName);
            }
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid model name: {}", modelName);
            throw new IllegalArgumentException("Unsupported model: " + modelName
                    + ". Allowed models: glm-5, deepseek-v4-flash, qwen3.6-flash-2026-04-16, auto");
        }

        // 创建 SseEmitter，设置超时时间为 60 秒
        SseEmitter emitter = new SseEmitter(60_000L);

        // 用于跟踪连接状态的标志位
        final boolean[] connectionClosed = { false };

        List<String> fileList = files == null ? Collections.emptyList()
                : files.stream()
                        .filter(url -> url != null && !url.isBlank())
                        .map(String::trim)
                        .toList();

        String userMessageText = MultipartUserMessageBuilder.buildText(text, fileList);

        // 复制到 final 变量以供 lambda 表达式使用
        final String finalResolvedModelName = resolvedModelName;
        final AutoRouteResult finalRouteResult = routeResult;
        final ClassificationResult finalClassificationResult = classificationResult;

        // 在异步线程中处理流式响应，避免阻塞主线程
        executor.execute(() -> {
            try {
                log.info("🚀 Starting TokenStream for memoryId: {}", memoryId);

                // 推送路由信息（如果使用了自动路由）
                if (finalRouteResult != null && finalRouteResult.isAutoRouted()) {
                    try {
                        if (!connectionClosed[0]) {
                            AiStreamChunk routingChunk = new AiStreamChunk("routing_info", null,
                                    new AiStreamChunk.UsageInfo(
                                            finalResolvedModelName, 0, 0, 0, 0, 0,
                                            1,
                                            finalRouteResult.confidence()));
                            String routingJson = objectMapper.writeValueAsString(routingChunk);
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(routingJson)
                                    .reconnectTime(3000));
                            log.info("📡 Sent routing_info: model={}, confidence={}",
                                    finalResolvedModelName, String.format("%.2f", finalRouteResult.confidence()));
                        }
                    } catch (IOException e) {
                        handleEmitterError(emitter, e, "routing_info", connectionClosed);
                    }
                }

                // 动态获取AI服务（带缓存，支持无限扩展）
                AiCodeHelperService selectedService = aiServiceFactory.getService(deepThink, webSearch,
                        finalResolvedModelName);
                log.info("🤖 AI service config: deepThink={}, webSearch={}, model={}, autoRoute={}",
                        deepThink, webSearch, finalResolvedModelName,
                        finalRouteResult != null && finalRouteResult.isAutoRouted());

                // 记录路由统计
                if (finalRouteResult != null) {
                    statsService.recordRouting(finalRouteResult, finalClassificationResult);
                }

                // 获取 TokenStream
                dev.langchain4j.service.TokenStream tokenStream = selectedService.chatWithStream(memoryId,
                        userMessageText);

                // 订阅流式响应
                tokenStream
                        .onPartialThinking(thinking -> {
                            try {
                                if (!connectionClosed[0]) {
                                    System.out.println("🤔 Thinking: " + thinking.text());
                                    AiStreamChunk chunk = new AiStreamChunk("thinking", thinking.text());
                                    String json = objectMapper.writeValueAsString(chunk);

                                    // 使用 send 方法立即推送数据
                                    emitter.send(SseEmitter.event()
                                            .name("message")
                                            .data(json)
                                            .reconnectTime(3000));

                                    log.debug("✅ Sent thinking chunk: {}", thinking);
                                }
                            } catch (IOException e) {
                                handleEmitterError(emitter, e, "thinking", connectionClosed);
                            }
                        })
                        .onPartialResponse(content -> {
                            try {
                                if (!connectionClosed[0] && content != null && !content.isEmpty()) {
                                    System.out.println("💬 Text: " + content);
                                    AiStreamChunk chunk = new AiStreamChunk("text", content);
                                    String json = objectMapper.writeValueAsString(chunk);

                                    // 使用 send 方法立即推送数据
                                    emitter.send(SseEmitter.event()
                                            .name("message")
                                            .data(json)
                                            .reconnectTime(3000));

                                    log.debug("✅ Sent text chunk: {}", content);
                                }
                            } catch (IOException e) {
                                handleEmitterError(emitter, e, "text", connectionClosed);
                            }
                        })
                        .onCompleteResponse(response -> {
                            try {
                                if (!connectionClosed[0]) {
                                    log.info("✅ Stream completed successfully");
                                    emitter.send(SseEmitter.event()
                                            .name("complete")
                                            .data("{\"status\":\"done\"}"));
                                    emitter.complete();
                                }
                            } catch (IOException e) {
                                log.error("❌ Error completing stream", e);
                                safeComplete(emitter, connectionClosed);
                            }
                        })
                        .onError(error -> {
                            log.error("❌ Stream error: ", error);
                            if (!connectionClosed[0]) {
                                try {
                                    String errorMessage = error != null
                                            ? (error.getMessage() != null ? error.getMessage() : "Unknown error")
                                            : "Unknown error";
                                    emitter.send(SseEmitter.event()
                                            .name("error")
                                            .data("{\"error\":\"" +
                                                    errorMessage.replace("\"", "\\\"") + "\"}"));
                                } catch (IOException e) {
                                    log.warn("⚠️ Failed to send error event (client may have disconnected)", e);
                                } finally {
                                    safeCompleteWithError(emitter, error, connectionClosed);
                                }
                            }
                        })
                        .start();

            } catch (Exception e) {
                log.error("❌ Error in streaming chat", e);
                if (!connectionClosed[0]) {
                    try {
                        String errorMessage = e.getMessage() != null ? e.getMessage() : "Internal server error";
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"error\":\"" + errorMessage.replace("\"", "\\\"") + "\"}"));
                    } catch (IOException ioException) {
                        log.warn("⚠️ Failed to send error event (client may have disconnected)", ioException);
                    } finally {
                        safeCompleteWithError(emitter, e, connectionClosed);
                    }
                }
            }
        });

        // 设置完成回调
        emitter.onCompletion(() -> {
            log.info("🔒 SSE connection completed");
            connectionClosed[0] = true;
        });

        // 设置超时回调
        emitter.onTimeout(() -> {
            log.warn("⏰ SSE connection timed out");
            connectionClosed[0] = true;
            emitter.complete();
        });

        // 设置错误回调
        emitter.onError((ex) -> {
            log.debug("⚠️ SSE connection error (client disconnected): {}", ex.getMessage());
            connectionClosed[0] = true;
        });

        return emitter;
    }

    /**
     * 安全地处理 emitter 发送错误
     */
    private void handleEmitterError(SseEmitter emitter, Exception e, String context, boolean[] connectionClosed) {
        if (isClientDisconnected(e)) {
            if (!connectionClosed[0]) {
                log.info("📴 Client disconnected during {}, stopping stream: {}", context, e.getMessage());
                connectionClosed[0] = true;
            }
        } else {
            log.error("❌ Error sending {} chunk", context, e);
            safeCompleteWithError(emitter, e, connectionClosed);
        }
    }

    /**
     * 判断是否是客户端断开连接导致的异常
     */
    private boolean isClientDisconnected(Exception e) {
        if (e == null)
            return false;

        String message = e.getMessage();
        Throwable cause = e.getCause();

        // 检查常见的客户端断开连接异常
        if (e instanceof IllegalStateException && message != null &&
                message.contains("already completed")) {
            return true;
        }

        // 检查 AsyncRequestNotUsableException 或其子类
        if (cause instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException) {
            return true;
        }

        // 检查 IO 异常中的连接重置/中止信息
        if (e instanceof java.io.IOException || cause instanceof java.io.IOException) {
            String errorMsg = (cause != null ? cause.getMessage() : message);
            if (errorMsg != null && (errorMsg.contains("Connection reset") ||
                    errorMsg.contains("Connection aborted") ||
                    errorMsg.contains("Broken pipe") ||
                    errorMsg.contains("软件中止了一个已建立的连接") ||
                    errorMsg.contains("failed to flush"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 安全地完成 emitter（带错误）
     */
    private void safeCompleteWithError(SseEmitter emitter, Throwable e, boolean[] connectionClosed) {
        try {
            if (!connectionClosed[0]) {
                connectionClosed[0] = true;
                emitter.completeWithError(e);
            }
        } catch (Exception ex) {
            log.debug("⚠️ Failed to complete emitter with error (already completed): {}", ex.getMessage());
        }
    }

    /**
     * 安全地完成 emitter
     */
    private void safeComplete(SseEmitter emitter, boolean[] connectionClosed) {
        try {
            if (!connectionClosed[0]) {
                connectionClosed[0] = true;
                emitter.complete();
            }
        } catch (Exception ex) {
            log.debug("⚠️ Failed to complete emitter (already completed): {}", ex.getMessage());
        }
    }

    /**
     * 查询路由统计信息
     */
    @GetMapping("/routing-stats")
    public ResponseEntity<RoutingStatsService.RoutingStatsRecord> getRoutingStats() {
        return ResponseEntity.ok(statsService.getStats());
    }

    private String extractIntentFromReason(String reason) {
        if (reason == null)
            return "UNKNOWN";
        if (reason.contains("CODE"))
            return "CODE_GENERATION";
        if (reason.contains("CHINESE") || reason.contains("中文"))
            return "CHINESE_QA";
        if (reason.contains("GENERAL") || reason.contains("通用"))
            return "GENERAL_CHAT";
        return "UNKNOWN";
    }

}
