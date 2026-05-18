package com.example.astrastudioopenai.ai;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AiCodeHelperServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(AiCodeHelperServiceFactory.class);

    // 模型白名单：只允许使用预定义的模型名称
    private static final Set<String> ALLOWED_MODELS = Set.of(
            "glm-5",
            "deepseek-v4-flash",
            "qwen3.6-flash-2026-04-16");

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

    // 服务实例缓存：避免重复创建相同配置的服务
    private final ConcurrentHashMap<String, AiCodeHelperService> serviceCache = new ConcurrentHashMap<>();

    /**
     * 动态获取AI服务实例（带缓存）
     *
     * @param deepThink 是否启用深度思考（思维链）
     * @param webSearch 是否启用联网搜索工具
     * @param modelName 使用的模型名称（必须在白名单中）
     * @return 匹配配置的AiCodeHelperService实例
     */
    public AiCodeHelperService getService(boolean deepThink, boolean webSearch, String modelName) {
        if (!ALLOWED_MODELS.contains(modelName)) {
            throw new IllegalArgumentException(
                    "Unsupported model: " + modelName + ". Allowed models: " + ALLOWED_MODELS);
        }

        String cacheKey = String.format("deepThink:%s,webSearch:%s,model:%s", deepThink, webSearch, modelName);

        return serviceCache.computeIfAbsent(cacheKey, key -> {
            log.info("🏭 Creating new AI service with config: {}", key);
            return buildService(deepThink, webSearch, modelName);
        });
    }

    /**
     * 构建AI服务实例
     * 核心扩展点：新增功能只需在此处添加条件判断
     */
    private AiCodeHelperService buildService(boolean deepThink, boolean webSearch, String modelName) {
        int timeoutSeconds = calculateTimeout(deepThink, webSearch);
        OpenAiStreamingChatModel streamingModel = createModel(deepThink, timeoutSeconds, modelName);

        var builder = AiServices.builder(AiCodeHelperService.class)
                .chatModel(openAiChatModel)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10));

        // 按需添加工具（可无限扩展）
        if (webSearch) {
            builder.toolProvider(mcpToolProvider); // 联网搜索
        }

        return builder.build();
    }

    /**
     * 计算超时时间（根据启用的功能动态调整）
     */
    private int calculateTimeout(boolean deepThink, boolean webSearch) {
        int baseTimeout = 30; // 基础超时
        if (deepThink)
            baseTimeout += 30; // 深度思考需要更多时间
        if (webSearch)
            baseTimeout += 15; // 联网搜索需要额外时间
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
        return getService(false, false, defaultModelName); // 默认服务：普通模式、不联网、默认模型
    }
}
