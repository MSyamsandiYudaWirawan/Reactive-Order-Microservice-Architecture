package com.MSyamsandiYW.gateway_service.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteValidatorTest {

    private final RouteValidator routeValidator = new RouteValidator();

    @Test
    @DisplayName("isOpen - auth paths should be open")
    void isOpen_authPaths() {
        assertThat(routeValidator.isOpen("/api/v1/auth/login")).isTrue();
        assertThat(routeValidator.isOpen("/api/v1/auth/register")).isTrue();
        assertThat(routeValidator.isOpen("/api/v1/auth/refresh")).isTrue();
    }

    @Test
    @DisplayName("isOpen - webhook callback should be open")
    void isOpen_webhookCallback() {
        assertThat(routeValidator.isOpen("/api/v1/payments/webhook/callback")).isTrue();
    }

    @Test
    @DisplayName("isOpen - protected paths should NOT be open")
    void isOpen_protectedPaths() {
        assertThat(routeValidator.isOpen("/api/v1/orders")).isFalse();
        assertThat(routeValidator.isOpen("/api/v1/payments")).isFalse();
        assertThat(routeValidator.isOpen("/api/v1/products")).isFalse();
    }

    @Test
    @DisplayName("requiresIdemKey - POST orders should require idempotency key")
    void requiresIdemKey_postOrders() {
        assertThat(routeValidator.requiresIdemKey("/api/v1/orders", "POST")).isTrue();
    }

    @Test
    @DisplayName("requiresIdemKey - POST payments should require idempotency key")
    void requiresIdemKey_postPayments() {
        assertThat(routeValidator.requiresIdemKey("/api/v1/payments", "POST")).isTrue();
    }

    @Test
    @DisplayName("requiresIdemKey - GET orders should NOT require idempotency key")
    void requiresIdemKey_getOrders() {
        assertThat(routeValidator.requiresIdemKey("/api/v1/orders", "GET")).isFalse();
    }

    @Test
    @DisplayName("requiresIdemKey - random paths should NOT require idempotency key")
    void requiresIdemKey_randomPath() {
        assertThat(routeValidator.requiresIdemKey("/api/v1/auth/login", "POST")).isFalse();
    }

    @Test
    @DisplayName("getRequiredRoles - orders should require USER or ADMIN")
    void getRequiredRoles_orders() {
        List<String> roles = routeValidator.getRequiredRoles("/api/v1/orders");
        assertThat(roles).contains("USER", "ADMIN");
    }

    @Test
    @DisplayName("getRequiredRoles - products should require ADMIN")
    void getRequiredRoles_products() {
        List<String> roles = routeValidator.getRequiredRoles("/api/v1/products");
        assertThat(roles).contains("ADMIN");
        assertThat(roles).doesNotContain("USER");
    }

    @Test
    @DisplayName("getRequiredRoles - payments should require USER or ADMIN")
    void getRequiredRoles_payments() {
        List<String> roles = routeValidator.getRequiredRoles("/api/v1/payments");
        assertThat(roles).contains("USER", "ADMIN");
    }

    @Test
    @DisplayName("getRequiredRoles - unknown path should return empty list")
    void getRequiredRoles_unknownPath() {
        List<String> roles = routeValidator.getRequiredRoles("/api/v1/unknown");
        assertThat(roles).isEmpty();
    }
}
