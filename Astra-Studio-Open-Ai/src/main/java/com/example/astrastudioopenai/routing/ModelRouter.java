package com.example.astrastudioopenai.routing;

import org.springframework.stereotype.Service;

@Service
public class ModelRouter {

    public RoutingDecision route(ClassificationResult classification) {
        String targetModel = switch (classification.intent()) {
            case "CODE_GENERATION" -> "deepseek-v4-flash";
            case "CHINESE_QA" -> "qwen3.6-flash-2026-04-16";
            default -> "glm-5";
        };

        return new RoutingDecision(targetModel, classification.confidence(), classification.reason());
    }
}
