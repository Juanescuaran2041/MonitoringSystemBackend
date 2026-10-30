package com.monitoring.inventory.proxy;

import com.monitoring.inventory.model.ServiceCallLog;
import com.monitoring.inventory.repository.CallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/**
 * Explicit Proxy (GoF): implements the same contract as the real service, wraps it, and
 * audits every call. No AOP, no annotations, no dynamic proxies: the wrapping is done by
 * hand in {@code MicroserviceConfiguration}, so the pattern is visible in the code.
 *
 * <p>Before the call it registers the start, after it registers duration and result, and
 * when the service fails it registers the error with a shortened stack trace and rethrows.</p>
 *
 * @param <T> type returned by the wrapped service
 */
public class LoggingProxy<T> implements MicroserviceProxy<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingProxy.class);
    private static final int STACK_TRACE_FRAMES = 3;

    private final MicroserviceProxy<T> target;
    private final CallLogRepository callLogRepository;

    public LoggingProxy(MicroserviceProxy<T> target, CallLogRepository callLogRepository) {
        this.target = Objects.requireNonNull(target, "target");
        this.callLogRepository = Objects.requireNonNull(callLogRepository, "callLogRepository");
    }

    @Override
    public String getServiceId() {
        return target.getServiceId();
    }

    /** The real service behind this proxy. */
    public MicroserviceProxy<T> getTarget() {
        return target;
    }

    @Override
    public T execute(String operation, Object... params) {
        String requestId = ServiceCallLog.newRequestId();
        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();

        LOGGER.info("[{}] START {}.{}", requestId, getServiceId(), operation);
        try {
            T result = target.execute(operation, params);
            long durationMs = elapsedMs(startNanos);
            callLogRepository.save(ServiceCallLog.success(
                    requestId, getServiceId(), operation, durationMs, startedAt, firstParam(params), result));
            LOGGER.info("[{}] SUCCESS {}.{} in {} ms", requestId, getServiceId(), operation, durationMs);
            return result;
        } catch (RuntimeException exception) {
            long durationMs = elapsedMs(startNanos);
            callLogRepository.save(ServiceCallLog.error(
                    requestId, getServiceId(), operation, durationMs, startedAt, firstParam(params),
                    describe(exception), summarizeStackTrace(exception)));
            LOGGER.error("[{}] ERROR {}.{} in {} ms - {}",
                    requestId, getServiceId(), operation, durationMs, describe(exception));
            throw exception;
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static Object firstParam(Object... params) {
        return params == null || params.length == 0 ? null : (params.length == 1 ? params[0] : params);
    }

    private static String describe(Throwable exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    /** First frames of the stack trace, enough to locate the failure without flooding the log. */
    private static String summarizeStackTrace(Throwable exception) {
        StringBuilder summary = new StringBuilder(describe(exception));
        StackTraceElement[] frames = exception.getStackTrace();
        for (int i = 0; i < Math.min(STACK_TRACE_FRAMES, frames.length); i++) {
            summary.append("\n\tat ").append(frames[i]);
        }
        if (frames.length > STACK_TRACE_FRAMES) {
            summary.append("\n\t... ").append(frames.length - STACK_TRACE_FRAMES).append(" more");
        }
        return summary.toString();
    }
}
