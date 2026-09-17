package com.servicedesk.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "AI-Powered IT Service Desk & Incident Auto-Remediation Engine");
        response.put("status", "running");
        response.put("docs", "/swagger-ui.html");
        response.put("health", "/actuator/health");
        return response;
    }
}
