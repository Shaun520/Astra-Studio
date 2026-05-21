package com.example.astrastudioopenai.service.title;

import com.example.astrastudioopenai.service.conversation.ConversationQueryService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@Slf4j
public class TitleGeneratorService {

    @Autowired
    private ConversationQueryService conversationQueryService;

    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Value("${conversation.title.max-length:30}")
    private int maxLength;

    @Value("${conversation.title.auto-generate:true}")
    private boolean autoGenerateEnabled;

    @Value("${conversation.title.model-name:deepseek-v4-flash}")
    private String titleModelName;

    @Value("${conversation.title.timeout-seconds:10}")
    private int timeoutSeconds;

    public String generate(String userMessage, String assistantReply) {
        if (!autoGenerateEnabled) {
            return generateFallback(userMessage);
        }

        try {
            return generateWithLLM(userMessage, assistantReply);
        } catch (Exception e) {
            log.warn("LLM title generation failed, using fallback: {}", e.getMessage());
            return generateFallback(userMessage);
        }
    }

    private String generateWithLLM(String userMessage, String assistantReply) {
        String prompt = buildPrompt(userMessage, assistantReply);

        log.debug("Generating title with LLM for message: {}", truncateForLog(userMessage));

        String generatedTitle = callLLMAPI(prompt);

        if (generatedTitle == null || generatedTitle.trim().isEmpty()) {
            log.warn("LLM returned empty title, using fallback");
            return generateFallback(userMessage);
        }

        String cleaned = cleanTitle(generatedTitle.trim());
        log.info("Generated title via LLM: {}", cleaned);
        return cleaned;
    }

    public String generateFallback(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "新对话";
        }

        String text = userMessage.trim();

        text = cleanSpecialCharacters(text);

        int maxChars = maxLength * 2;
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars);
        }

        if (text.length() > maxLength) {
            text = text.substring(0, maxLength) + "...";
        }

        log.debug("Generated fallback title: {}", text);
        return text.isEmpty() ? "新对话" : text;
    }

    private String buildPrompt(String userMessage, String assistantReply) {
        return String.format(
                "请根据以下对话内容生成一个简洁的标题（不超过%d个字符）。" +
                        "只返回标题文字，不要加引号或其他标记。\n\n" +
                        "用户: %s\n\n" +
                        "助手: %s",
                maxLength,
                truncateForPrompt(userMessage),
                truncateForPrompt(assistantReply));
    }

    private String callLLMAPI(String prompt) {
        try {
            log.debug("🎯 Calling LLM API for title generation with model: {}", titleModelName);

            UserMessage userMessage = UserMessage.from(prompt);
            ChatResponse response = openAiChatModel.chat(userMessage);

            if (response == null || response.aiMessage() == null) {
                log.warn("LLM returned null response for title generation");
                return null;
            }

            String generatedTitle = response.aiMessage().text();

            if (generatedTitle == null || generatedTitle.trim().isEmpty()) {
                log.warn("LLM returned empty text for title generation");
                return null;
            }

            log.info("✅ LLM title generation successful: {}", truncateForLog(generatedTitle));
            return generatedTitle.trim();

        } catch (Exception e) {
            log.error("❌ Failed to call LLM API for title generation: {}", e.getMessage(), e);
            return null;
        }
    }

    private String cleanTitle(String title) {
        title = title.replaceAll("^[\"'\\`]+|[\"'\\`]+$", "");

        title = cleanSpecialCharacters(title);

        if (title.length() > maxLength) {
            title = title.substring(0, maxLength);
        }

        return title.isEmpty() ? "新对话" : title;
    }

    private String cleanSpecialCharacters(String text) {
        Pattern pattern = Pattern.compile(
                "[^\\u4e00-\\u9fa5a-zA-Z0-9\\s\\uff0c\\u3002\\uff01\\uff1f\\u3001\\uff1a\\uff1b\\u201c\\u201d\\u2018\\u2019\\uff08\\uff09]");
        return pattern.matcher(text).replaceAll("").trim();
    }

    private String truncateForLog(String text) {
        if (text == null)
            return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }

    private String truncateForPrompt(String text) {
        if (text == null)
            return "";
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
