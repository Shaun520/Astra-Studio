package com.example.astrastudioopenai.routing;

public record AutoRouteResult(
    String modelName,
    boolean isAutoRouted,
    double confidence,
    String reason
) {
    public static AutoRouteResult manual(String modelName) {
        return new AutoRouteResult(modelName, false, 0.0, "用户手动选择");
    }

    public static AutoRouteResult auto(RoutingDecision decision) {
        return new AutoRouteResult(decision.selectedModel(), true, decision.confidence(), decision.reason());
    }

    public static AutoRouteResult fallback(String defaultModel, ClassificationResult classification) {
        return new AutoRouteResult(defaultModel, false, classification.confidence(),
            "置信度过低，使用默认模型 (最佳意图: " + classification.intent() + ")");
    }
}
