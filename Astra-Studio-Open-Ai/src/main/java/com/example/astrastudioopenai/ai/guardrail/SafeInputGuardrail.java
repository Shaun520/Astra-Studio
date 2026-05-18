package com.example.astrastudioopenai.ai.guardrail;


import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Set;

/**
 * 安全检测输入护轨
 */
public class SafeInputGuardrail implements InputGuardrail {

    private static final Set<String> sensitiveWords = Set.of("kill", "evil");

    /**
     * 检测用户输入是否安全
     */
    private static String extractTextForGuardrail(UserMessage userMessage) {
        if (userMessage.hasSingleText()) {
            return userMessage.singleText();
        }
        StringBuilder sb = new StringBuilder();
        for (var content : userMessage.contents()) {
            if (content instanceof TextContent textContent) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(textContent.text());
            }
        }
        return sb.toString();
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        // 获取用户输入并转换为小写以确保大小写不敏感（多模态消息只扫描文本片段）
        String inputText = extractTextForGuardrail(userMessage).toLowerCase();
        if (inputText.isEmpty()) {
            return success();
        }
        // 使用正则表达式分割输入文本为单词
        String[] words = inputText.split("\\W+");
        // 遍历所有单词，检查是否存在敏感词
        for (String word : words) {
            if (sensitiveWords.contains(word)) {
                return fatal("Sensitive word detected: " + word);
            }
        }
        return success();
    }
}