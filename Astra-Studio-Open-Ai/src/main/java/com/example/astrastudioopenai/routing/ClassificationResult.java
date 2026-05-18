package com.example.astrastudioopenai.routing;

public record ClassificationResult(
    String intent,
    double confidence,
    String reason
) {
}
