package com.monitoring.inventory.controller;

import com.monitoring.inventory.exception.ServiceException;
import com.monitoring.inventory.metrics.LoadSimulator;
import com.monitoring.inventory.metrics.MetricsService;
import com.monitoring.inventory.metrics.MetricsSummary;
import com.monitoring.inventory.model.CallStatus;
import com.monitoring.inventory.model.LogPage;
import com.monitoring.inventory.model.LogQuery;
import com.monitoring.inventory.repository.CallLogRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Observability API consumed by the React dashboard. */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;
    private final CallLogRepository callLogRepository;
    private final LoadSimulator loadSimulator;

    public MetricsController(MetricsService metricsService, CallLogRepository callLogRepository,
                             LoadSimulator loadSimulator) {
        this.metricsService = metricsService;
        this.callLogRepository = callLogRepository;
        this.loadSimulator = loadSimulator;
    }

    @GetMapping("/summary")
    public MetricsSummary summary() {
        return metricsService.summary();
    }

    @GetMapping("/logs")
    public LogPage logs(@RequestParam(required = false) String service,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
        return callLogRepository.find(new LogQuery(
                service, parseStatus(status), parseInstant(from, "from"), parseInstant(to, "to"), page, size));
    }

    @PostMapping("/simulate-load")
    public LoadSimulator.SimulationResult simulateLoad(@RequestBody(required = false) Map<String, Object> body) {
        int calls = LoadSimulator.DEFAULT_CALLS;
        if (body != null && body.get("calls") instanceof Number number) {
            calls = number.intValue();
        }
        return loadSimulator.run(calls);
    }

    @DeleteMapping("/logs")
    public Map<String, String> clearLogs() {
        callLogRepository.clear();
        return Map.of("status", "cleared");
    }

    private static CallStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CallStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw ServiceException.badRequest("Invalid status '" + status + "'. Use SUCCESS or ERROR");
        }
    }

    /** Accepts an ISO-8601 instant (2026-10-30T15:00:00Z). */
    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw ServiceException.badRequest(
                    "Invalid '" + field + "' date: " + value + ". Expected ISO-8601, e.g. 2026-10-30T15:00:00Z");
        }
    }
}
