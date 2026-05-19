package com.example.astrastudioopenai.service.routing;

import com.example.astrastudioopenai.dto.response.routing.AutoRouteResult;
import com.example.astrastudioopenai.dto.response.routing.ClassificationResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RoutingStatsService {

    private static final Logger log = LoggerFactory.getLogger(RoutingStatsService.class);

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong autoRoutedCount = new AtomicLong(0);
    private final AtomicLong manualOverrideCount = new AtomicLong(0);
    private final AtomicLong fallbackCount = new AtomicLong(0);

    private final ConcurrentHashMap<String, AtomicLong> intentDistribution = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> modelUsage = new ConcurrentHashMap<>();

    private final AtomicReference<Double> totalConfidenceSum = new AtomicReference<>(0.0);
    private final Object confidenceLock = new Object();

    @PostConstruct
    public void init() {
        log.info("📊 RoutingStatsService initialized");
    }

    public void recordRouting(AutoRouteResult result, ClassificationResult classification) {
        totalRequests.incrementAndGet();

        if (result.isAutoRouted()) {
            autoRoutedCount.incrementAndGet();
        } else if (result.reason().contains("用户手动选择")) {
            manualOverrideCount.incrementAndGet();
        } else {
            fallbackCount.incrementAndGet();
        }

        if (classification != null) {
            intentDistribution
                    .computeIfAbsent(classification.intent(), k -> new AtomicLong(0))
                    .incrementAndGet();

            modelUsage
                    .computeIfAbsent(result.modelName(), k -> new AtomicLong(0))
                    .incrementAndGet();

            synchronized (confidenceLock) {
                totalConfidenceSum.set(totalConfidenceSum.get() + result.confidence());
            }
        }
    }

    public RoutingStatsRecord getStats() {
        long total = totalRequests.get();
        long confidenceCount = autoRoutedCount.get() + fallbackCount.get();
        double avgConfidence = confidenceCount > 0 ? totalConfidenceSum.get() / confidenceCount : 0.0;

        return new RoutingStatsRecord(
                total,
                autoRoutedCount.get(),
                manualOverrideCount.get(),
                fallbackCount.get(),
                Map.copyOf(intentDistribution),
                Map.copyOf(modelUsage),
                avgConfidence,
                Instant.now());
    }

    public record RoutingStatsRecord(
            long totalRequests,
            long autoRoutedCount,
            long manualOverrideCount,
            long fallbackCount,
            Map<String, AtomicLong> intentDistribution,
            Map<String, AtomicLong> modelUsage,
            double averageConfidence,
            Instant timestamp) {
        public String toFormattedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n📊 ===== ROUTING STATS REPORT =====\n");
            sb.append(String.format("Timestamp: %s\n", timestamp));
            sb.append(String.format("Total Requests: %d\n", totalRequests));
            sb.append(String.format("Auto-Routed: %d (%.1f%%)\n",
                    autoRoutedCount, totalRequests > 0 ? autoRoutedCount * 100.0 / totalRequests : 0));
            sb.append(String.format("Manual Override: %d (%.1f%%)\n",
                    manualOverrideCount, totalRequests > 0 ? manualOverrideCount * 100.0 / totalRequests : 0));
            sb.append(String.format("Fallback: %d (%.1f%%)\n",
                    fallbackCount, totalRequests > 0 ? fallbackCount * 100.0 / totalRequests : 0));
            sb.append(String.format("Average Confidence: %.2f\n\n", averageConfidence));

            sb.append("Intent Distribution:\n");
            intentDistribution
                    .forEach((intent, count) -> sb.append(String.format("  %-20s: %d\n", intent, count.get())));

            sb.append("\nModel Usage:\n");
            modelUsage.forEach((model, count) -> sb.append(String.format("  %-30s: %d\n", model, count.get())));

            return sb.toString();
        }
    }
}
