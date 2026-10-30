package com.monitoring.inventory.controller;

import com.monitoring.inventory.proxy.ProxyRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Liveness probe for the hosting platform and service catalogue for the dashboard. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ProxyRegistry proxyRegistry;

    public HealthController(ProxyRegistry proxyRegistry) {
        this.proxyRegistry = proxyRegistry;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("services", proxyRegistry.getServiceIds());
        return body;
    }
}
