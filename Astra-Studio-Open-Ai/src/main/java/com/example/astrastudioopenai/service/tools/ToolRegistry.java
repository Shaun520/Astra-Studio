package com.example.astrastudioopenai.service.tools;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    private static final ToolRegistry INSTANCE = new ToolRegistry();

    private final Map<String, Object> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolInfo> toolInfos = new ConcurrentHashMap<>();
    private final Set<String> disabledTools = ConcurrentHashMap.newKeySet();

    private ToolRegistry() {
        logger.info("ToolRegistry initialized (singleton)");
    }

    public static ToolRegistry getInstance() {
        return INSTANCE;
    }

    public void registerTool(String name, Object toolInstance) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }
        if (toolInstance == null) {
            throw new IllegalArgumentException("Tool instance cannot be null");
        }

        Object existing = tools.putIfAbsent(name, toolInstance);
        if (existing != null) {
            throw new IllegalStateException("Tool already registered: " + name);
        }

        ToolInfo info = extractToolInfo(name, toolInstance);
        toolInfos.put(name, info);
        logger.info("Tool registered: {} ({})", name, toolInstance.getClass().getName());
    }

    public void unregisterTool(String name) {
        Object removed = tools.remove(name);
        if (removed != null) {
            toolInfos.remove(name);
            disabledTools.remove(name);
            logger.info("Tool unregistered: {}", name);
        }
    }

    public void registerTools(List<Object> toolList) {
        List<String> registeredNames = new ArrayList<>();
        try {
            for (Object tool : toolList) {
                String name = extractToolName(tool);
                registerTool(name, tool);
                registeredNames.add(name);
            }
        } catch (Exception e) {
            registeredNames.forEach(this::unregisterTool);
            throw new RuntimeException("Failed to register tools batch, rolled back", e);
        }
    }

    public Object getTool(String name) {
        return tools.get(name);
    }

    public List<Object> getAllTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    public List<Object> getEnabledTools() {
        return tools.entrySet().stream()
                .filter(entry -> !disabledTools.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    public List<Object> getToolsByNames(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        return toolNames.stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty() && tools.containsKey(name) && !disabledTools.contains(name))
                .map(tools::get)
                .toList();
    }

    public List<ToolInfo> getToolInfos() {
        return new ArrayList<>(toolInfos.values());
    }

    public ToolInfo getToolInfo(String name) {
        return toolInfos.get(name);
    }

    public boolean isToolRegistered(String name) {
        return tools.containsKey(name);
    }

    public int getToolCount() {
        return tools.size();
    }

    public void disableTool(String name) {
        if (tools.containsKey(name)) {
            disabledTools.add(name);
            logger.info("Tool disabled: {}", name);
        }
    }

    public void enableTool(String name) {
        disabledTools.remove(name);
        logger.info("Tool enabled: {}", name);
    }

    public boolean isToolDisabled(String name) {
        return disabledTools.contains(name);
    }

    public Map<String, Boolean> healthCheck() {
        Map<String, Boolean> healthStatus = new LinkedHashMap<>();
        tools.forEach((name, instance) -> {
            boolean healthy = instance != null;
            healthStatus.put(name, healthy);
            if (!healthy) {
                logger.warn("Tool health check failed: {} (null instance)", name);
            }
        });
        return healthStatus;
    }

    public void clearAll() {
        tools.clear();
        toolInfos.clear();
        disabledTools.clear();
        logger.warn("All tools cleared from registry");
    }

    private String extractToolName(Object toolInstance) {
        Class<?> clazz = toolInstance.getClass();
        Tool classAnnotation = clazz.getAnnotation(Tool.class);
        if (classAnnotation != null && !classAnnotation.name().isEmpty()) {
            return classAnnotation.name();
        }

        for (Method method : clazz.getDeclaredMethods()) {
            Tool methodAnnotation = method.getAnnotation(Tool.class);
            if (methodAnnotation != null && !methodAnnotation.name().isEmpty()) {
                return methodAnnotation.name();
            }
        }

        String simpleName = clazz.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private ToolInfo extractToolInfo(String name, Object toolInstance) {
        Class<?> clazz = toolInstance.getClass();
        String description = "";

        Tool classAnnotation = clazz.getAnnotation(Tool.class);
        if (classAnnotation != null) {
            String[] values = classAnnotation.value();
            description = values.length > 0 ? values[0] : "";
        } else {
            for (Method method : clazz.getDeclaredMethods()) {
                Tool methodAnnotation = method.getAnnotation(Tool.class);
                if (methodAnnotation != null) {
                    String[] values = methodAnnotation.value();
                    description = values.length > 0 ? values[0] : "";
                    break;
                }
            }
        }

        return new ToolInfo(name, description, clazz.getName(), Instant.now());
    }
}
