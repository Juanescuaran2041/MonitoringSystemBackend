package com.monitoring.inventory.proxy;

/**
 * Contract shared by the real microservices and by every proxy that wraps them.
 * Because proxy and subject implement the same type, a caller cannot tell them apart.
 *
 * @param <T> type returned by the executed operation
 */
public interface MicroserviceProxy<T> {

    T execute(String operation, Object... params);

    String getServiceId();
}
