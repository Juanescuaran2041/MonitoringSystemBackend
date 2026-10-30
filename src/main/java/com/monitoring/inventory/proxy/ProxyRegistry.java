package com.monitoring.inventory.proxy;

import com.monitoring.inventory.exception.ServiceException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Every microservice the API exposes, already wrapped in its logging proxy.
 * Callers only ever see {@link MicroserviceProxy}, never the real service.
 */
public class ProxyRegistry {

    private final Map<String, MicroserviceProxy<Object>> proxiesByServiceId;

    public ProxyRegistry(Map<String, MicroserviceProxy<Object>> proxiesByServiceId) {
        this.proxiesByServiceId = Map.copyOf(new LinkedHashMap<>(proxiesByServiceId));
    }

    public MicroserviceProxy<Object> get(String serviceId) {
        MicroserviceProxy<Object> proxy = proxiesByServiceId.get(serviceId);
        if (proxy == null) {
            throw ServiceException.notFound("Unknown service '" + serviceId + "'. Available: " + getServiceIds());
        }
        return proxy;
    }

    public Set<String> getServiceIds() {
        return proxiesByServiceId.keySet();
    }
}
