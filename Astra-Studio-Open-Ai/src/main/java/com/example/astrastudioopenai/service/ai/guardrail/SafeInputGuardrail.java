package com.example.astrastudioopenai.service.ai.guardrail;


import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Set;

public class SafeInputGuardrail implements InputGuardrail {

    private static final Set<String> sensitiveWords = Set.of("kill", "evil");

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
        String inputText = extractTextForGuardrail(userMessage).toLowerCase();
        if (inputText.isEmpty()) {
            return success();
        }
        String[] words = inputText.split("\\W+");
        for (String word : words) {
            if (sensitiveWords.contains(word)) {
                return fatal("Sensitive word detected: " + word);
            }
        }
        return success();
    }
}
