package com.example.astrastudioopenai.controller;

import com.example.astrastudioopenai.service.tools.ToolInfo;
import com.example.astrastudioopenai.service.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/tools")
public class ToolController {
    private static final Logger logger = LoggerFactory.getLogger(ToolController.class);

    private final Map<String, AtomicLong> callCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> successCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalDurationMs = new ConcurrentHashMap<>();

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAllTools() {
        List<Map<String, Object>> toolList = new ArrayList<>();

        for (ToolInfo info : ToolRegistry.getInstance().getToolInfos()) {
            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("name", info.getName());
            toolMap.put("description", info.getDescription());
            toolMap.put("className", info.getClassName());
            toolMap.put("status", getToolStatus(info.getName()));
            toolMap.put("registeredAt", info.getRegisteredAt());
            toolMap.put("callCount", getCallCount(info.getName()));

            toolList.add(toolMap);
        }

        return ResponseEntity.ok(toolList);
    }

    @GetMapping("/{toolName}")
    public ResponseEntity<Map<String, Object>> getToolDetail(@PathVariable String toolName) {
        ToolInfo info = ToolRegistry.getInstance().getToolInfo(toolName);

        if (info == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Tool not found");
            error.put("toolName", toolName);
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", info.getName());
        detail.put("description", info.getDescription());
        detail.put("className", info.getClassName());
        detail.put("registeredAt", info.getRegisteredAt());
        detail.put("status", getToolStatus(toolName));
        detail.put("disabled", ToolRegistry.getInstance().isToolDisabled(toolName));

        detail.put("statistics", Map.of(
                "totalCalls", getCallCount(toolName),
                "successCount", getSuccessCount(toolName),
                "failureCount", getFailureCount(toolName),
                "avgDurationMs", getAvgDurationMs(toolName)));

        return ResponseEntity.ok(detail);
    }

    @PostMapping("/{toolName}/test")
    public ResponseEntity<Map<String, Object>> testTool(
            @PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> params) {

        if (!ToolRegistry.getInstance().isToolRegistered(toolName)) {
            return ResponseEntity.notFound().build();
        }

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            Object toolInstance = ToolRegistry.getInstance().getTool(toolName);

            result.put("success", true);
            result.put("toolName", toolName);
            result.put("toolType", toolInstance.getClass().getName());
            result.put("timestamp", Instant.now());
            result.put("message", "Tool is registered and accessible");
            result.put("paramsReceived", params != null ? params : null);

            recordSuccess(toolName, System.currentTimeMillis() - startTime);

            logger.info("Tool test successful: {}", toolName);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", Instant.now());

            recordFailure(toolName, System.currentTimeMillis() - startTime);

            logger.error("Tool test failed: {} - {}", toolName, e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/{toolName}/execute")
    public ResponseEntity<Map<String, Object>> executeTool(
            @PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> params) {

        if (!ToolRegistry.getInstance().isToolRegistered(toolName)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", "Tool not found: " + toolName);
            return ResponseEntity.notFound().build();
        }

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            Object toolInstance = ToolRegistry.getInstance().getTool(toolName);
            Method toolMethod = findToolMethod(toolInstance.getClass());

            if (toolMethod == null) {
                result.put("success", false);
                result.put("error", "No @Tool annotated method found in " + toolInstance.getClass().getSimpleName());
                return ResponseEntity.badRequest().body(result);
            }

            Object[] args = prepareArguments(toolMethod, params != null ? params : new HashMap<>());
            Object returnValue = toolMethod.invoke(toolInstance, args);

            result.put("success", true);
            result.put("toolName", toolName);
            result.put("methodName", toolMethod.getName());
            result.put("result", returnValue);
            result.put("timestamp", Instant.now());
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);

            recordSuccess(toolName, System.currentTimeMillis() - startTime);

            logger.info("Tool executed successfully: {} in {}ms", toolName, System.currentTimeMillis() - startTime);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            Throwable rootCause = unwrapException(e);

            result.put("success", false);
            result.put("error", rootCause.getMessage());
            result.put("errorType", rootCause.getClass().getSimpleName());
            result.put("rootCauseClass", rootCause.getClass().getName());
            result.put("timestamp", Instant.now());

            recordFailure(toolName, System.currentTimeMillis() - startTime);

            logger.error("Tool execution failed: {} - {} | Root cause: {}: {}",
                    toolName, e.getMessage(),
                    rootCause.getClass().getSimpleName(), rootCause.getMessage(),
                    rootCause);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private Throwable unwrapException(Throwable e) {
        if (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null) {
            return unwrapException(e.getCause());
        }
        return e;
    }

    private Method findToolMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private Object[] prepareArguments(Method method, Map<String, Object> params) {
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            Object value = params.get(paramName);

            if (value == null) {
                args[i] = getDefaultValue(paramTypes[i]);
            } else {
                args[i] = convertValue(value, paramTypes[i]);
            }
        }

        return args;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null)
            return null;

        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (targetType == String.class) {
            return value.toString();
        }

        if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number)
                return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        }

