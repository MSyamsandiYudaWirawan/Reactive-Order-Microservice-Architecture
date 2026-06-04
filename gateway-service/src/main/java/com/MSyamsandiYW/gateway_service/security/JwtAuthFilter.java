package com.MSyamsandiYW.gateway_service.security;

import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static com.MSyamsandiYW.common.exception.ErrorCode.*;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final TokenValidator tokenValidator;
    private final JwtService jwtService;
    private final RouteValidator routeValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        if (routeValidator.isOpen(path)) {
            return chain.filter(exchange);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return errorCodeMapper(exchange, USER_UNAUTHORIZED);
        }
        String token = authHeader.substring(7);

        return tokenValidator.validateToken(token)
                .then(jwtService.extractClaims(token))
                .flatMap(claims -> {
                    String userRoles = claims.get("userRole").toString();
                    List<String> roles = List.of(userRoles.split("\\|"));
                    List<String> requiredRoles = routeValidator.getRequiredRoles(path);
                    // check required roles path
                    if (!requiredRoles.isEmpty() && roles.stream().noneMatch(requiredRoles::contains)) {
                        return errorCodeMapper(exchange, USER_FORBIDDEN);
                    }
                    String method = exchange.getRequest().getMethod().name();
                    // check mandatory idempotency key path
                    if (routeValidator.requiresIdemKey(path, method)) {
                        String idempotencyKey = exchange.getRequest().getHeaders().getFirst("X-Idempotency-Key");
                        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
                            return errorCodeMapper(exchange, MISSING_IDEMPOTENCY_KEY);
                        }
                        //todo check redis by X-Idempotency-Key to deduplicate
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

    private Mono<Void> errorCodeMapper(ServerWebExchange exchange, ErrorCode errorCode) {
        String body = """
                {"code":"%s","message":"%s"}
                """.formatted(errorCode.getCode(), errorCode.getDefaultMessage());
        exchange.getResponse().setStatusCode(errorCode.getStatus());
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
