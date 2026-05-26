package com.example.astrastudioopenai.service.tools.document;

import lombok.Getter;

@Getter
public class ExecutionResult {
    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final long durationMs;

    public ExecutionResult(String stdout, String stderr, int exitCode, long durationMs) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