        if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Number)
                return ((Number) value).longValue();
            return Long.parseLong(value.toString());
        }

        if (targetType == double.class || targetType == Double.class) {
            if (value instanceof Number)
                return ((Number) value).doubleValue();
            return Double.parseDouble(value.toString());
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }

        return value;
    }

    private Object getDefaultValue(Class<?> type) {
        if (type == int.class || type == Integer.class)
            return 0;
        if (type == long.class || type == Long.class)
            return 0L;
        if (type == double.class || type == Double.class)
            return 0.0;
        if (type == boolean.class || type == Boolean.class)
            return false;
        return null;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        int totalTools = ToolRegistry.getInstance().getToolCount();
        long totalCalls = callCounters.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
        long totalSuccess = successCounters.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
        long totalFailure = failureCounters.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();

        stats.put("totalTools", totalTools);
        stats.put("enabledTools", totalTools - countDisabledTools());
        stats.put("globalTotalCalls", totalCalls);
        stats.put("globalSuccessRate", totalCalls > 0 ? (double) totalSuccess / totalCalls : 0.0);
        stats.put("globalFailureRate", totalCalls > 0 ? (double) totalFailure / totalCalls : 0.0);
        stats.put("timestamp", Instant.now());

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/{toolName}/disable")
    public ResponseEntity<Void> disableTool(@PathVariable String toolName) {
        if (ToolRegistry.getInstance().isToolRegistered(toolName)) {
            ToolRegistry.getInstance().disableTool(toolName);
            logger.info("Tool disabled via API: {}", toolName);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{toolName}/enable")
    public ResponseEntity<Void> enableTool(@PathVariable String toolName) {
        if (ToolRegistry.getInstance().isToolRegistered(toolName)) {
            ToolRegistry.getInstance().enableTool(toolName);
            logger.info("Tool enabled via API: {}", toolName);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    private String getToolStatus(String toolName) {
        boolean isDisabled = ToolRegistry.getInstance().isToolDisabled(toolName);
        return isDisabled ? "DISABLED" : "ACTIVE";
    }

    private long getCallCount(String toolName) {
        return callCounters.computeIfAbsent(toolName, k -> new AtomicLong(0)).get();
    }

    private long getSuccessCount(String toolName) {
        return successCounters.computeIfAbsent(toolName, k -> new AtomicLong(0)).get();
    }

    private long getFailureCount(String toolName) {
        return failureCounters.computeIfAbsent(toolName, k -> new AtomicLong(0)).get();
    }

    private double getAvgDurationMs(String toolName) {
        AtomicLong duration = totalDurationMs.get(toolName);
        AtomicLong calls = callCounters.get(toolName);
        if (duration == null || calls == null || calls.get() == 0) {
            return 0.0;
        }
        return (double) duration.get() / calls.get();
    }

    private void recordSuccess(String toolName, long durationMs) {
        callCounters.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        successCounters.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        totalDurationMs.computeIfAbsent(toolName, k -> new AtomicLong(0)).addAndGet(durationMs);
    }

    private void recordFailure(String toolName, long durationMs) {
        callCounters.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        failureCounters.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        totalDurationMs.computeIfAbsent(toolName, k -> new AtomicLong(0)).addAndGet(durationMs);
    }

    private long countDisabledTools() {
        return (long) ToolRegistry.getInstance().getToolInfos().stream()
                .filter(info -> ToolRegistry.getInstance().isToolDisabled(info.getName()))
                .count();
    }

    @GetMapping("/pdfGeneratorTool/download")
    public ResponseEntity<Resource> downloadPdf(@RequestParam String fileName) {
        try {
            if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/")
                    || fileName.contains("\\")) {
                logger.warn("Invalid fileName rejected: {}", fileName);
                return ResponseEntity.badRequest().build();
            }

            java.nio.file.Path baseDir = java.nio.file.Paths.get("./generated-pdfs").toAbsolutePath().normalize();
            java.nio.file.Path filePath = baseDir.resolve(fileName).normalize();

            if (!filePath.startsWith(baseDir)) {
                logger.warn("Path traversal attempt blocked: {}", fileName);
                return ResponseEntity.badRequest().build();
            }

            if (!java.nio.file.Files.exists(filePath)) {
                logger.error("PDF file not found: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                logger.error("PDF file resource not found: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            String contentType = "application/pdf";
            String headerValue = "attachment; filename=\"" + filePath.getFileName() + "\"";

            logger.info("PDF downloaded: {}, size: {} bytes", filePath.getFileName(), resource.contentLength());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                    .body(resource);

        } catch (Exception e) {
            logger.error("Failed to download PDF: {} - {}", fileName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
