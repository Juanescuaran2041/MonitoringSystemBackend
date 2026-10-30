package com.monitoring.inventory.service;

import com.monitoring.inventory.exception.ServiceException;
import com.monitoring.inventory.proxy.MicroserviceProxy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Template for the real microservices: it resolves the operation name, validates the
 * incoming parameters and delegates to the handler each subclass registers.
 */
public abstract class AbstractMicroservice implements MicroserviceProxy<Object> {

    private final String serviceId;
    private volatile Map<String, Function<Map<String, Object>, Object>> operations;

    protected AbstractMicroservice(String serviceId) {
        this.serviceId = serviceId;
    }

    /** Operation name -> handler. Built lazily so subclass fields are already initialised. */
    protected abstract Map<String, Function<Map<String, Object>, Object>> buildOperations();

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public Object execute(String operation, Object... params) {
        Function<Map<String, Object>, Object> handler = operations().get(operation);
        if (handler == null) {
            throw ServiceException.badRequest(
                    "Unknown operation '" + operation + "' on service '" + serviceId
                            + "'. Available: " + getOperations());
        }
        return handler.apply(asParameterMap(params));
    }

    public Set<String> getOperations() {
        return operations().keySet();
    }

    private Map<String, Function<Map<String, Object>, Object>> operations() {
        Map<String, Function<Map<String, Object>, Object>> current = operations;
        if (current == null) {
            synchronized (this) {
                current = operations;
                if (current == null) {
                    current = Collections.unmodifiableMap(new LinkedHashMap<>(buildOperations()));
                    operations = current;
                }
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asParameterMap(Object... params) {
        if (params == null || params.length == 0 || params[0] == null) {
            return Map.of();
        }
        if (params[0] instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw ServiceException.badRequest("Operation parameters must be a JSON object");
    }

    protected static String requireText(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null || value.toString().isBlank()) {
            throw ServiceException.badRequest("Missing required parameter '" + key + "'");
        }
        return value.toString();
    }

    protected static int requirePositiveInt(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw ServiceException.badRequest("Missing required parameter '" + key + "'");
        }
        int number;
        try {
            number = (value instanceof Number n) ? n.intValue() : Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw ServiceException.badRequest("Parameter '" + key + "' must be an integer");
        }
        if (number <= 0) {
            throw ServiceException.badRequest("Parameter '" + key + "' must be greater than zero");
        }
        return number;
    }

    protected static double requirePositiveAmount(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw ServiceException.badRequest("Missing required parameter '" + key + "'");
        }
        double amount;
        try {
            amount = (value instanceof Number n) ? n.doubleValue() : Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw ServiceException.badRequest("Parameter '" + key + "' must be a number");
        }
        if (amount <= 0) {
            throw ServiceException.badRequest("Parameter '" + key + "' must be greater than zero");
        }
        return amount;
    }

    /** Simulated processing time so the dashboard shows realistic response times. */
    protected static void simulateLatency(int minMs, int maxMs) {
        try {
            Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
