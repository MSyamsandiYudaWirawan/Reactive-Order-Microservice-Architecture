package com.MSyamsandiYW.gateway_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final RouteValidator routeValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (routeValidator.isOpen(path)) {
            return chain.filter(exchange);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if(authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        String token = authHeader.substring(7);

        return jwtService.validateToken(token)
                .then(jwtService.extractClaims(token))
                .flatMap(claims -> {
                    String userRoles = claims.get("userRole").toString();
                    List<String> roles = List.of(userRoles.split("\\|"));
                    List<String> requiredRoles = routeValidator.getRequiredRoles(path);
                    if(!requiredRoles.isEmpty() && roles.stream().noneMatch(requiredRoles::contains)){
                        return forbidden(exchange);
                    }
                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r
                                    .header("X-User-Id", claims.getId())
                                    .header("X-User-Email", claims.get("userEmail").toString())
                                    .header("X-User-Roles", userRoles)
                                    .header("X-Correlation-Id", UUID.randomUUID().toString())
                            )
                            .build();
                    return chain.filter(mutated);
                });
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
