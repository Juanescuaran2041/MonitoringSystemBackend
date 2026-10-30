package com.monitoring.inventory.metrics;

import com.monitoring.inventory.model.CallStatus;
import com.monitoring.inventory.model.ServiceCallLog;
import com.monitoring.inventory.proxy.ProxyRegistry;
import com.monitoring.inventory.repository.CallLogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the dashboard metrics out of the audit logs collected by the proxies. */
@Service
public class MetricsService {

    private final CallLogRepository callLogRepository;
    private final ProxyRegistry proxyRegistry;

    public MetricsService(CallLogRepository callLogRepository, ProxyRegistry proxyRegistry) {
        this.callLogRepository = callLogRepository;
        this.proxyRegistry = proxyRegistry;
    }

    public MetricsSummary summary() {
        Map<String, Accumulator> accumulators = new LinkedHashMap<>();
        proxyRegistry.getServiceIds().forEach(serviceId -> accumulators.put(serviceId, new Accumulator()));

        long totalCalls = 0;
        long totalErrors = 0;
        long totalDurationMs = 0;
        for (ServiceCallLog log : callLogRepository.findAll()) {
            accumulators.computeIfAbsent(log.serviceId(), serviceId -> new Accumulator()).add(log);
            totalCalls++;
            totalDurationMs += log.durationMs();
            if (log.status() == CallStatus.ERROR) {
                totalErrors++;
            }
        }

        List<ServiceMetrics> services = new ArrayList<>();
        accumulators.forEach((serviceId, accumulator) -> services.add(accumulator.toMetrics(serviceId)));

        return new MetricsSummary(
                Instant.now(),
                totalCalls,
                totalErrors,
                totalCalls == 0 ? 0 : round((double) totalErrors / totalCalls),
                totalCalls == 0 ? 0 : round((double) totalDurationMs / totalCalls),
                services);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Running totals of one service while the log list is scanned. */
    private static final class Accumulator {
        private long successCalls;
        private long errorCalls;
        private long totalDurationMs;
        private long maxDurationMs;
        private Instant lastCallAt;

        void add(ServiceCallLog log) {
            if (log.status() == CallStatus.ERROR) {
                errorCalls++;
            } else {
                successCalls++;
            }
            totalDurationMs += log.durationMs();
            maxDurationMs = Math.max(maxDurationMs, log.durationMs());
            if (lastCallAt == null || log.timestamp().isAfter(lastCallAt)) {
                lastCallAt = log.timestamp();
            }
        }

        ServiceMetrics toMetrics(String serviceId) {
            if (successCalls + errorCalls == 0) {
                return ServiceMetrics.empty(serviceId);
            }
            return ServiceMetrics.of(serviceId, successCalls, errorCalls, totalDurationMs, maxDurationMs, lastCallAt);
        }
    }
}
