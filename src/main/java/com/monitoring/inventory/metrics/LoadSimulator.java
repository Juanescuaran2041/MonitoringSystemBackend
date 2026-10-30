package com.monitoring.inventory.metrics;

import com.monitoring.inventory.proxy.MicroserviceProxy;
import com.monitoring.inventory.proxy.ProxyRegistry;
import com.monitoring.inventory.service.InventoryService;
import com.monitoring.inventory.service.OrderService;
import com.monitoring.inventory.service.PaymentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates random traffic through the proxies so the dashboard has data to show.
 * Every generated call is audited exactly like a call coming from the API.
 */
@Service
public class LoadSimulator {

    public static final int DEFAULT_CALLS = 50;
    public static final int MAX_CALLS = 500;

    private final ProxyRegistry proxyRegistry;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public LoadSimulator(ProxyRegistry proxyRegistry, InventoryService inventoryService,
                         OrderService orderService, PaymentService paymentService) {
        this.proxyRegistry = proxyRegistry;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    public SimulationResult run(int requestedCalls) {
        int calls = Math.min(Math.max(requestedCalls, 1), MAX_CALLS);
        long startNanos = System.nanoTime();
        int success = 0;
        int errors = 0;

        for (int i = 0; i < calls; i++) {
            try {
                randomCall();
                success++;
            } catch (RuntimeException exception) {
                errors++; // already audited by the proxy
            }
        }
        return new SimulationResult(calls, success, errors, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private void randomCall() {
        switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> inventoryCall();
            case 1 -> orderCall();
            default -> paymentCall();
        }
    }

    private void inventoryCall() {
        MicroserviceProxy<Object> inventory = proxyRegistry.get(InventoryService.SERVICE_ID);
        String productId = pick(inventoryService.getProductIds(), "SKU-1001");
        switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> inventory.execute("listProducts", Map.of());
            case 1 -> inventory.execute("checkStock", Map.of("productId", productId));
            case 2 -> inventory.execute("reserveStock",
                    Map.of("productId", productId, "quantity", ThreadLocalRandom.current().nextInt(1, 4)));
            default -> inventory.execute("releaseStock",
                    Map.of("productId", productId, "quantity", ThreadLocalRandom.current().nextInt(1, 4)));
        }
    }

    private void orderCall() {
        MicroserviceProxy<Object> orders = proxyRegistry.get(OrderService.SERVICE_ID);
        List<String> knownOrders = orderService.getOrderIds();
        if (knownOrders.isEmpty() || ThreadLocalRandom.current().nextBoolean()) {
            orders.execute("createOrder", Map.of(
                    "customerId", "CUST-" + ThreadLocalRandom.current().nextInt(100, 999),
                    "productId", pick(inventoryService.getProductIds(), "SKU-1001"),
                    "quantity", ThreadLocalRandom.current().nextInt(1, 5)));
            return;
        }
        String orderId = pick(knownOrders, null);
        if (ThreadLocalRandom.current().nextInt(4) == 0) {
            orders.execute("cancelOrder", Map.of("orderId", orderId));
        } else {
            orders.execute("getOrder", Map.of("orderId", orderId));
        }
    }

    private void paymentCall() {
        MicroserviceProxy<Object> payments = proxyRegistry.get(PaymentService.SERVICE_ID);
        List<String> knownPayments = paymentService.getPaymentIds();
        if (knownPayments.isEmpty() || ThreadLocalRandom.current().nextInt(3) > 0) {
            payments.execute("processPayment", Map.of(
                    "orderId", pick(orderService.getOrderIds(), "ORD-SIMULATED"),
                    "amount", Math.round(ThreadLocalRandom.current().nextDouble(10, 500) * 100) / 100.0));
            return;
        }
        payments.execute("getPaymentStatus", Map.of("paymentId", pick(knownPayments, "PAY-UNKNOWN")));
    }

    private static String pick(List<String> values, String fallback) {
        if (values.isEmpty()) {
            return fallback;
        }
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    /** Outcome of a simulation run. */
    public record SimulationResult(int calls, int success, int errors, long durationMs) {
    }
}
