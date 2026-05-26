package com.example.astrastudioopenai.service.chat;

import com.example.astrastudioopenai.service.ai.AiCodeHelperService;
import com.example.astrastudioopenai.service.ai.AiCodeHelperServiceFactory;
import com.example.astrastudioopenai.dto.AiStreamChunk;
import com.example.astrastudioopenai.dto.response.routing.AutoRouteResult;
import com.example.astrastudioopenai.dto.response.routing.ClassificationResult;
import com.example.astrastudioopenai.service.routing.AutoRoutingService;
import com.example.astrastudioopenai.service.routing.RoutingStatsService;
import com.example.astrastudioopenai.service.knowledge.RAGRetrievalService;
import com.example.astrastudioopenai.service.conversation.ConversationQueryService;
import com.example.astrastudioopenai.service.conversation.ConversationPersistenceService;
import com.example.astrastudioopenai.service.title.TitleGeneratorService;
import com.example.astrastudioopenai.common.utils.SseEmitterHelper;
import com.example.astrastudioopenai.common.utils.MultipartUserMessageBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private ConversationQueryService conversationQueryService;

    @Autowired(required = false)
    private ConversationPersistenceService persistenceService;

    @Autowired(required = false)
    private TitleGeneratorService titleGeneratorService;

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
        return streamChat(memoryId, text, files, deepThink, webSearch, modelName, knowledgeBase, null);
    }

    public SseEmitter streamChat(String memoryId, String text, List<String> files,
            boolean deepThink, boolean webSearch, String modelName,
            boolean knowledgeBase, List<String> selectedTools) {
        log.info(
                "memoryId: {}, text: {}, files: {}, deepThink: {}, webSearch={}, model={}, knowledgeBase={}, selectedTools={}",
                memoryId, text, files, deepThink, webSearch, modelName, knowledgeBase, selectedTools);

        try {
            if (!conversationQueryService.conversationExists(memoryId)) {
                log.info("Auto-creating conversation for memoryId: {}", memoryId);
                conversationQueryService.createConversation(memoryId, modelName);
            }
        } catch (Exception e) {
            log.error("❌ Failed to auto-create conversation for memoryId: {}. Chat aborted to prevent data loss.",
                    memoryId, e);
            throw new RuntimeException("Failed to create conversation for memoryId: " + memoryId
                    + ". Cannot proceed with chat without valid conversation context.", e);
        }

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
                aiServiceFactory.getService(deepThink, webSearch, modelName, knowledgeBase, selectedTools);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported model: " + modelName
                    + ". Allowed models: glm-5.1, deepseek-v4-flash, qwen3.6-flash-2026-04-16, qwen3.7-max-2026-05-17, auto");
        }

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        final boolean[] connectionClosed = { false };
        final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> heartbeatFuture = null;

        try {
            heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
                try {
                    if (!connectionClosed[0]) {
                        emitter.send(SseEmitter.event().name("heartbeat").data("{\"type\":\"heartbeat\"}"));
                        log.debug("💓 SSE heartbeat sent");
                    }
                } catch (Exception e) {
                    log.debug("Heartbeat failed (connection likely closed): {}", e.getMessage());
                }
            }, 15, 15, TimeUnit.SECONDS);

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

                    if (persistenceService != null) {
                        persistenceService.saveUserMessage(memoryId, userMessageText);
                    }

                    sendRoutingInfo(emitter, finalResolvedModelName, finalRouteResult, connectionClosed);

                    AiCodeHelperService selectedService = aiServiceFactory.getService(deepThink, webSearch,
                            finalResolvedModelName, knowledgeBase, selectedTools);
                    log.info(
                            "🤖 AI service config: deepThink={}, webSearch={}, model={}, knowledgeBase={}, selectedTools={}, autoRoute={}",
                            deepThink, webSearch, finalResolvedModelName, knowledgeBase, selectedTools,
                            finalRouteResult != null && finalRouteResult.isAutoRouted());

                    if (finalRouteResult != null) {
                        statsService.recordRouting(finalRouteResult, finalClassificationResult);
                    }

                    dev.langchain4j.service.TokenStream tokenStream = selectedService.chatWithStream(memoryId,
                            userMessageText);

                    subscribeTokenStream(tokenStream, emitter, connectionClosed,
                            knowledgeBase ? text : null, memoryId, userMessageText, finalResolvedModelName);

                } catch (Exception e) {
                    log.error("❌ Error in streaming chat", e);
                    handleStreamingError(emitter, e, connectionClosed);
                }
            });

            setupEmitterCallbacks(emitter, connectionClosed, heartbeatExecutor, heartbeatFuture);
        } catch (Exception e) {
            stopHeartbeat(heartbeatExecutor, heartbeatFuture);
            throw e;
        }

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
            SseEmitter emitter, boolean[] connectionClosed, String ragQuery,
            String memoryId, String userMessageText, String modelName) {

        final StringBuilder thinkingBuffer = new StringBuilder();
        final StringBuilder contentBuffer = new StringBuilder();

        tokenStream
                .onPartialThinking(thinking -> {
                    try {
                        if (!connectionClosed[0]) {
                            log.debug("🤔 Thinking: {}", thinking.text());
                            if (thinking.text() != null) {
                                thinkingBuffer.append(thinking.text());
                            }
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
                            contentBuffer.append(content);
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
                    } finally {
                        saveCollectedContent(memoryId, response, thinkingBuffer, contentBuffer);
                        generateTitleAsync(memoryId, userMessageText, response);
                    }
                })
                .onError(error -> {
                    log.error("❌ Stream error: ", error);

                    // 💾 即使出错也要保存已收集的内容
                    savePartialContentOnError(memoryId, thinkingBuffer, contentBuffer, error);

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

    /**
     * 保存正常完成时收集到的完整内容
     */
    private void saveCollectedContent(String memoryId, Object response,
            StringBuilder thinkingBuffer, StringBuilder contentBuffer) {
        try {
            String assistantContent = extractResponseText(response);

            // 如果 extractResponseText 返回空，使用收集器中的内容作为后备
            if ((assistantContent == null || assistantContent.isEmpty()) && contentBuffer.length() > 0) {
                assistantContent = contentBuffer.toString().trim();
            }

            String collectedThinking = thinkingBuffer.length() > 0 ? thinkingBuffer.toString().trim() : null;

            if (assistantContent != null && !assistantContent.isEmpty() && persistenceService != null) {
                persistenceService.saveAssistantMessage(memoryId, assistantContent, collectedThinking);
                log.info("💾 Saved complete assistant message with thinkingContent (length={}) for memoryId={}",
                        collectedThinking != null ? collectedThinking.length() : 0, memoryId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to save complete message for memoryId={}: {}", memoryId, e.getMessage(), e);
        }
    }

    /**
     * 保存错误发生时已收集的部分内容（确保不丢失上下文）
     */
    private void savePartialContentOnError(String memoryId,
            StringBuilder thinkingBuffer, StringBuilder contentBuffer, Throwable error) {
        if (persistenceService == null) {
            log.warn("⚠️ Cannot save partial content: persistenceService is null");
            return;
        }

        try {
            String partialContent = contentBuffer.length() > 0 ? contentBuffer.toString().trim() : null;
            String partialThinking = thinkingBuffer.length() > 0 ? thinkingBuffer.toString().trim() : null;

            if (partialContent != null && !partialContent.isEmpty()) {
                // 在内容末尾添加错误标记
                String contentWithMarker = partialContent + "\n\n[生成中断: " +
                        (error.getMessage() != null ? error.getMessage() : "未知错误") + "]";

                persistenceService.saveAssistantMessage(memoryId, contentWithMarker, partialThinking);
                log.warn("💾 Saved partial assistant message ({} chars, thinking={} chars) after error for memoryId={}",
                        partialContent.length(),
                        partialThinking != null ? partialThinking.length() : 0,
                        memoryId);
            } else {
                log.info("ℹ️ No partial content to save for memoryId={}", memoryId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to save partial content for memoryId={}: {}", memoryId, e.getMessage(), e);
        }
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

    private void setupEmitterCallbacks(SseEmitter emitter, boolean[] connectionClosed,
            ScheduledExecutorService heartbeatExecutor, ScheduledFuture<?> heartbeatFuture) {
        emitter.onCompletion(() -> {
            log.info("🔒 SSE connection completed");
            connectionClosed[0] = true;
            stopHeartbeat(heartbeatExecutor, heartbeatFuture);
        });

        emitter.onTimeout(() -> {
            log.warn("⏰ SSE connection timed out");
            connectionClosed[0] = true;
            stopHeartbeat(heartbeatExecutor, heartbeatFuture);
            emitter.complete();
        });

        emitter.onError((ex) -> {
            log.debug("⚠️ SSE connection error (client disconnected): {}", ex.getMessage());
            connectionClosed[0] = true;
            stopHeartbeat(heartbeatExecutor, heartbeatFuture);
        });
    }

    private void stopHeartbeat(ScheduledExecutorService executor, ScheduledFuture<?> future) {
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        log.debug("💓 Heartbeat stopped");
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

    private void generateTitleAsync(String memoryId, String userMessage, Object response) {
        if (titleGeneratorService == null) {
            return;
        }

        streamExecutor.execute(() -> {
            try {
                String assistantReply = extractResponseText(response);
                String generatedTitle = titleGeneratorService.generate(userMessage,
                        assistantReply != null ? assistantReply : "");
                conversationQueryService.updateTitle(memoryId, generatedTitle);
                log.info("📝 Auto-generated title for memoryId={}: {}", memoryId, generatedTitle);
            } catch (Exception e) {
                log.warn("Failed to auto-generate title for memoryId={}: {}", memoryId, e.getMessage());
            }
        });
    }

    private void saveConversationContext(String memoryId, String userMessageText, Object response, String modelName) {
        if (persistenceService == null) {
            log.warn("⚠️ ConversationPersistenceService is null, skipping message persistence for memoryId={}",
                    memoryId);
            return;
        }
        try {
            com.example.astrastudioopenai.service.conversation.ConversationContext ctx = new com.example.astrastudioopenai.service.conversation.ConversationContext();
            ctx.setMemoryId(memoryId);
            ctx.setModelName(modelName);

            com.example.astrastudioopenai.dto.response.MessageEntry userEntry = new com.example.astrastudioopenai.dto.response.MessageEntry(
                    "user", userMessageText, 0);
            ctx.getMessages().add(userEntry);

            String assistantContent = extractResponseText(response);
            if (assistantContent != null && !assistantContent.isEmpty()) {
                com.example.astrastudioopenai.dto.response.MessageEntry assistantEntry = new com.example.astrastudioopenai.dto.response.MessageEntry(
                        "assistant", assistantContent, 1);
                ctx.getMessages().add(assistantEntry);
            }

            persistenceService.saveContext(memoryId, ctx);
            log.info("💾 Saved conversation context for memoryId={}, messages={}", memoryId, ctx.getMessageCount());
        } catch (Exception e) {
            log.warn("Failed to save conversation context for memoryId={}: {}", memoryId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractResponseText(Object response) {
        if (response == null) {
            return null;
        }
        try {
            ChatResponse chatResponse = (ChatResponse) response;
            dev.langchain4j.data.message.AiMessage aiMessage = chatResponse.aiMessage();
            if (aiMessage != null) {
                return aiMessage.text();
            }
        } catch (Exception e) {
            log.debug("Failed to extract text from ChatResponse, fallback to toString: {}", e.getMessage());
        }
        return response.toString();
    }
}
