package com.example.astrastudioopenai.service.routing;

import com.example.astrastudioopenai.dto.response.routing.AutoRouteResult;
import com.example.astrastudioopenai.dto.response.routing.ClassificationResult;
import com.example.astrastudioopenai.dto.response.routing.RoutingDecision;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AutoRoutingService {

    private static final Logger log = LoggerFactory.getLogger(AutoRoutingService.class);

    @Resource
    private IntentClassifier intentClassifier;

    @Resource
    private ModelRouter modelRouter;

    @Value("${auto-routing.confidence-threshold:0.6}")
    private double confidenceThreshold;

    @Value("${auto-routing.default-model:glm-5.1}")
    private String defaultModel;

    public AutoRouteResult autoRoute(String userText, String explicitModel) {
        if (explicitModel != null && !"auto".equals(explicitModel)) {
            log.debug("[AutoRouting] mode=manual | model={}", explicitModel);
            return AutoRouteResult.manual(explicitModel);
        }

        try {
            ClassificationResult classification = intentClassifier.classify(userText);

            if (classification.confidence() >= confidenceThreshold) {
                RoutingDecision decision = modelRouter.route(classification);
                log.info("[AutoRouting] text=\"{}\" | intent={} | conf={} | model={} | reason={}",
                    truncate(userText, 50),
                    classification.intent(),
                    String.format("%.2f", classification.confidence()),
                    decision.selectedModel(),
                    decision.reason()
                );
                return AutoRouteResult.auto(decision);
            } else {
                log.warn("[AutoRouting][FALLBACK] text=\"{}\" | bestIntent={} | conf={}",
                    truncate(userText, 100),
                    classification.intent(),
                    String.format("%.2f", classification.confidence())
                );
                return AutoRouteResult.fallback(defaultModel, classification);
            }
        } catch (Exception e) {
            log.error("[AutoRouting] Error during auto routing, falling back to default", e);
            return AutoRouteResult.fallback(defaultModel,
                new ClassificationResult("ERROR", 0.0, "分类器异常: " + e.getMessage()));
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
