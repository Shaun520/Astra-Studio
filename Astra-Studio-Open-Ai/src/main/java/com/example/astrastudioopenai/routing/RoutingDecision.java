package com.example.astrastudioopenai.routing;

public record RoutingDecision(
    String selectedModel,
    double confidence,
    String reason
) {
}
