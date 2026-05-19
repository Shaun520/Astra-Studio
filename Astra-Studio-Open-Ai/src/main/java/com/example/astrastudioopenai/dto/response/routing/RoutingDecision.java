package com.example.astrastudioopenai.dto.response.routing;

public record RoutingDecision(
    String selectedModel,
    double confidence,
    String reason
) {
}
