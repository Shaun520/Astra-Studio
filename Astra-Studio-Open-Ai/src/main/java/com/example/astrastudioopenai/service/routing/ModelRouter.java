package com.example.astrastudioopenai.service.routing;

import com.example.astrastudioopenai.dto.response.routing.ClassificationResult;
import com.example.astrastudioopenai.dto.response.routing.RoutingDecision;
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
