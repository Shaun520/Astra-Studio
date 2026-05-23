package com.example.astrastudioopenai.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class PerformanceMonitor {

    @Value("${performance.monitoring.enabled:true}")
    private boolean enabled;

    @Value("${performance.monitoring.slow-query-threshold-ms:100}")
    private long slowQueryThresholdMs;

    @Value("${performance.monitoring.log-slow-queries:true}")
    private boolean logSlowQueries;

    private final ConcurrentHashMap<String, AtomicLong> queryCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> queryTotalDurationMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> slowQueryCounters = new ConcurrentHashMap<>();

    public <T> T measureTime(String operationName, java.util.concurrent.Callable<T> callable) throws Exception {
        if (!enabled) {
            return callable.call();
        }

        long startTime = System.currentTimeMillis();
        try {
            T result = callable.call();
            long duration = System.currentTimeMillis() - startTime;

            recordMetrics(operationName, duration);

            if (duration > slowQueryThresholdMs && logSlowQueries) {
                log.warn("⏰ SLOW QUERY DETECTED: {} took {} ms (threshold: {} ms)",
                        operationName, duration, slowQueryThresholdMs);
            }

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ OPERATION FAILED: {} after {} ms - Error: {}", operationName, duration, e.getMessage());
            throw e;
        }
    }

    public void measureTime(String operationName, Runnable runnable) {
        try {
            measureTime(operationName, () -> {
                runnable.run();
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void recordMetrics(String operationName, long durationMs) {
        queryCounters.computeIfAbsent(operationName, k -> new AtomicLong(0)).incrementAndGet();
        queryTotalDurationMs.computeIfAbsent(operationName, k -> new AtomicLong(0)).addAndGet(durationMs);

        if (durationMs > slowQueryThresholdMs) {
            slowQueryCounters.computeIfAbsent(operationName, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    public PerformanceStats getStats(String operationName) {
        long count = queryCounters.getOrDefault(operationName, new AtomicLong(0)).get();
        long totalDuration = queryTotalDurationMs.getOrDefault(operationName, new AtomicLong(0)).get();
        long slowCount = slowQueryCounters.getOrDefault(operationName, new AtomicLong(0)).get();

        return new PerformanceStats(
                operationName,
                count,
                count > 0 ? totalDuration / count : 0,
                totalDuration,
                slowCount
        );
    }

    public void resetStats() {
        queryCounters.clear();
        queryTotalDurationMs.clear();
        slowQueryCounters.clear();
    }

    public void logAllStats() {
        if (!enabled) return;

        log.info("📊 === Performance Statistics Summary ===");
        queryCounters.keySet().forEach(key -> {
            PerformanceStats stats = getStats(key);
            log.info("📈 {}: calls={}, avg={}ms, total={}ms, slow_queries={}",
                    stats.operationName,
                    stats.totalCalls,
                    stats.avgDurationMs,
                    stats.totalDurationMs,
                    stats.slowQueryCount);
        });
        log.info("📊 =====================================");
    }

    public record PerformanceStats(
            String operationName,
            long totalCalls,
            long avgDurationMs,
            long totalDurationMs,
            long slowQueryCount
    ) {}
}
