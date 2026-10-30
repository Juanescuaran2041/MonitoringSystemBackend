package com.monitoring.inventory.controller;

import com.monitoring.inventory.proxy.ProxyRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single entry point to the three microservices. The controller only knows the proxies,
 * so every call is audited; the real services are never reachable from here.
 */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ProxyRegistry proxyRegistry;

    public ServiceController(ProxyRegistry proxyRegistry) {
        this.proxyRegistry = proxyRegistry;
    }

    @PostMapping("/inventory/{operation}")
    public ResponseEntity<Map<String, Object>> inventory(@PathVariable String operation,
                                                         @RequestBody(required = false) Map<String, Object> params) {
        return call("inventory", operation, params);
    }

    @PostMapping("/orders/{operation}")
    public ResponseEntity<Map<String, Object>> orders(@PathVariable String operation,
                                                      @RequestBody(required = false) Map<String, Object> params) {
        return call("orders", operation, params);
    }

    @PostMapping("/payments/{operation}")
    public ResponseEntity<Map<String, Object>> payments(@PathVariable String operation,
                                                        @RequestBody(required = false) Map<String, Object> params) {
        return call("payments", operation, params);
    }

    private ResponseEntity<Map<String, Object>> call(String serviceId, String operation, Map<String, Object> params) {
        Object result = proxyRegistry.get(serviceId).execute(operation, params == null ? Map.of() : params);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", serviceId);
        response.put("operation", operation);
        response.put("result", result);
        return ResponseEntity.ok(response);
    }
}
