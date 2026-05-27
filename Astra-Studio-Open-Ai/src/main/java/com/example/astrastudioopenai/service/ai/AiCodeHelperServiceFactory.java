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
import com.example.astrastudioopenai.service.tools.ToolRegistry;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AiCodeHelperServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(AiCodeHelperServiceFactory.class);

    private static final Set<String> ALLOWED_MODELS = Set.of(
            "glm-5.1",
            "deepseek-v4-flash",
            "qwen3.6-flash-2026-04-16",
            "qwen3.7-max-2026-05-17");

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

    @Value("${tools.enabled:true}")
    private boolean toolsEnabled;

    private final ConcurrentHashMap<String, AiCodeHelperService> serviceCache = new ConcurrentHashMap<>();

    public AiCodeHelperService getService(boolean deepThink, boolean webSearch, String modelName) {
        return getService(deepThink, webSearch, modelName, false);
    }

    public AiCodeHelperService getService(boolean deepThink, boolean webSearch, String modelName,
            boolean knowledgeBase) {
        return getService(deepThink, webSearch, modelName, knowledgeBase, null);
    }

    public AiCodeHelperService getService(boolean deepThink, boolean webSearch, String modelName,
            boolean knowledgeBase, List<String> selectedToolNames) {
        if (!ALLOWED_MODELS.contains(modelName)) {
            throw new IllegalArgumentException(
                    "Unsupported model: " + modelName + ". Allowed models: " + ALLOWED_MODELS);
        }

        String toolsKey = (selectedToolNames != null && !selectedToolNames.isEmpty())
                ? selectedToolNames.stream().sorted().collect(java.util.stream.Collectors.joining(","))
                : "none";
        String cacheKey = String.format("deepThink:%s,webSearch:%s,model:%s,rag:%s,tools:[%s]",
                deepThink, webSearch, modelName, knowledgeBase, toolsKey);

        return serviceCache.computeIfAbsent(cacheKey, key -> {
            log.info("🏭 Creating new AI service with config: {}", key);
            return buildService(deepThink, webSearch, modelName, knowledgeBase, selectedToolNames);
        });
    }

    private AiCodeHelperService buildService(boolean deepThink, boolean webSearch, String modelName,
            boolean knowledgeBase, List<String> selectedToolNames) {
        int timeoutSeconds = calculateTimeout(deepThink, webSearch, knowledgeBase, selectedToolNames);
        OpenAiStreamingChatModel streamingModel = createModel(deepThink, timeoutSeconds, modelName);

        var builder = AiServices.builder(AiCodeHelperService.class)
                .chatModel(openAiChatModel)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10));

        if (toolsEnabled && selectedToolNames != null && !selectedToolNames.isEmpty()) {
            var localTools = ToolRegistry.getInstance().getToolsByNames(selectedToolNames);
            if (!localTools.isEmpty()) {
                builder.tools(localTools.toArray(new Object[0]));
                log.info("🔧 Injected {} selected tools into AI service: {}", localTools.size(), selectedToolNames);
            }
        }

        if (webSearch) {
            builder.toolProvider(mcpToolProvider);
        }

        if (knowledgeBase && ragContentRetriever != null) {
            builder.contentRetriever(ragContentRetriever);
        }

        return builder.build();
    }

    private int calculateTimeout(boolean deepThink, boolean webSearch, boolean knowledgeBase,
            List<String> selectedToolNames) {
        int baseTimeout = 120;
        if (deepThink)
            baseTimeout += 60;
        if (webSearch)
            baseTimeout += 30;
        if (knowledgeBase)
            baseTimeout += 30;
        if (selectedToolNames != null && !selectedToolNames.isEmpty())
            baseTimeout += 180;
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

    public OpenAiStreamingChatModel createParameterizedModel(String modelName, boolean deepThink,
            Double temperature, Integer maxTokens, Double topP) {
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .returnThinking(deepThink)
                .timeout(Duration.ofSeconds(300))
                .logRequests(true)
                .logResponses(true);
        if (temperature != null) builder.temperature(temperature);
        if (maxTokens != null) builder.maxTokens(maxTokens);
        if (topP != null) builder.topP(topP);
        return builder.build();
    }

    @Bean
    public AiCodeHelperService defaultAiService() {
        return getService(false, false, defaultModelName);
    }
}
