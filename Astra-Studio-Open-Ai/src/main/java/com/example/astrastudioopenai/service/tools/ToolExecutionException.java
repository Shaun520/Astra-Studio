package com.example.astrastudioopenai.service.tools;

import lombok.Getter;

@Getter
public class ToolExecutionException extends RuntimeException {
    private final String toolName;

    public ToolExecutionException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
    }

    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
    }

}
