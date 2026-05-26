package com.example.astrastudioopenai.service.tools.document;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class ImageAnalysisResult {
    private final String taskType;
    private final String analysis;
    private final Map<String, Object> metadata;

    public ImageAnalysisResult(String taskType, String analysis, Map<String, Object> metadata) {
        this.taskType = taskType;
        this.analysis = analysis;
        this.metadata = metadata != null ? metadata : new java.util.HashMap<>();
    }

}