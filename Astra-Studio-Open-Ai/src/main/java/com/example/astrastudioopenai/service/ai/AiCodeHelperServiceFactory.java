package com.example.astrastudioopenai.service.ai;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import com.example.astrastudioopenai.service.knowledge.RAGRetrievalService;
import com.example.astrastudioopenai.service.conversation.ConversationPersistenceService;
import com.example.astrastudioopenai.service.knowledge.DocumentETLPipeline;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AiCodeHelperServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(AiCodeHelperServiceFactory.class);

    private static final Set<String> ALLOWED_MODELS = Set.of(
            "glm-5",
            "deepseek-v4-flash",
            "qwen3.6-flash-2026-04-16");

    @Autowired(required = false)
    private RAGRetrievalService ragRetrievalService;

    @Autowired(required = false)
    private ConversationPersistenceService conversationPersistenceService;

    @Autowired(required = false)
    private DocumentETLPipeline documentETLPipeline;

    @Autowired(required = false)
    private ContentRetriever ragContentRetriever;

    @Resource
    private OpenAiChatModel openAiChatModel;
    @Resource
    private McpToolProvider mcpToolProvider;

    @Value("${langchain4j.open-ai.streaming-chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.streaming-chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.streaming-chat-model.model-name}")
    private String defaultModelName;

    private final ConcurrentHashMap<String, AiCodeHelperService> serviceCache = new ConcurrentHashMap<>();

    public AiCodeHelperService getService(boolean deepThink, boolean webSearch, String modelName) {
        return getService(deepThink, webSearch, modelName, false);
    }

    public AiCodeHelperService getService(boolean deepThink, boolean webSearch, String modelName,
            boolean knowledgeBase) {
        if (!ALLOWED_MODELS.contains(modelName)) {
            throw new IllegalArgumentException(
                    "Unsupported model: " + modelName + ". Allowed models: " + ALLOWED_MODELS);
        }

        String cacheKey = String.format("deepThink:%s,webSearch:%s,model:%s,rag:%s",
                deepThink, webSearch, modelName, knowledgeBase);

        return serviceCache.computeIfAbsent(cacheKey, key -> {
            log.info("🏭 Creating new AI service with config: {}", key);
            return buildService(deepThink, webSearch, modelName, knowledgeBase);
        });
    }

    private AiCodeHelperService buildService(boolean deepThink, boolean webSearch, String modelName,
            boolean knowledgeBase) {
        int timeoutSeconds = calculateTimeout(deepThink, webSearch, knowledgeBase);
        OpenAiStreamingChatModel streamingModel = createModel(deepThink, timeoutSeconds, modelName);

        var builder = AiServices.builder(AiCodeHelperService.class)
                .chatModel(openAiChatModel)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10));

        if (webSearch) {
            builder.toolProvider(mcpToolProvider);
        }

        if (knowledgeBase && ragContentRetriever != null) {
            builder.contentRetriever(ragContentRetriever);
        }

        return builder.build();
    }

    private int calculateTimeout(boolean deepThink, boolean webSearch, boolean knowledgeBase) {
        int baseTimeout = 30;
        if (deepThink)
            baseTimeout += 30;
        if (webSearch)
            baseTimeout += 15;
        if (knowledgeBase)
            baseTimeout += 10;
        return baseTimeout;
    }

    private OpenAiStreamingChatModel createModel(boolean deepThink, int timeoutSeconds, String modelName) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .returnThinking(deepThink)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public AiCodeHelperService defaultAiService() {
        return getService(false, false, defaultModelName);
    }
}
