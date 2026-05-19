package com.example.astrastudioopenai.dto;

import com.example.astrastudioopenai.dto.response.RetrievedChunk;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiStreamChunk {
    private String type;
    private String content;
    private UsageInfo usage;
    private List<RetrievedChunk> sources;

    public AiStreamChunk(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public AiStreamChunk(String type, String content, UsageInfo usage) {
        this.type = type;
        this.content = content;
        this.usage = usage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageInfo {
        private String model;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private double estimatedCost;
        private double baselineCost;
        private double savingsRate;
        private double confidence;
    }
}
