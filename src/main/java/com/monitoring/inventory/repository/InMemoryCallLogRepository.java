package com.monitoring.inventory.repository;

import com.monitoring.inventory.model.LogPage;
import com.monitoring.inventory.model.LogQuery;
import com.monitoring.inventory.model.ServiceCallLog;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded in-memory log store, newest first. Enough for the observability dashboard
 * and it keeps the service stateless-friendly on free hosting tiers.
 */
@Repository
public class InMemoryCallLogRepository implements CallLogRepository {

    /** shortcut: logs live in memory only and are lost on restart; swap for a DB if history must survive deploys. */
    public static final int MAX_LOGS = 5_000;

    private final ConcurrentLinkedDeque<ServiceCallLog> logs = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();

    @Override
    public void save(ServiceCallLog log) {
        logs.addFirst(log);
        if (size.incrementAndGet() > MAX_LOGS) {
            if (logs.pollLast() != null) {
                size.decrementAndGet();
            }
        }
    }

    @Override
    public LogPage find(LogQuery query) {
        List<ServiceCallLog> matches = new ArrayList<>();
        for (ServiceCallLog log : logs) {
            if (query.matches(log)) {
                matches.add(log);
            }
        }
        return LogPage.of(matches, query.page(), query.size());
    }

    @Override
    public List<ServiceCallLog> findAll() {
        return List.copyOf(logs);
    }

    @Override
    public void clear() {
        logs.clear();
        size.set(0);
    }
}
