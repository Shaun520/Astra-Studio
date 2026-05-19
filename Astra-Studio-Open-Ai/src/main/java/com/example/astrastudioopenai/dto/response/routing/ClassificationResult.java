package com.example.astrastudioopenai.dto.response.routing;

public record ClassificationResult(
    String intent,
    double confidence,
    String reason
) {
}
