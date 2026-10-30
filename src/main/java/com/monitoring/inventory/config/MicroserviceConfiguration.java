package com.monitoring.inventory.config;

import com.monitoring.inventory.proxy.LoggingProxy;
import com.monitoring.inventory.proxy.MicroserviceProxy;
import com.monitoring.inventory.proxy.ProxyRegistry;
import com.monitoring.inventory.repository.CallLogRepository;
import com.monitoring.inventory.service.InventoryService;
import com.monitoring.inventory.service.OrderService;
import com.monitoring.inventory.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wires the real services and wraps each one in a {@link LoggingProxy} by hand.
 * This is the only place where the proxy is attached, and it is explicit on purpose.
 */
@Configuration
public class MicroserviceConfiguration {

    @Bean
    public InventoryService inventoryService() {
        return new InventoryService();
    }

    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public PaymentService paymentService(
            @Value("${monitoring.payments.failure-rate:0.10}") double failureRate) {
        return new PaymentService(failureRate);
    }

    @Bean
    public ProxyRegistry proxyRegistry(InventoryService inventoryService,
                                       OrderService orderService,
                                       PaymentService paymentService,
                                       CallLogRepository callLogRepository) {
        Map<String, MicroserviceProxy<Object>> proxies = new LinkedHashMap<>();
        proxies.put(InventoryService.SERVICE_ID, new LoggingProxy<>(inventoryService, callLogRepository));
        proxies.put(OrderService.SERVICE_ID, new LoggingProxy<>(orderService, callLogRepository));
        proxies.put(PaymentService.SERVICE_ID, new LoggingProxy<>(paymentService, callLogRepository));
        return new ProxyRegistry(proxies);
    }
}
