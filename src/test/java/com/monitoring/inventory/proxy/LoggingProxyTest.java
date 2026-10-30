package com.monitoring.inventory.proxy;

import com.monitoring.inventory.model.CallStatus;
import com.monitoring.inventory.model.LogQuery;
import com.monitoring.inventory.model.ServiceCallLog;
import com.monitoring.inventory.repository.InMemoryCallLogRepository;
import com.monitoring.inventory.service.InventoryService;
import com.monitoring.inventory.service.PaymentService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingProxyTest {

    private final InMemoryCallLogRepository repository = new InMemoryCallLogRepository();

    @Test
    void auditsSuccessfulCalls() {
        MicroserviceProxy<Object> proxy = new LoggingProxy<>(new InventoryService(), repository);

        Object result = proxy.execute("checkStock", Map.of("productId", "SKU-1001"));

        assertEquals(Map.of("productId", "SKU-1001", "available", 40), result);
        ServiceCallLog log = onlyLog();
        assertEquals("inventory", log.serviceId());
        assertEquals("checkStock", log.operation());
        assertEquals(CallStatus.SUCCESS, log.status());
        assertNotNull(log.requestId());
        assertNotNull(log.timestamp());
        assertNotNull(log.result());
        assertTrue(log.durationMs() >= 0);
        assertNull(log.errorMessage());
    }

    @Test
    void auditsFailuresWithStackTraceAndRethrows() {
        // failure rate 1.0 -> every payment is declined
        MicroserviceProxy<Object> proxy = new LoggingProxy<>(new PaymentService(1.0), repository);

        assertThrows(RuntimeException.class,
                () -> proxy.execute("processPayment", Map.of("orderId", "ORD-1", "amount", 10)));

        ServiceCallLog log = onlyLog();
        assertEquals(CallStatus.ERROR, log.status());
        assertTrue(log.errorMessage().contains("declined"));
        assertTrue(log.errorStackTrace().contains("at "));
        assertNull(log.result());
    }

    @Test
    void auditsUnknownOperations() {
        MicroserviceProxy<Object> proxy = new LoggingProxy<>(new InventoryService(), repository);

        assertThrows(RuntimeException.class, () -> proxy.execute("doesNotExist", Map.of()));

        assertEquals(CallStatus.ERROR, onlyLog().status());
    }

    @Test
    void filtersLogsByServiceAndStatus() {
        new LoggingProxy<>(new InventoryService(), repository).execute("listProducts", Map.of());
        assertThrows(RuntimeException.class,
                () -> new LoggingProxy<>(new PaymentService(1.0), repository)
                        .execute("processPayment", Map.of("orderId", "ORD-1", "amount", 10)));

        List<ServiceCallLog> errors = repository
                .find(new LogQuery("payments", CallStatus.ERROR, null, null, 0, 10)).content();

        assertEquals(1, errors.size());
        assertEquals("payments", errors.get(0).serviceId());
    }

    private ServiceCallLog onlyLog() {
        List<ServiceCallLog> logs = repository.findAll();
        assertEquals(1, logs.size());
        return logs.get(0);
    }
}
