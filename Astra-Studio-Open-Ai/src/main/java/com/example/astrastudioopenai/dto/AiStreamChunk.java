package com.example.astrastudioopenai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiStreamChunk {
    private String type; // "thinking"、"text"、"usage" 或 "routing_info"
    private String content;
    private UsageInfo usage; // Token使用统计或路由信息（仅特定type时有值）

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
        private String model; // 使用的模型名称
        private int promptTokens; // 输入Token数
        private int completionTokens; // 输出Token数
        private int totalTokens; // 总Token数
        private double estimatedCost; // 预估成本（元）
        private double baselineCost; // 基线成本（如果用默认模型）
        private double savingsRate; // 节省率（0-1）或 isAutoRouted 标志（0/1）
        private double confidence; // 路由置信度（仅 routing_info 时有值）
    }
}
