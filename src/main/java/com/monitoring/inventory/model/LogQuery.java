package com.monitoring.inventory.model;

import java.time.Instant;

/**
 * Filters accepted by GET /api/metrics/logs. Null values mean "do not filter".
 */
public record LogQuery(String serviceId, CallStatus status, Instant from, Instant to, int page, int size) {

    public static final int MAX_PAGE_SIZE = 200;

    public LogQuery {
        page = Math.max(page, 0);
        size = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
    }

    public boolean matches(ServiceCallLog log) {
        if (serviceId != null && !serviceId.isBlank() && !serviceId.equals(log.serviceId())) {
            return false;
        }
        if (status != null && status != log.status()) {
            return false;
        }
        if (from != null && log.timestamp().isBefore(from)) {
            return false;
        }
        return to == null || !log.timestamp().isAfter(to);
    }
}
