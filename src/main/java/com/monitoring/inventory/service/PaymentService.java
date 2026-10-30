package com.monitoring.inventory.service;

import com.monitoring.inventory.exception.ServiceException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Real payment microservice. By design a configurable share of the payments is
 * declined, so the dashboard always has errors to show.
 */
public class PaymentService extends AbstractMicroservice {

    public static final String SERVICE_ID = "payments";
    public static final double DEFAULT_FAILURE_RATE = 0.10;

    private final double failureRate;
    private final Map<String, Map<String, Object>> paymentsById = new ConcurrentHashMap<>();

    public PaymentService() {
        this(DEFAULT_FAILURE_RATE);
    }

    public PaymentService(double failureRate) {
        super(SERVICE_ID);
        this.failureRate = failureRate;
    }

    @Override
    protected Map<String, Function<Map<String, Object>, Object>> buildOperations() {
        Map<String, Function<Map<String, Object>, Object>> operations = new LinkedHashMap<>();
        operations.put("processPayment", params -> processPayment(
                requireText(params, "orderId"), requirePositiveAmount(params, "amount")));
        operations.put("getPaymentStatus", params -> getPaymentStatus(requireText(params, "paymentId")));
        operations.put("refundPayment", params -> refundPayment(requireText(params, "paymentId")));
        return operations;
    }

    public Map<String, Object> processPayment(String orderId, double amount) {
        simulateLatency(40, 180);
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw ServiceException.failed("Payment declined by the gateway for order " + orderId);
        }
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("paymentId", paymentId);
        payment.put("orderId", orderId);
        payment.put("amount", amount);
        payment.put("status", "APPROVED");
        payment.put("processedAt", Instant.now().toString());
        paymentsById.put(paymentId, payment);
        return Map.copyOf(payment);
    }

    public Map<String, Object> getPaymentStatus(String paymentId) {
        simulateLatency(10, 50);
        return Map.copyOf(paymentOrFail(paymentId));
    }

    public Map<String, Object> refundPayment(String paymentId) {
        simulateLatency(30, 140);
        Map<String, Object> payment = paymentOrFail(paymentId);
        synchronized (payment) {
            if ("REFUNDED".equals(payment.get("status"))) {
                throw ServiceException.failed("Payment " + paymentId + " is already refunded");
            }
            payment.put("status", "REFUNDED");
            return Map.copyOf(payment);
        }
    }

    private Map<String, Object> paymentOrFail(String paymentId) {
        Map<String, Object> payment = paymentsById.get(paymentId);
        if (payment == null) {
            throw ServiceException.notFound("Payment not found: " + paymentId);
        }
        return payment;
    }

    /** Known payment ids, used by the load simulator. */
    public java.util.List<String> getPaymentIds() {
        return java.util.List.copyOf(paymentsById.keySet());
    }
}
