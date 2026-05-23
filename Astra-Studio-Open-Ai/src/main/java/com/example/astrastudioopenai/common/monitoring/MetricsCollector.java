package com.example.astrastudioopenai.common.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

@Slf4j
@Component
public class MetricsCollector {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Histogram> histograms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public void incrementCounter(String metricName) {
        incrementCounter(metricName, 1);
    }

    public void incrementCounter(String metricName, long value) {
        counters.computeIfAbsent(metricName, k -> new AtomicLong(0)).addAndGet(value);
    }

    public long getCounterValue(String metricName) {
        AtomicLong counter = counters.get(metricName);
        return counter != null ? counter.get() : 0;
    }

    public void recordHistogram(String metricName, long durationMs) {
        Histogram histogram = histograms.computeIfAbsent(metricName, k -> new Histogram());
        histogram.record(durationMs);
    }

    public HistogramStats getHistogramStats(String metricName) {
        Histogram histogram = histograms.get(metricName);
        if (histogram == null) {
            return new HistogramStats(metricName, 0, 0, 0, 0,0.0, 0.0);
        }
        return histogram.getStats();
    }

    public void setGauge(String metricName, long value) {
        gauges.computeIfAbsent(metricName, k -> new AtomicLong(0)).set(value);
    }

    public long getGaugeValue(String metricName) {
        AtomicLong gauge = gauges.get(metricName);
        return gauge != null ? gauge.get() : 0;
    }

    public void logAllMetrics() {
        log.info("📈 === Metrics Summary ===");

        log.info("📊 Counters:");
        counters.forEach((name, counter) ->
                log.info("   {} = {}", name, counter.get()));

        log.info("📊 Histograms:");
        histograms.forEach((name, hist) -> {
            HistogramStats stats = hist.getStats();
            log.info("   {}: count={}, avg={:.2f}ms, p50={:.2f}ms, p99={:.2f}ms",
                    name, stats.count, stats.avg, stats.p50, stats.p99);
        });

        log.info("📊 Gauges:");
        gauges.forEach((name, gauge) ->
                log.info("   {} = {}", name, gauge.get()));

        log.info("📈 =======================");
    }

    public void resetAll() {
        counters.clear();
        histograms.clear();
        gauges.clear();
        log.info("🧹 All metrics reset");
    }

    public static class Histogram {
        private final AtomicLong count = new AtomicLong(0);
        private final DoubleAdder sum = new DoubleAdder();
        private final ConcurrentHashMap<Integer, AtomicLong> buckets = new ConcurrentHashMap<>();

        private static final int[] BUCKET_BOUNDARIES = {1, 5, 10, 25, 50, 100, 250, 500, 1000};

        public void record(long durationMs) {
            count.incrementAndGet();
            sum.add(durationMs);

            for (int boundary : BUCKET_BOUNDARIES) {
                if (durationMs <= boundary) {
                    buckets.computeIfAbsent(boundary, k -> new AtomicLong(0)).incrementAndGet();
                    break;
                }
            }
        }

        public HistogramStats getStats() {
            long totalCount = count.get();
            double avg = totalCount > 0 ? sum.sum() / totalCount : 0;

            double p50 = calculatePercentile(totalCount, 0.50);
            double p99 = calculatePercentile(totalCount, 0.99);

            long min = findMinBucket();
            long max = findMaxBucket();

            return new HistogramStats(
                    "histogram",
                    totalCount,
                    min,
                    max,
                    avg,
                    p50,
                    p99
            );
        }

        private double calculatePercentile(long totalCount, double percentile) {
            if (totalCount == 0) return 0;

            long targetCount = (long) (totalCount * percentile);
            long cumulativeCount = 0;

            for (int boundary : BUCKET_BOUNDARIES) {
                AtomicLong bucketCount = buckets.get(boundary);
                long countInBucket = bucketCount != null ? bucketCount.get() : 0;
                cumulativeCount += countInBucket;

                if (cumulativeCount >= targetCount) {
                    return boundary;
                }
            }

            return BUCKET_BOUNDARIES[BUCKET_BOUNDARIES.length - 1];
        }

        private long findMinBucket() {
            for (int boundary : BUCKET_BOUNDARIES) {
                AtomicLong bucketCount = buckets.get(boundary);
                if (bucketCount != null && bucketCount.get() > 0) {
                    return boundary;
                }
            }
            return 0;
        }

        private long findMaxBucket() {
            long maxBucket = 0;
            for (int boundary : BUCKET_BOUNDARIES) {
                AtomicLong bucketCount = buckets.get(boundary);
                if (bucketCount != null && bucketCount.get() > 0) {
                    maxBucket = boundary;
                }
            }
            return maxBucket;
        }
    }

    public record HistogramStats(
            String name,
            long count,
            long min,
            long max,
            double avg,
            double p50,
            double p99
    ) {}
}
