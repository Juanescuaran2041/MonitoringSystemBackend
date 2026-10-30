package com.monitoring.inventory.service;

import com.monitoring.inventory.exception.ServiceException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Real order microservice: creates and tracks orders in memory. */
public class OrderService extends AbstractMicroservice {

    public static final String SERVICE_ID = "orders";

    private final Map<String, Map<String, Object>> ordersById = new ConcurrentHashMap<>();

    public OrderService() {
        super(SERVICE_ID);
    }

    @Override
    protected Map<String, Function<Map<String, Object>, Object>> buildOperations() {
        Map<String, Function<Map<String, Object>, Object>> operations = new LinkedHashMap<>();
        operations.put("createOrder", params -> createOrder(
                requireText(params, "customerId"),
                requireText(params, "productId"),
                requirePositiveInt(params, "quantity")));
        operations.put("getOrder", params -> getOrder(requireText(params, "orderId")));
        operations.put("listOrders", params -> listOrders());
        operations.put("cancelOrder", params -> cancelOrder(requireText(params, "orderId")));
        return operations;
    }

    public Map<String, Object> createOrder(String customerId, String productId, int quantity) {
        simulateLatency(25, 120);
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", orderId);
        order.put("customerId", customerId);
        order.put("productId", productId);
        order.put("quantity", quantity);
        order.put("status", "CREATED");
        order.put("createdAt", Instant.now().toString());
        ordersById.put(orderId, order);
        return Map.copyOf(order);
    }

    public Map<String, Object> getOrder(String orderId) {
        simulateLatency(10, 45);
        return Map.copyOf(orderOrFail(orderId));
    }

    public List<Map<String, Object>> listOrders() {
        simulateLatency(15, 70);
        return new ArrayList<>(ordersById.values());
    }

    public Map<String, Object> cancelOrder(String orderId) {
        simulateLatency(20, 80);
        Map<String, Object> order = orderOrFail(orderId);
        synchronized (order) {
            if ("CANCELLED".equals(order.get("status"))) {
                throw ServiceException.failed("Order " + orderId + " is already cancelled");
            }
            order.put("status", "CANCELLED");
            return Map.copyOf(order);
        }
    }

    private Map<String, Object> orderOrFail(String orderId) {
        Map<String, Object> order = ordersById.get(orderId);
        if (order == null) {
            throw ServiceException.notFound("Order not found: " + orderId);
        }
        return order;
    }

    /** Known order ids, used by the load simulator. */
    public List<String> getOrderIds() {
        return List.copyOf(ordersById.keySet());
    }
}
