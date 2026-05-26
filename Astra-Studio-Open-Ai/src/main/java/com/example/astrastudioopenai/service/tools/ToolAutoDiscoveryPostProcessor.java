package com.example.astrastudioopenai.service.tools;

import dev.langchain4j.agent.tool.Tool;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tools.enabled", havingValue = "true", matchIfMissing = true)
public class ToolAutoDiscoveryPostProcessor implements BeanPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ToolAutoDiscoveryPostProcessor.class);

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (isToolBean(bean)) {
            try {
                ToolRegistry registry = ToolRegistry.getInstance();
                registry.registerTool(beanName, bean);
                logger.info("Auto-discovered and registered tool: {} ({})", beanName, bean.getClass().getName());
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("already registered")) {
                    logger.warn("Tool already registered, skipping: {}", beanName);
                } else {
                    throw e;
                }
            }
        }
        return bean;
    }

    private boolean isToolBean(Object bean) {
        if (bean == null) {
            return false;
        }

        Class<?> clazz = bean.getClass();

        Tool classAnnotation = AnnotationUtils.findAnnotation(clazz, Tool.class);
        if (classAnnotation != null) {
            return true;
        }

        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            Tool methodAnnotation = AnnotationUtils.findAnnotation(method, Tool.class);
            if (methodAnnotation != null) {
                return true;
            }
        }

        return false;
    }
}
