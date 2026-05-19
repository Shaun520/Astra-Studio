package com.example.astrastudioopenai.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
public class SseEmitterHelper {

    public static void handleEmitterError(SseEmitter emitter, Exception e, String context, boolean[] connectionClosed) {
        if (isClientDisconnected(e)) {
            if (!connectionClosed[0]) {
                log.info("📴 Client disconnected during {}, stopping stream: {}", context, e.getMessage());
                connectionClosed[0] = true;
            }
        } else {
            log.error("❌ Error sending {} chunk", context, e);
            safeCompleteWithError(emitter, e, connectionClosed);
        }
    }

    public static boolean isClientDisconnected(Exception e) {
        if (e == null)
            return false;

        String message = e.getMessage();
        Throwable cause = e.getCause();

        if (e instanceof IllegalStateException && message != null &&
                message.contains("already completed")) {
            return true;
        }

        if (cause instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException) {
            return true;
        }

        if (e instanceof java.io.IOException || cause instanceof java.io.IOException) {
            String errorMsg = (cause != null ? cause.getMessage() : message);
            if (errorMsg != null && (errorMsg.contains("Connection reset") ||
                    errorMsg.contains("Connection aborted") ||
                    errorMsg.contains("Broken pipe") ||
                    errorMsg.contains("软件中止了一个已建立的连接") ||
                    errorMsg.contains("failed to flush"))) {
                return true;
            }
        }

        return false;
    }

    public static void safeCompleteWithError(SseEmitter emitter, Throwable e, boolean[] connectionClosed) {
        try {
            if (!connectionClosed[0]) {
                connectionClosed[0] = true;
                emitter.completeWithError(e);
            }
        } catch (Exception ex) {
            log.debug("⚠️ Failed to complete emitter with error (already completed): {}", ex.getMessage());
        }
    }

    public static void safeComplete(SseEmitter emitter, boolean[] connectionClosed) {
        try {
            if (!connectionClosed[0]) {
                connectionClosed[0] = true;
                emitter.complete();
            }
        } catch (Exception ex) {
            log.debug("⚠️ Failed to complete emitter (already completed): {}", ex.getMessage());
        }
    }
}
