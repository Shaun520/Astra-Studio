package com.example.astrastudioopenai.service.chat;

import com.example.astrastudioopenai.service.ai.AiCodeHelperService;
import com.example.astrastudioopenai.service.ai.AiCodeHelperServiceFactory;
import com.example.astrastudioopenai.dto.AiStreamChunk;
import com.example.astrastudioopenai.dto.response.routing.AutoRouteResult;
import com.example.astrastudioopenai.dto.response.routing.ClassificationResult;
import com.example.astrastudioopenai.service.routing.AutoRoutingService;
import com.example.astrastudioopenai.service.routing.RoutingStatsService;
import com.example.astrastudioopenai.service.knowledge.RAGRetrievalService;
import com.example.astrastudioopenai.common.utils.SseEmitterHelper;
import com.example.astrastudioopenai.common.utils.MultipartUserMessageBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ChatService {

    @Autowired
    private AiCodeHelperServiceFactory aiServiceFactory;

    @Autowired
    private AutoRoutingService autoRoutingService;

    @Autowired
    private RoutingStatsService statsService;

    @Autowired(required = false)
    private RAGRetrievalService ragRetrievalService;

    @Value("${sse.timeout-ms:300000}")
    private long sseTimeoutMs;

    @Autowired
    @Qualifier("streamExecutor")
    private Executor streamExecutor;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public SseEmitter streamChat(String memoryId, String text, List<String> files,
            boolean deepThink, boolean webSearch, String modelName) {
        return streamChat(memoryId, text, files, deepThink, webSearch, modelName, false);
    }

    public SseEmitter streamChat(String memoryId, String text, List<String> files,
            boolean deepThink, boolean webSearch, String modelName,
            boolean knowledgeBase) {
        log.info("memoryId: {}, text: {}, files: {}, deepThink: {}, webSearch={}, model={}, knowledgeBase={}",
                memoryId, text, files, deepThink, webSearch, modelName, knowledgeBase);

        String resolvedModelName = modelName;
        AutoRouteResult routeResult = null;
        ClassificationResult classificationResult = null;

        try {
            if ("auto".equals(modelName)) {
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
                aiServiceFactory.getService(deepThink, webSearch, modelName, knowledgeBase);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported model: " + modelName
                    + ". Allowed models: glm-5, deepseek-v4-flash, qwen3.6-flash-2026-04-16, auto");
        }

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        final boolean[] connectionClosed = { false };

        List<String> fileList = files == null ? Collections.emptyList()
                : files.stream()
                        .filter(url -> url != null && !url.isBlank())
                        .map(String::trim)
                        .toList();

        String userMessageText = MultipartUserMessageBuilder.buildText(text, fileList);
        final String finalResolvedModelName = resolvedModelName;
        final AutoRouteResult finalRouteResult = routeResult;
        final ClassificationResult finalClassificationResult = classificationResult;

        streamExecutor.execute(() -> {
            try {
                log.info("🚀 Starting TokenStream for memoryId: {}", memoryId);

                sendRoutingInfo(emitter, finalResolvedModelName, finalRouteResult, connectionClosed);

                AiCodeHelperService selectedService = aiServiceFactory.getService(deepThink, webSearch,
                        finalResolvedModelName, knowledgeBase);
                log.info("🤖 AI service config: deepThink={}, webSearch={}, model={}, knowledgeBase={}, autoRoute={}",
                        deepThink, webSearch, finalResolvedModelName, knowledgeBase,
                        finalRouteResult != null && finalRouteResult.isAutoRouted());

                if (finalRouteResult != null) {
                    statsService.recordRouting(finalRouteResult, finalClassificationResult);
                }

                dev.langchain4j.service.TokenStream tokenStream = selectedService.chatWithStream(memoryId,
                        userMessageText);

                subscribeTokenStream(tokenStream, emitter, connectionClosed,
                        knowledgeBase ? text : null);

            } catch (Exception e) {
                log.error("❌ Error in streaming chat", e);
                handleStreamingError(emitter, e, connectionClosed);
            }
        });

        setupEmitterCallbacks(emitter, connectionClosed);
        return emitter;
    }

    private void sendRoutingInfo(SseEmitter emitter, String resolvedModelName, AutoRouteResult routeResult,
            boolean[] connectionClosed) {
        if (routeResult == null || !routeResult.isAutoRouted())
            return;

        try {
            if (!connectionClosed[0]) {
                AiStreamChunk routingChunk = new AiStreamChunk("routing_info", null,
                        new AiStreamChunk.UsageInfo(
                                resolvedModelName, 0, 0, 0, 0, 0,
                                1,
                                routeResult.confidence()));
                String routingJson = objectMapper.writeValueAsString(routingChunk);
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(routingJson)
                        .reconnectTime(3000));
                log.info("📡 Sent routing_info: model={}, confidence={}",
                        resolvedModelName, String.format("%.2f", routeResult.confidence()));
            }
        } catch (IOException e) {
            SseEmitterHelper.handleEmitterError(emitter, e, "routing_info", connectionClosed);
        }
    }

    private void subscribeTokenStream(dev.langchain4j.service.TokenStream tokenStream,
            SseEmitter emitter, boolean[] connectionClosed, String ragQuery) {
        tokenStream
                .onPartialThinking(thinking -> {
                    try {
                        if (!connectionClosed[0]) {
                            log.debug("🤔 Thinking: {}", thinking.text());
                            AiStreamChunk chunk = new AiStreamChunk("thinking", thinking.text());
                            String json = objectMapper.writeValueAsString(chunk);
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(json)
                                    .reconnectTime(3000));
                            log.debug("✅ Sent thinking chunk: {}", thinking);
                        }
                    } catch (IOException e) {
                        SseEmitterHelper.handleEmitterError(emitter, e, "thinking", connectionClosed);
                    }
                })
                .onPartialResponse(content -> {
                    try {
                        if (!connectionClosed[0] && content != null && !content.isEmpty()) {
                            log.debug("💬 Text: {}", content);
                            AiStreamChunk chunk = new AiStreamChunk("text", content);
                            String json = objectMapper.writeValueAsString(chunk);
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(json)
                                    .reconnectTime(3000));
                            log.debug("✅ Sent text chunk: {}", content);
                        }
                    } catch (IOException e) {
                        SseEmitterHelper.handleEmitterError(emitter, e, "text", connectionClosed);
                    }
                })
                .onCompleteResponse(response -> {
                    try {
                        if (!connectionClosed[0]) {
                            if (ragQuery != null && ragRetrievalService != null) {
                                var chunks = ragRetrievalService.retrieve(ragQuery);
                                if (!chunks.isEmpty()) {
                                    AiStreamChunk sourcesChunk = new AiStreamChunk("sources", null);
                                    sourcesChunk.setSources(chunks);
                                    String json = objectMapper.writeValueAsString(sourcesChunk);
                                    emitter.send(SseEmitter.event()
                                            .name("message")
                                            .data(json)
                                            .reconnectTime(3000));
                                    log.info("📡 Sent RAG sources: count={}", chunks.size());
                                }
                            }

                            log.info("✅ Stream completed successfully");
                            emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data("{\"status\":\"done\"}"));
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        log.error("❌ Error completing stream", e);
                        SseEmitterHelper.safeComplete(emitter, connectionClosed);
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
                                    .data(objectMapper.writeValueAsString(Map.of("error", errorMessage))));
                        } catch (IOException e) {
                            log.warn("⚠️ Failed to send error event (client may have disconnected)", e);
                        } finally {
                            SseEmitterHelper.safeCompleteWithError(emitter, error, connectionClosed);
                        }
                    }
                })
                .start();
    }

    private void handleStreamingError(SseEmitter emitter, Exception e, boolean[] connectionClosed) {
        if (!connectionClosed[0]) {
            try {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "Internal server error";
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(objectMapper.writeValueAsString(Map.of("error", errorMessage))));
            } catch (IOException ioException) {
                log.warn("⚠️ Failed to send error event (client may have disconnected)", ioException);
            } finally {
                SseEmitterHelper.safeCompleteWithError(emitter, e, connectionClosed);
            }
        }
    }

    private void setupEmitterCallbacks(SseEmitter emitter, boolean[] connectionClosed) {
        emitter.onCompletion(() -> {
            log.info("🔒 SSE connection completed");
            connectionClosed[0] = true;
        });

        emitter.onTimeout(() -> {
            log.warn("⏰ SSE connection timed out");
            connectionClosed[0] = true;
            emitter.complete();
        });

        emitter.onError((ex) -> {
            log.debug("⚠️ SSE connection error (client disconnected): {}", ex.getMessage());
            connectionClosed[0] = true;
        });
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
