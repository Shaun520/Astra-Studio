package com.example.astrastudioopenai.dto.request;

import lombok.Data;

@Data
public class CreateConversationRequest {
    private String memoryId;
    private String modelName;
}
