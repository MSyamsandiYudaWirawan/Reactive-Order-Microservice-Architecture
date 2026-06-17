package com.MSyamsandiYW.gateway_service.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RouteValidator {

    private final List<String> openPaths = List.of(
            "/api/v1/auth",
            //bypass webhook callback for ease testing
            "/api/v1/payments/webhook/callback"
    );

    private final Map<String, List<String>> roleProtectedPaths = Map.of(
            "/api/v1/admin", List.of("ADMIN"),
            "/api/v1/orders", List.of("USER","ADMIN"),
            "/api/v1/products", List.of("ADMIN"),
            "/api/v1/payments", List.of("USER","ADMIN")
    );

    private final Map<String, List<String>> idemKeyPaths = Map.of(
            "/api/v1/orders", List.of("POST"), // matches /api/v1/orders/**
            "/api/v1/payments", List.of("POST")
    );

    public boolean isOpen(String path) {
        return openPaths.stream().anyMatch(path::startsWith);
    }

    public boolean requiresIdemKey(String path, String method) {
        return idemKeyPaths.entrySet().stream()
                .anyMatch(e -> path.startsWith(e.getKey()) && e.getValue().contains(method));
    }

    public List<String> getRequiredRoles(String path){
        return roleProtectedPaths.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());
    }

}
