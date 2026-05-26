package com.example.astrastudioopenai.service.routing;

import com.example.astrastudioopenai.dto.response.routing.ClassificationResult;
import com.example.astrastudioopenai.dto.response.routing.RoutingDecision;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    @Value("${auto-routing.rules-file:classpath:intent-rules.yaml}")
    private String rulesFile;

    private final List<IntentConfig> intents = new ArrayList<>();
    private final Map<String, List<Pattern>> compiledPatterns = new HashMap<>();

    @PostConstruct
    public void init() {
        loadRules();
        log.info("🔍 IntentClassifier initialized with {} intent rules", intents.size());
        intents.forEach(intent -> log.info("   - {}: weight={}, model={}",
            intent.name(), intent.weight(), intent.targetModel()));
    }

    public ClassificationResult classify(String text) {
        if (text == null || text.isBlank()) {
            return new ClassificationResult("GENERAL_CHAT", 0.0, "空输入");
        }

        String bestIntent = "GENERAL_CHAT";
        double bestScore = 0.0;
        String bestReason = "无匹配关键词";

        for (IntentConfig intent : intents) {
            double keywordScore = calculateKeywordMatch(text, intent.keywords());
            double patternScore = calculatePatternMatch(text, intent.name());
            double finalScore = Math.max(keywordScore, patternScore) * intent.weight();

            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestIntent = intent.name();
                bestReason = generateReason(text, intent, keywordScore, patternScore);
            }
        }

        return new ClassificationResult(bestIntent, bestScore, bestReason);
    }

    private void loadRules() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(
            rulesFile.replace("classpath:", ""))) {

            if (is == null) {
                log.warn("⚠️ Rules file not found: {}, using empty rules", rulesFile);
                return;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            Map<String, Object> intentsMap = (Map<String, Object>) root.get("intents");

            if (intentsMap != null) {
                intentsMap.forEach((name, config) -> {
                    Map<String, Object> configMap = (Map<String, Object>) config;
                    IntentConfig intentConfig = new IntentConfig(
                        name,
                        extractKeywordGroups(configMap.get("keywords")),
                        extractPatterns(configMap.get("patterns")),
                        ((Number) configMap.getOrDefault("weight", 0.7)).doubleValue(),
                        (String) configMap.getOrDefault("target_model", "glm-5.1"),
                        (String) configMap.getOrDefault("description", "")
                    );
                    intents.add(intentConfig);

                    List<Pattern> patterns = intentConfig.patterns().stream()
                        .map(Pattern::compile)
                        .toList();
                    if (!patterns.isEmpty()) {
                        compiledPatterns.put(name, patterns);
                    }
                });
            }

        } catch (Exception e) {
            log.error("❌ Failed to load intent rules from {}", rulesFile, e);
        }
    }

    private double calculateKeywordMatch(String text, List<List<String>> keywordGroups) {
        if (keywordGroups == null || keywordGroups.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        int totalKeywords = 0;

        for (List<String> group : keywordGroups) {
            long matchedCount = group.stream()
                .filter(keyword -> text.toLowerCase().contains(keyword.toLowerCase()))
                .count();

            if (matchedCount > 0) {
                double groupScore = (double) matchedCount / group.size();
                totalScore += groupScore;
            }
            totalKeywords += group.size();
        }

        if (totalKeywords == 0) return 0.0;

        return Math.min(totalScore / keywordGroups.size(), 1.0);
    }

    private double calculatePatternMatch(String text, String intentName) {
        List<Pattern> patterns = compiledPatterns.get(intentName);
        if (patterns == null || patterns.isEmpty()) {
            return 0.0;
        }

        return patterns.stream().anyMatch(p -> p.matcher(text).matches()) ? 1.0 : 0.0;
    }

    private String generateReason(String text, IntentConfig intent,
                                   double keywordScore, double patternScore) {
        if (patternScore > keywordScore && patternScore > 0) {
            return "模式匹配: " + intent.name();
        } else if (keywordScore > 0) {
            List<String> matchedKeywords = findMatchedKeywords(text, intent.keywords());
            return "检测到关键词: " + String.join(", ", matchedKeywords);
        } else {
            return "默认意图";
        }
    }

    private List<String> findMatchedKeywords(String text, List<List<String>> keywordGroups) {
        List<String> matched = new ArrayList<>();
        for (List<String> group : keywordGroups) {
            group.stream()
                .filter(keyword -> text.toLowerCase().contains(keyword.toLowerCase()))
                .findFirst()
                .ifPresent(matched::add);
        }
        return matched.isEmpty() ? List.of("通用") : matched;
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> extractKeywordGroups(Object keywordsObj) {
        if (keywordsObj == null) return List.of();
        List<List<String>> groups = new ArrayList<>();
        if (keywordsObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof List<?> group) {
                    groups.add((List<String>) group);
                } else if (item instanceof String single) {
                    groups.add(List.of(single));
                }
            }
        }
        return groups;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractPatterns(Object patternsObj) {
        if (patternsObj == null) return List.of();
        if (patternsObj instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    public record IntentConfig(
        String name,
        List<List<String>> keywords,
        List<String> patterns,
        double weight,
        String targetModel,
        String description
    ) {}
}
