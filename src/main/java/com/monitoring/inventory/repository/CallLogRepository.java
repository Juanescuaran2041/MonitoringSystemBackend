package com.monitoring.inventory.repository;

import com.monitoring.inventory.model.LogPage;
import com.monitoring.inventory.model.LogQuery;
import com.monitoring.inventory.model.ServiceCallLog;

import java.util.List;

/** Storage for the audit records produced by the logging proxy. */
public interface CallLogRepository {

    void save(ServiceCallLog log);

    /** Newest first, filtered and paginated. */
    LogPage find(LogQuery query);

    /** All stored logs, newest first. */
    List<ServiceCallLog> findAll();

    void clear();
}
