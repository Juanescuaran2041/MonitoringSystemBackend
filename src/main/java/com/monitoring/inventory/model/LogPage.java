package com.monitoring.inventory.model;

import java.util.List;

/** One page of audit logs. */
public record LogPage(List<ServiceCallLog> content, int page, int size, long totalElements, int totalPages) {

    public static LogPage of(List<ServiceCallLog> matches, int page, int size) {
        int totalPages = (int) Math.ceil((double) matches.size() / size);
        int fromIndex = Math.min(page * size, matches.size());
        int toIndex = Math.min(fromIndex + size, matches.size());
        return new LogPage(List.copyOf(matches.subList(fromIndex, toIndex)), page, size, matches.size(), totalPages);
    }
}
