package com.example.astrastudioopenai.service.tools;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class ToolCallTracker {
    
    private static final String MDC_TOOL_NAME = "toolName";
    private static final String MDC_MEMORY_ID = "memoryId";
    private static final String MDC_START_TIME = "startTime";
    private static final String MDC_STATUS = "status";
    private static final String MDC_DURATION_MS = "durationMs";

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    public ToolCallContext startTracking(String toolName, String memoryId) {
        totalCalls.incrementAndGet();
        
        MDC.put(MDC_TOOL_NAME, toolName);
        MDC.put(MDC_MEMORY_ID, memoryId != null ? memoryId : "unknown");
        MDC.put(MDC_START_TIME, java.time.LocalDateTime.now().toString());
        
        return new ToolCallContext(toolName, memoryId, System.currentTimeMillis());
    }

    public void endTracking(ToolCallContext context, boolean success, Throwable error) {
        long durationMs = System.currentTimeMillis() - context.startTime();
        
        if (success) {
            successCount.incrementAndGet();
            MDC.put(MDC_STATUS, "SUCCESS");
        } else {
            failureCount.incrementAndGet();
            MDC.put(MDC_STATUS, "ERROR");
            
            if (error != null) {
                logSanitizedError(error);
            }
        }
        
        MDC.put(MDC_DURATION_MS, String.valueOf(durationMs));
        totalDurationMs.addAndGet(durationMs);
        
        logStructuredMessage(context, durationMs, success);
        
        MDC.remove(MDC_TOOL_NAME);
        MDC.remove(MDC_MEMORY_ID);
        MDC.remove(MDC_START_TIME);
        MDC.remove(MDC_STATUS);
        MDC.remove(MDC_DURATION_MS);
    }

    private void logStructuredMessage(ToolCallContext context, long durationMs, boolean success) {
        if (success) {
            org.slf4j.LoggerFactory.getLogger(ToolCallTracker.class).info(
                    "[toolName={}] [memoryId={}] [startTime={}] [endTime={}] [status=SUCCESS] [durationMs={}]", 
                    context.toolName(),
                    context.memoryId(),
                    java.time.Instant.ofEpochMilli(context.startTime()),
                    java.time.Instant.now(),
                    durationMs
            );
        } else {
            org.slf4j.LoggerFactory.getLogger(ToolCallTracker.class).error(
                    "[toolName={}] [memoryId={}] [startTime={}] [endTime={}] [status=ERROR] [durationMs={}]", 
                    context.toolName(),
                    context.memoryId(),
                    java.time.Instant.ofEpochMilli(context.startTime()),
                    java.time.Instant.now(),
                    durationMs
            );
        }
    }

    private void logSanitizedError(Throwable error) {
        String message = error.getMessage();
        if (message != null) {
            message = sanitizeSensitiveInfo(message);
        }
        
        org.slf4j.LoggerFactory.getLogger(ToolCallTracker.class).error(
                "Tool execution failed: {}", 
                message,
                error
        );
    }

    private String sanitizeSensitiveInfo(String message) {
        return message
                .replaceAll("(?i)(api[_-]?key|token|password|secret)[=:]['\"][^'\"]+['\"]", "$1=***")
                .replaceAll("(?i)(Bearer\\s+)[\\w-]+", "$1***")
                .replaceAll("(?i)(access_token=)[^&\\s]+", "$1***");
    }

    public TrackingStats getStats() {
        return new TrackingStats(
                totalCalls.get(),
                successCount.get(),
                failureCount.get(),
                totalDurationMs.get()
        );
    }

    public void resetStats() {
        totalCalls.set(0);
        successCount.set(0);
        failureCount.set(0);
        totalDurationMs.set(0);
    }

    public record ToolCallContext(String toolName, String memoryId, long startTime) {

    }

    public record TrackingStats(
            long totalCalls,
            long successCount,
            long failureCount,
            long totalDurationMs
    ) {
        public double getSuccessRate() {
            return totalCalls > 0 ? (double) successCount / totalCalls : 0.0;
        }

        public double getAvgDurationMs() {
            return totalCalls > 0 ? (double) totalDurationMs / totalCalls : 0.0;
        }
    }
}
