package com.MSyamsandiYW.auth_service.security;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class JwtFilter implements WebFilter {

    private final JwtService jwtService;
    private final ReactiveUserDetailsService reactiveUserDetailsService;

    @Override
    public Mono<Void> filter(
            @NotNull final ServerWebExchange exchange,
            @NotNull final WebFilterChain chain) {

        return Mono.defer(() -> {
            if (shouldSkip(exchange)) {
                return chain.filter(exchange);
            }

            final String jwt = extractToken(exchange);
            if (jwt == null) {
                return chain.filter(exchange);
            }

            return authenticateAndContinue(jwt, exchange, chain);
        });
    }

    private boolean shouldSkip(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().value().startsWith("/api/v1/auth");
    }

    private String extractToken(ServerWebExchange exchange) {
        final String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    private Mono<Void> authenticateAndContinue(String jwt, ServerWebExchange exchange, WebFilterChain chain) {
        return jwtService.extractClaims(jwt)
                .flatMap(claims -> jwtService.validateToken(jwt, claims.getSubject())
                        .then(Mono.just(claims)))
                .flatMap(claims -> reactiveUserDetailsService.findByUsername(claims.getSubject()))
                .map(userDetails -> new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                ))
                .flatMap(authToken -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authToken)))
                .onErrorResume(e -> chain.filter(exchange));
    }
}

