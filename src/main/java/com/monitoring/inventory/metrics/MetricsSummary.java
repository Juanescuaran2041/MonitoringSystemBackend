package com.monitoring.inventory.metrics;

import java.time.Instant;
import java.util.List;

/** Response of GET /api/metrics/summary. */
public record MetricsSummary(
        Instant generatedAt,
        long totalCalls,
        long totalErrors,
        double overallErrorRate,
        double averageResponseTimeMs,
        List<ServiceMetrics> services) {
}
