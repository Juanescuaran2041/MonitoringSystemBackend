package com.monitoring.inventory.metrics;

import java.time.Instant;

/** Aggregated usage metrics of a single microservice. */
public record ServiceMetrics(
        String serviceId,
        long totalCalls,
        long successCalls,
        long errorCalls,
        double successRate,
        double errorRate,
        double averageResponseTimeMs,
        long maxResponseTimeMs,
        Instant lastCallAt,
        boolean degraded) {

    /** A service is flagged as degraded when more than 15% of its calls fail. */
    public static final double DEGRADED_ERROR_RATE = 0.15;

    public static ServiceMetrics empty(String serviceId) {
        return new ServiceMetrics(serviceId, 0, 0, 0, 0, 0, 0, 0, null, false);
    }

    public static ServiceMetrics of(String serviceId, long successCalls, long errorCalls,
                                    long totalDurationMs, long maxResponseTimeMs, Instant lastCallAt) {
        long totalCalls = successCalls + errorCalls;
        double errorRate = totalCalls == 0 ? 0 : round((double) errorCalls / totalCalls);
        double successRate = totalCalls == 0 ? 0 : round((double) successCalls / totalCalls);
        double average = totalCalls == 0 ? 0 : round((double) totalDurationMs / totalCalls);
        return new ServiceMetrics(serviceId, totalCalls, successCalls, errorCalls, successRate, errorRate,
                average, maxResponseTimeMs, lastCallAt, errorRate > DEGRADED_ERROR_RATE);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
