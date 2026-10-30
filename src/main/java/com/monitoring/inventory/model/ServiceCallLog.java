package com.monitoring.inventory.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record produced by the logging proxy for every intercepted call.
 *
 * @param requestId        unique id of the call (UUID)
 * @param serviceId        service that was invoked
 * @param operation        operation that was invoked
 * @param status           SUCCESS or ERROR
 * @param durationMs       wall-clock duration of the call
 * @param timestamp        moment the call started
 * @param params           input parameters, as received by the proxy
 * @param result           full response, null when the call failed
 * @param errorMessage     exception message, null when the call succeeded
 * @param errorStackTrace  shortened stack trace, null when the call succeeded
 */
public record ServiceCallLog(
        String requestId,
        String serviceId,
        String operation,
        CallStatus status,
        long durationMs,
        Instant timestamp,
        Object params,
        Object result,
        String errorMessage,
        String errorStackTrace) {

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public static ServiceCallLog success(String requestId, String serviceId, String operation,
                                         long durationMs, Instant timestamp, Object params, Object result) {
        return new ServiceCallLog(requestId, serviceId, operation, CallStatus.SUCCESS,
                durationMs, timestamp, params, result, null, null);
    }

    public static ServiceCallLog error(String requestId, String serviceId, String operation,
                                       long durationMs, Instant timestamp, Object params,
                                       String errorMessage, String errorStackTrace) {
        return new ServiceCallLog(requestId, serviceId, operation, CallStatus.ERROR,
                durationMs, timestamp, params, null, errorMessage, errorStackTrace);
    }
}
