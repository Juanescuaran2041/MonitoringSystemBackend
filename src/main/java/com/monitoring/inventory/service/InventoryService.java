package com.monitoring.inventory.service;

import com.monitoring.inventory.exception.ServiceException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Real inventory microservice: keeps stock per product in memory. */
public class InventoryService extends AbstractMicroservice {

    public static final String SERVICE_ID = "inventory";

    private final Map<String, Integer> stockByProduct = new ConcurrentHashMap<>();

    public InventoryService() {
        super(SERVICE_ID);
        stockByProduct.put("SKU-1001", 40);
        stockByProduct.put("SKU-1002", 25);
        stockByProduct.put("SKU-1003", 12);
        stockByProduct.put("SKU-1004", 7);
    }

    @Override
    protected Map<String, Function<Map<String, Object>, Object>> buildOperations() {
        Map<String, Function<Map<String, Object>, Object>> operations = new LinkedHashMap<>();
        operations.put("listProducts", params -> listProducts());
        operations.put("checkStock", params -> checkStock(requireText(params, "productId")));
        operations.put("reserveStock", params -> reserveStock(
                requireText(params, "productId"), requirePositiveInt(params, "quantity")));
        operations.put("releaseStock", params -> releaseStock(
                requireText(params, "productId"), requirePositiveInt(params, "quantity")));
        return operations;
    }

    public List<Map<String, Object>> listProducts() {
        simulateLatency(10, 60);
        List<Map<String, Object>> products = new ArrayList<>();
        stockByProduct.forEach((productId, quantity) -> products.add(
                Map.of("productId", productId, "available", quantity)));
        products.sort((left, right) -> left.get("productId").toString().compareTo(right.get("productId").toString()));
        return products;
    }

    public Map<String, Object> checkStock(String productId) {
        simulateLatency(10, 50);
        return Map.of("productId", productId, "available", availableOrFail(productId));
    }

    public Map<String, Object> reserveStock(String productId, int quantity) {
        simulateLatency(20, 90);
        synchronized (stockByProduct) {
            int available = availableOrFail(productId);
            if (available < quantity) {
                throw ServiceException.failed("Not enough stock for " + productId
                        + ": requested " + quantity + ", available " + available);
            }
            stockByProduct.put(productId, available - quantity);
            return Map.of("productId", productId, "reserved", quantity, "available", available - quantity);
        }
    }

    public Map<String, Object> releaseStock(String productId, int quantity) {
        simulateLatency(15, 70);
        synchronized (stockByProduct) {
            int available = availableOrFail(productId);
            stockByProduct.put(productId, available + quantity);
            return Map.of("productId", productId, "released", quantity, "available", available + quantity);
        }
    }

    private int availableOrFail(String productId) {
        Integer available = stockByProduct.get(productId);
        if (available == null) {
            throw ServiceException.notFound("Product not found: " + productId);
        }
        return available;
    }

    /** Product catalogue, used by the load simulator. */
    public List<String> getProductIds() {
        return List.copyOf(stockByProduct.keySet());
    }
}
