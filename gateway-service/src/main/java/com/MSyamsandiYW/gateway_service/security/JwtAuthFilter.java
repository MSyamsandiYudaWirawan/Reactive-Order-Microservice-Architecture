package com.MSyamsandiYW.gateway_service.security;

import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.common.redis.RedisService;
import io.jsonwebtoken.Claims;
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
    private final RedisService redisService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        if (routeValidator.isOpen(path)) {
            return chain.filter(exchange);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        //no auth =  reject
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return errorCodeMapper(exchange, USER_UNAUTHORIZED);
        }
        String token = authHeader.substring(7);

        //validate token & extract claims
        return tokenValidator.validateToken(token)
                .then(jwtService.extractClaims(token))
                .flatMap(claims -> {
                    //get role
                    String userRoles = claims.get("userRole").toString();
                    List<String> roles = List.of(userRoles.split("\\|"));
                    // check path require specific role
                    List<String> requiredRoles = routeValidator.getRequiredRoles(path);
                    if (!requiredRoles.isEmpty() && roles.stream().noneMatch(requiredRoles::contains)) {
                        return errorCodeMapper(exchange, USER_FORBIDDEN);
                    }
                    // check path require idempotency key
                    String method = exchange.getRequest().getMethod().name();
                    if (routeValidator.requiresIdemKey(path, method)) {
                        String idempotencyKey = exchange.getRequest().getHeaders().getFirst("X-Idempotency-Key");
                        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
                            return errorCodeMapper(exchange, MISSING_IDEMPOTENCY_KEY);
                        }
                        return checkIdempotencyKey(exchange, chain, claims, idempotencyKey, userRoles);
                    }
                    return chain.filter(mutateExchange(exchange, claims, userRoles));
                });
    }

    private Mono<Void> checkIdempotencyKey(ServerWebExchange exchange,
                                           GatewayFilterChain chain,
                                           Claims claims,
                                           String idempotencyKey,
                                           String userRoles) {
        return redisService.storeIfAbsent(idempotencyKey, "processed")
                .flatMap(stored -> {
                    if (!stored) {
                        return errorCodeMapper(exchange, DUPLICATE_REQUEST);
                    }
                    return chain.filter(mutateExchange(exchange, claims, userRoles));
                });
    }

    private ServerWebExchange mutateExchange(ServerWebExchange exchange, Claims claims, String userRoles) {
        return exchange.mutate()
                .request(r -> r
                        .header("X-User-Id", claims.getId())
                        .header("X-User-Email", claims.get("userEmail").toString())
                        .header("X-User-Roles", userRoles)
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                )
                .build();
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
